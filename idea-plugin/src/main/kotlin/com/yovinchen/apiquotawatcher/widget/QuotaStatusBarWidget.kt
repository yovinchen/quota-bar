package com.yovinchen.apiquotawatcher.widget

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.util.Consumer
import com.yovinchen.apiquotawatcher.service.*
import com.yovinchen.apiquotawatcher.settings.QuotaSettings
import java.awt.event.MouseEvent
import java.net.URL
import java.util.Date
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.TimeUnit

class QuotaStatusBarWidget(private val project: Project) : StatusBarWidget, StatusBarWidget.TextPresentation {

    private val LOG = Logger.getInstance(QuotaStatusBarWidget::class.java)

    private var statusBar: StatusBar? = null
    private var quotaInfo: QuotaInfo? = null
    private var speedResults: List<SpeedTestResult> = emptyList()
    private var timer: Timer? = null
    private var isLoading = false
    private var lastError: String? = null

    companion object {
        const val ID = "ApiQuotaWatcher"
    }

    override fun ID(): String = ID

    override fun install(statusBar: StatusBar) {
        LOG.info("Installing API Quota Watcher widget")
        this.statusBar = statusBar
        startPolling()
    }

    override fun dispose() {
        LOG.info("Disposing API Quota Watcher widget")
        stopPolling()
    }

