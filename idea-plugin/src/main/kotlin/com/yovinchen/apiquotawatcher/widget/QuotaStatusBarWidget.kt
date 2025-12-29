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
    private var lastUpdateTime: Long = 0
    private var pendingUiUpdate = false

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
                    lastUpdateTime = System.currentTimeMillis()
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
                    requestStatusBarUpdate()
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
            return wrapTooltip(sectionTitle("API 配额监控已禁用"))
        }

        if (lastError != null) {
            val content = StringBuilder()
            content.append(sectionTitle("获取配额失败"))
            content.append(paragraph("错误: $lastError"))
            content.append(paragraph("平台: ${getPlatformName(settings.platformType)}"))
            return wrapTooltip(content.toString())
        }

        val info = quotaInfo
        if (info == null) {
            val content = StringBuilder()
            content.append(sectionTitle("API 配额信息"))
            content.append(paragraph("状态: ${if (isLoading) "加载中..." else "未获取"}"))
            content.append(paragraph("平台: ${getPlatformName(settings.platformType)}"))
            return wrapTooltip(content.toString())
        }

        // PackyCode 使用扩展信息
        if (settings.platformType == "packycode" && info.extended != null) {
            return buildExtendedTooltip(info, info.extended)
        }

        // Cubence 使用扩展信息
        if (settings.platformType == "cubence" && info.extended != null) {
            return buildCubenceTooltip(info, info.extended)
        }

        return buildBasicTooltip(info)
    }

    private fun buildBasicTooltip(info: QuotaInfo): String {
        val settings = QuotaSettings.getInstance().state
        val speedSection = buildSpeedTestSection()
        val remainPct = if (info.total > 0) (info.remaining / info.total) * 100 else 0.0
        val usedPct = 100 - remainPct

        val content = StringBuilder()
        content.append(sectionTitle(getPlatformName(settings.platformType)))
        content.append(
            buildTable(
                listOf("项目", "金额", "比例"),
                listOf(
                    listOf("剩余", formatCurrency(info.remaining), formatPercentage(remainPct)),
                    listOf("已用", formatCurrency(info.used), formatPercentage(usedPct)),
                    listOf("总额", formatCurrency(info.total), "-")
                )
            )
        )
        if (speedSection.isNotEmpty()) {
            content.append(speedSection)
        }

        return wrapTooltip(content.toString())
    }

    private fun buildExtendedTooltip(info: QuotaInfo, ext: ExtendedQuotaData): String {
        val sb = StringBuilder()
        sb.append(sectionTitle("PackyCode"))

        val badges = mutableListOf<String>()
        ext.planType?.let { badges.add(getPlanDisplayName(it)) }
        ext.planExpiresAt?.let { badges.add("到期 ${getDaysUntil(it)}天后") }
        ext.balanceUsd?.let { badges.add("余额 ${formatCurrency(it)}") }
        if (badges.isNotEmpty()) {
            sb.append(buildBadges(badges))
        }

        val accountRows = mutableListOf<List<String>>()
        ext.username?.let { accountRows.add(listOf("用户", it)) }
        ext.planType?.let { accountRows.add(listOf("套餐", getPlanDisplayName(it))) }
        ext.planExpiresAt?.let {
            val daysLeft = getDaysUntil(it)
            accountRows.add(listOf("到期", "${daysLeft}天后"))
        }
        ext.balanceUsd?.let { accountRows.add(listOf("余额", formatCurrency(it))) }
        if (accountRows.isNotEmpty()) {
            sb.append(buildTable(listOf("项目", "值"), accountRows))
        }

        ext.monthly?.let { sb.append(buildBudgetSection("本月", it)) }
        ext.weekly?.let { sb.append(buildBudgetSection("本周", it)) }
        ext.daily?.let { sb.append(buildBudgetSection("今日", it)) }

        val speedSection = buildSpeedTestSection()
        if (speedSection.isNotEmpty()) {
            sb.append(speedSection)
        }

        return wrapTooltip(sb.toString())
    }

    private fun buildCubenceTooltip(info: QuotaInfo, ext: ExtendedQuotaData): String {
        val sb = StringBuilder()
        sb.append(sectionTitle("Cubence"))

        ext.balanceUsd?.let {
            sb.append(buildTable(listOf("项目", "金额"), listOf(listOf("余额", formatCurrency(it)))))
        }

        ext.apiKeyQuota?.let { sb.append(buildBudgetSection("API Key 配额", it)) }
        ext.fiveHour?.let { sb.append(buildBudgetSection("5小时窗口", it)) }
        ext.weekly?.let { sb.append(buildBudgetSection("本周限制", it)) }

        val speedSection = buildSpeedTestSection()
        if (speedSection.isNotEmpty()) {
            sb.append(speedSection)
        }

        return wrapTooltip(sb.toString())
    }

    private fun buildSpeedTestSection(): String {
        if (speedResults.isEmpty()) {
            return ""
        }

        val rows = speedResults.map { result ->
            val host = shortenUrl(result.url)
            val latency = if (result.status == SpeedTestStatus.SUCCESS && result.latency != null) {
                "${result.latency}ms"
            } else {
                "-"
            }
            listOf(host, latency)
        }

        return sectionTitle("测速") + buildTable(listOf("节点", "延迟"), rows)
    }

    private fun wrapTooltip(content: String): String {
        return "<html><body style='padding: 8px; font-family: sans-serif;'>$content</body></html>"
    }

    private fun sectionTitle(title: String): String {
        return "<div style='font-weight:bold; margin:0 0 6px 0;'>$title</div>"
    }

    private fun paragraph(text: String): String {
        return "<div style='margin:0 0 6px 0;'>$text</div>"
    }

    private fun buildTable(headers: List<String>, rows: List<List<String>>): String {
        val sb = StringBuilder()
        sb.append("<table style='border-collapse:collapse; width:100%; margin:0 0 8px 0;'>")

        if (headers.isNotEmpty()) {
            sb.append("<tr>")
            headers.forEachIndexed { index, header ->
                val align = if (index == 0) "left" else "right"
                sb.append("<th style='padding:0 6px 4px 0; text-align:$align; color:#888; font-weight:normal;'>$header</th>")
            }
            sb.append("</tr>")
        }

        for (row in rows) {
            sb.append("<tr>")
            row.forEachIndexed { index, cell ->
                val align = if (index == 0) "left" else "right"
                sb.append("<td style='padding:2px 6px 2px 0; text-align:$align;'>$cell</td>")
            }
            sb.append("</tr>")
        }

        sb.append("</table>")
        return sb.toString()
    }

    private fun buildBudgetSection(title: String, period: BudgetPeriod): String {
        return sectionTitle("$title (已用 ${formatPercentage(period.percentage)})") + buildTable(
            listOf("项目", "金额"),
            listOf(
                listOf("剩余", formatCurrency(period.remaining)),
                listOf("已用", formatCurrency(period.spent)),
                listOf("预算", formatCurrency(period.budget))
            )
        )
    }

    private fun formatCurrency(value: Double): String = "$${String.format("%.2f", value)}"

    private fun formatPercentage(value: Double): String = "${String.format("%.1f", value)}%"

    private fun buildBadges(labels: List<String>): String {
        val sb = StringBuilder()
        sb.append("<div style='display:flex; flex-wrap:wrap; gap:6px; margin:0 0 8px 0;'>")
        labels.forEach { label ->
            sb.append("<span style='background:#f2f2f2; border-radius:12px; padding:4px 10px; font-size:12px;'>$label</span>")
        }
        sb.append("</div>")
        return sb.toString()
    }

    private fun shortenUrl(url: String): String {
        return try {
            java.net.URL(url).host
        } catch (e: Exception) {
            if (url.length > 20) url.take(17) + "..." else url
        }
    }

    private fun getDaysUntil(date: Date): Long {
        val now = System.currentTimeMillis()
        val diff = date.time - now
        return TimeUnit.MILLISECONDS.toDays(diff)
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

    /**
     * 避免悬浮提示被立即关闭：鼠标停留在状态栏时先延迟更新，鼠标离开后再刷新
     */
    private fun requestStatusBarUpdate() {
        val component = statusBar?.component ?: return
        val mouseOverStatusBar = component.mousePosition != null

        if (!mouseOverStatusBar) {
            pendingUiUpdate = false
            statusBar?.updateWidget(ID)
            return
        }

        if (pendingUiUpdate) {
            return
        }
        pendingUiUpdate = true

        ApplicationManager.getApplication().executeOnPooledThread {
            Thread.sleep(500)
            ApplicationManager.getApplication().invokeLater {
                requestStatusBarUpdate()
            }
        }
    }

    override fun getAlignment(): Float = 0f

    override fun getClickConsumer(): Consumer<MouseEvent> = Consumer {
        LOG.info("Widget clicked, refreshing quota")
        refreshQuota()
    }
}