    private fun startPolling() {
        stopPolling()

        val settings = QuotaSettings.getInstance().state
        if (!settings.enabled) {
            LOG.info("API Quota Watcher is disabled")
            return
        }

        LOG.info("Starting polling with interval: ${settings.pollingInterval}ms")

        timer = Timer("ApiQuotaWatcher-Polling", true)
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                refreshQuota()
            }
        }, 0, settings.pollingInterval)
    }

    private fun stopPolling() {
        timer?.cancel()
        timer = null
    }

    fun refreshQuota() {
        if (isLoading) {
            LOG.debug("Already loading, skipping refresh")
            return
        }
        isLoading = true
        lastError = null

        LOG.info("Refreshing quota...")

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val service = QuotaServiceImpl.getInstance()
                val newQuotaInfo = service.fetchQuota()

                if (newQuotaInfo != null) {
                    quotaInfo = newQuotaInfo
                    LOG.info("Quota fetched: used=${newQuotaInfo.used}, total=${newQuotaInfo.total}, remaining=${newQuotaInfo.remaining}")
                } else {
                    lastError = "无法获取配额信息，请检查配置"
                    LOG.warn("Failed to fetch quota: returned null")
                }

                val settings = QuotaSettings.getInstance().state
                if (settings.speedTestEnabled) {
                    speedResults = service.testSpeedAll()
                    LOG.info("Speed test completed: ${speedResults.size} results")
                }
            } catch (e: Exception) {
                lastError = e.message ?: "未知错误"
                LOG.error("Error fetching quota", e)
            } finally {
                isLoading = false
                ApplicationManager.getApplication().invokeLater {
                    statusBar?.updateWidget(ID)
                }
            }
        }
    }

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun getText(): String {
        val settings = QuotaSettings.getInstance().state

        if (!settings.enabled) {
            return "API: 已禁用"
        }

        if (isLoading && quotaInfo == null) {
            return "API: 加载中..."
        }

        if (lastError != null && quotaInfo == null) {
            return "API: 错误"
        }

        val info = quotaInfo ?: return "API: --"

        val parts = mutableListOf<String>()
        val remainPct = if (info.total > 0) (info.remaining / info.total) * 100 else 0.0
        val usedPct = 100 - remainPct

        // 状态图标
        if (settings.widgetStatusIcon) {
            val icon = when {
                remainPct > 60 -> "🟢"
                remainPct > 20 -> "🟡"
                else -> "🔴"
            }
            parts.add(icon)
        }

        // 状态比例
        if (settings.widgetPercentage) {
            parts.add("${String.format("%.1f", usedPct)}%")
        }

        // 已使用金额
        if (settings.widgetUsed) {
            parts.add("$${String.format("%.2f", info.used)}")
        }

        // 总金额
        if (settings.widgetTotal) {
            parts.add("$${String.format("%.2f", info.total)}")
        }

        // 测速延迟
        if (settings.widgetLatency && speedResults.isNotEmpty()) {
            val minLatency = speedResults
                .filter { it.status == SpeedTestStatus.SUCCESS }
                .minByOrNull { it.latency ?: Long.MAX_VALUE }
                ?.latency
            if (minLatency != null) {
                parts.add("${minLatency}ms")
            }
        }

        return if (parts.isNotEmpty()) parts.joinToString(" ") else "API: --"
    }

    override fun getTooltipText(): String {
        val settings = QuotaSettings.getInstance().state

        if (!settings.enabled) {
            return "<html><body style='${tooltipBodyStyle()}'>API 配额监控已禁用</body></html>"
        }

        if (lastError != null) {
            return """
                <html>
                <body style='${tooltipBodyStyle()}'>
                <div style='font-weight: 600; margin-bottom: 4px;'>获取配额失败</div>
                <div style='color: #B00020; margin-bottom: 6px;'>错误: $lastError</div>
                <div style='color: #6a737d;'>平台: ${getPlatformName(settings.platformType)}</div>
                </body>
                </html>
            """.trimIndent()
        }

        val info = quotaInfo
        if (info == null) {
            return """
                <html>
                <body style='${tooltipBodyStyle()}'>
                <div style='font-weight: 600; margin-bottom: 4px;'>API 配额信息</div>
                <div style='margin-bottom: 6px;'>状态: ${if (isLoading) "加载中..." else "未获取"}</div>
                <div style='color: #6a737d;'>平台: ${getPlatformName(settings.platformType)}</div>
                </body>
                </html>
            """.trimIndent()
        }

        // PackyCode 使用扩展信息
        if (settings.platformType == "packycode" && info.extended != null) {
            return buildExtendedTooltip(info.extended)
        }

        // Cubence 使用扩展信息
        if (settings.platformType == "cubence" && info.extended != null) {
            return buildCubenceTooltip(info.extended)
        }

        return buildBasicTooltip(info)
    }

    private fun buildBasicTooltip(info: QuotaInfo): String {
        val settings = QuotaSettings.getInstance().state
        val usedPct = if (info.total > 0) (info.used / info.total) * 100 else 0.0
        val speedSection = buildSpeedTestSection()

        val sb = StringBuilder()
        sb.append("<html><body style='${tooltipBodyStyle()}'>")
        sb.append("<div style='font-weight: 600; margin-bottom: 6px;'>${getPlatformName(settings.platformType)}</div>")
        sb.append("<table style='width: 100%; border-collapse: collapse;'>")
        appendTableHeader(sb, listOf("周期", "已用", "预算", "进度"), listOf("left", "right", "right", "left"))
        appendTableRow(
            sb,
            listOf(
                "总额度",
                "$${String.format("%.2f", info.used)}",
                "$${String.format("%.2f", info.total)}",
                buildProgressBar(usedPct)
            ),
            listOf("left", "right", "right", "left")
        )
        sb.append("</table>")
        if (speedSection.isNotEmpty()) {
            sb.append(speedSection)
        }
        sb.append("</body></html>")
        return sb.toString()
    }

    private fun buildExtendedTooltip(ext: ExtendedQuotaData): String {
        val sb = StringBuilder()
        sb.append("<html><body style='${tooltipBodyStyle()}'>")
        sb.append("<div style='font-weight: 600; margin-bottom: 6px;'>PackyCode</div>")

        val accountRows = mutableListOf<Pair<String, String>>()
        ext.username?.let { accountRows.add("用户" to it) }
        ext.planType?.let { accountRows.add("套餐" to getPlanDisplayName(it)) }
        ext.planExpiresAt?.let {
            val daysLeft = getDaysUntil(it)
            accountRows.add("到期" to "${daysLeft}天后")
        }
        ext.balanceUsd?.let { accountRows.add("余额" to "$${String.format("%.2f", it)}") }
        appendKeyValueTable(sb, "项目", "值", accountRows)

        val quotaRows = mutableListOf<List<String>>()
        ext.monthly?.let { period ->
            quotaRows.add(buildQuotaRowSimple("本月", period))
        }
        ext.weekly?.let { period ->
            quotaRows.add(buildQuotaRowSimple("本周", period))
        }
        ext.daily?.let { period ->
            quotaRows.add(buildQuotaRowSimple("今日", period))
        }

        if (quotaRows.isNotEmpty()) {
            sb.append("<div style='font-weight: 600; margin: 8px 0 4px 0;'>额度使用</div>")
            appendQuotaTableSimple(sb, quotaRows)
        }

        val speedSection = buildSpeedTestSection()
        if (speedSection.isNotEmpty()) {
            sb.append(speedSection)
        }

        sb.append("</body></html>")

        return sb.toString()
    }

    private fun buildCubenceTooltip(ext: ExtendedQuotaData): String {
        val sb = StringBuilder()
        sb.append("<html><body style='${tooltipBodyStyle()}'>")
        sb.append("<div style='font-weight: 600; margin-bottom: 6px;'>Cubence</div>")

        val balanceRows = mutableListOf<Pair<String, String>>()
        ext.balanceUsd?.let { balanceRows.add("余额" to "$${String.format("%.2f", it)}") }
        appendKeyValueTable(sb, "项目", "金额", balanceRows)

        val quotaRows = mutableListOf<List<String>>()
        ext.apiKeyQuota?.let { period ->
            quotaRows.add(buildQuotaRowWithReset("API Key", period))
        }
        ext.fiveHour?.let { period ->
            quotaRows.add(buildQuotaRowWithReset("5小时", period))
        }
        ext.weekly?.let { period ->
            quotaRows.add(buildQuotaRowWithReset("本周", period))
        }

        if (quotaRows.isNotEmpty()) {
            sb.append("<div style='font-weight: 600; margin: 8px 0 4px 0;'>额度使用</div>")
            appendQuotaTableWithReset(sb, quotaRows)
        }

        val speedSection = buildSpeedTestSection()
        if (speedSection.isNotEmpty()) {
            sb.append(speedSection)
        }

        sb.append("</body></html>")

        return sb.toString()
    }

    private fun appendKeyValueTable(
        sb: StringBuilder,
        leftHeader: String,
        rightHeader: String,
        rows: List<Pair<String, String>>
    ) {
        if (rows.isEmpty()) {
            return
        }
        sb.append("<table style='width: 100%; border-collapse: collapse;'>")
        appendTableHeader(sb, listOf(leftHeader, rightHeader), listOf("left", "right"))
        for (row in rows) {
            appendTableRow(sb, listOf(row.first, row.second), listOf("left", "right"))
        }
        sb.append("</table>")
    }

    /**
     * 额度表格 - 带重置时间 (Cubence 用)
     */
    private fun appendQuotaTableWithReset(sb: StringBuilder, rows: List<List<String>>) {
        sb.append("<table style='width: 100%; border-collapse: collapse;'>")
        appendTableHeader(sb, listOf("周期", "已用", "预算", "进度", "重置时间"), listOf("left", "right", "right", "left", "left"))
        for (row in rows) {
            appendTableRow(sb, row, listOf("left", "right", "right", "left", "left"))
        }
        sb.append("</table>")
    }

    /**
     * 额度表格 - 不带重置时间 (PackyCode/PackyAPI 用)
     */
    private fun appendQuotaTableSimple(sb: StringBuilder, rows: List<List<String>>) {
        sb.append("<table style='width: 100%; border-collapse: collapse;'>")
        appendTableHeader(sb, listOf("周期", "已用", "预算", "进度"), listOf("left", "right", "right", "left"))
        for (row in rows) {
            appendTableRow(sb, row, listOf("left", "right", "right", "left"))
        }
        sb.append("</table>")
    }

    private fun appendTableHeader(sb: StringBuilder, headers: List<String>, aligns: List<String>) {
        sb.append("<tr>")
        for (i in headers.indices) {
            val align = aligns.getOrNull(i) ?: "left"
            sb.append("<th align='$align' style='padding: 2px 4px; color: #6a737d; font-weight: normal; white-space: nowrap;'>${headers[i]}</th>")
        }
        sb.append("</tr>")
    }

    private fun appendTableRow(sb: StringBuilder, cells: List<String>, aligns: List<String>) {
        sb.append("<tr>")
        for (i in cells.indices) {
            val align = aligns.getOrNull(i) ?: "left"
            sb.append("<td align='$align' style='padding: 2px 4px; white-space: nowrap;'>${cells[i]}</td>")
        }
        sb.append("</tr>")
    }

    /**
     * 构建额度行 - 带重置时间 (Cubence 用)
     */
    private fun buildQuotaRowWithReset(label: String, period: BudgetPeriod): List<String> {
        val resetTimeStr = period.resetAt?.let { formatResetTime(it) } ?: "-"
        return listOf(
            label,
            "$${String.format("%.2f", period.spent)}",
            "$${String.format("%.2f", period.budget)}",
            buildProgressBar(period.percentage),
            resetTimeStr
        )
    }

    /**
     * 构建额度行 - 不带重置时间 (PackyCode/PackyAPI 用)
     */
    private fun buildQuotaRowSimple(label: String, period: BudgetPeriod): List<String> {
        return listOf(
            label,
            "$${String.format("%.2f", period.spent)}",
            "$${String.format("%.2f", period.budget)}",
            buildProgressBar(period.percentage)
        )
    }

    private fun buildSpeedTestSection(): String {
        if (speedResults.isEmpty()) {
            return ""
        }

        val sb = StringBuilder()
        sb.append("<div style='font-weight: 600; margin: 8px 0 4px 0;'>测速</div>")
        sb.append("<table style='width: 100%; border-collapse: collapse;'>")
        appendTableHeader(sb, listOf("节点", "延迟"), listOf("left", "right"))
        for (result in speedResults) {
            val host = shortenUrl(result.url)
            val latency = if (result.status == SpeedTestStatus.SUCCESS && result.latency != null) {
                "${result.latency}ms"
            } else {
                "-"
            }
            appendTableRow(sb, listOf(host, latency), listOf("left", "right"))
        }
        sb.append("</table>")

        return sb.toString()
    }

    private fun buildProgressBar(percentage: Double): String {
        val pct = percentage.coerceIn(0.0, 100.0)
        val filled = Math.round(pct / 10).toInt().coerceIn(0, 10)
        val empty = 10 - filled
        // 使用 Unicode 块字符: █ (实心) ░ (阴影)，与 VS Code 插件保持一致
        val bar = "█".repeat(filled) + "░".repeat(empty)
        return "<span style='font-family: monospace; white-space: nowrap;'>$bar ${String.format("%.1f", pct)}%</span>"
    }

    private fun tooltipBodyStyle(): String {
        return "padding: 8px 10px; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; " +
            "font-size: 12px; color: #1f2328; min-width: 280px; line-height: 1.4;"
    }

    private fun shortenUrl(url: String): String {
        return try {
            URL(url).host
        } catch (e: Exception) {
            if (url.length > 20) url.take(17) + "..." else url
        }
    }

    private fun getDaysUntil(date: Date): Long {
        val now = System.currentTimeMillis()
        val diff = date.time - now
        return TimeUnit.MILLISECONDS.toDays(diff)
    }

    /**
     * 格式化重置时间（用于 Cubence 等平台）
     * 显示相对时间，如 "3小时后" 或 "2天后"
     */
    private fun formatResetTime(date: Date): String {
        val now = System.currentTimeMillis()
        val diff = date.time - now
        
        if (diff <= 0) {
            return "已重置"
        }
        
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        val days = TimeUnit.MILLISECONDS.toDays(diff)
        
        return when {
            days > 0 -> {
                val remainingHours = hours % 24
                if (remainingHours > 0) "${days}天${remainingHours}小时" else "${days}天后"
            }
            hours > 0 -> {
                val remainingMinutes = minutes % 60
                if (remainingMinutes > 0) "${hours}小时${remainingMinutes}分" else "${hours}小时后"
            }
            else -> "${minutes}分钟后"
        }
    }

    private fun getPlanDisplayName(planType: String): String {
        return when (planType.lowercase()) {
            "basic" -> "基础版"
            "pro" -> "专业版"
            "premium" -> "高级版"
            "enterprise" -> "企业版"
            else -> planType
        }
    }

    private fun getPlatformName(platformType: String): String {
        return when (platformType) {
            "newapi" -> "NewAPI"
            "packyapi" -> "PackyAPI"
            "packycode" -> "PackyCode"
            "cubence" -> "Cubence"
            else -> platformType
        }
    }

    override fun getAlignment(): Float = 0f

    override fun getClickConsumer(): Consumer<MouseEvent> = Consumer {
        LOG.info("Widget clicked, refreshing quota")
        refreshQuota()
    }
}
