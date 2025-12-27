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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.TimeUnit

class QuotaStatusBarWidget(private val project: Project) : StatusBarWidget, StatusBarWidget.TextPresentation {

    private val LOG = Logger.getInstance(QuotaStatusBarWidget::class.java)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd")
    private val shortDateFormat = SimpleDateFormat("MM-dd")
    
    private var statusBar: StatusBar? = null
    private var quotaInfo: QuotaInfo? = null
    private var speedResults: List<SpeedTestResult> = emptyList()
    private var timer: Timer? = null
    private var isLoading = false
    private var lastError: String? = null
    private var lastUpdateTime: Long = 0

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

        val quotaText = QuotaServiceImpl.getInstance().getDisplayText(info)
        val speedText = if (settings.speedTestEnabled && speedResults.isNotEmpty()) {
            val minLatency = speedResults
                .filter { it.status == SpeedTestStatus.SUCCESS }
                .minByOrNull { it.latency ?: Long.MAX_VALUE }
                ?.latency
            if (minLatency != null) " | ${minLatency}ms" else ""
        } else {
            ""
        }

        return "💳 $quotaText$speedText"
    }

    override fun getTooltipText(): String {
        val settings = QuotaSettings.getInstance().state

        if (!settings.enabled) {
            return "<html><body style='padding: 6px; font-family: sans-serif;'>API 配额监控已禁用</body></html>"
        }

        if (lastError != null) {
            return """
                <html>
                <body style='padding: 6px; font-family: sans-serif;'>
                <b>❌ 获取配额失败</b><br>
                <hr style='margin: 4px 0;'>
                错误: $lastError<br>
                <hr style='margin: 4px 0;'>
                平台: ${getPlatformName(settings.platformType)}
                </body>
                </html>
            """.trimIndent()
        }

        val info = quotaInfo
        if (info == null) {
            return """
                <html>
                <body style='padding: 6px; font-family: sans-serif;'>
                <b>API 配额信息</b><br>
                <hr style='margin: 4px 0;'>
                状态: ${if (isLoading) "加载中..." else "未获取"}<br>
                平台: ${getPlatformName(settings.platformType)}
                </body>
                </html>
            """.trimIndent()
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
        val updateInfo = if (lastUpdateTime > 0) {
            val elapsed = (System.currentTimeMillis() - lastUpdateTime) / 1000
            "🕐 更新于: ${elapsed}秒前"
        } else ""

        val speedSection = buildSpeedTestSection()

        return """
            <html>
            <body style='padding: 6px; font-family: sans-serif;'>
            <b>📊 ${getPlatformName(settings.platformType)} 配额信息</b><br>
            <hr style='margin: 4px 0;'>
            <b>💰 额度明细</b><br><br>
            🟢 剩余: $${String.format("%.2f", info.remaining)}<br>
            🔴 已用: $${String.format("%.2f", info.used)}<br>
            ⚪ 总额: $${String.format("%.2f", info.total)}<br>
            📊 使用率: ${String.format("%.1f", info.percentage)}%<br>
            $speedSection
            <hr style='margin: 4px 0;'>
            <div style='color: gray; font-size: small;'>$updateInfo</div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun buildExtendedTooltip(info: QuotaInfo, ext: ExtendedQuotaData): String {
        val updateInfo = if (lastUpdateTime > 0) {
            val elapsed = (System.currentTimeMillis() - lastUpdateTime) / 1000
            "🕐 更新于: ${elapsed}秒前"
        } else ""

        val sb = StringBuilder()
        sb.append("<html><body style='padding: 6px; font-family: sans-serif;'>")
        sb.append("<b>📊 PackyCode 配额信息</b><br>")
        sb.append("<hr style='margin: 4px 0;'>")

        // 用户和套餐信息
        sb.append("<b>👤 账户信息</b><br><br>")
        ext.username?.let { sb.append("用户名: $it<br>") }
        ext.planType?.let { sb.append("套餐: ${getPlanDisplayName(it)}<br>") }
        ext.planExpiresAt?.let {
            val daysLeft = getDaysUntil(it)
            sb.append("到期时间: ${dateFormat.format(it)} (${daysLeft}天)<br>")
        }
        ext.balanceUsd?.let { sb.append("账户余额: \$${String.format("%.2f", it)}<br>") }
        ext.totalSpentUsd?.let { sb.append("累计消费: \$${String.format("%.2f", it)}<br>") }

        sb.append("<br><hr style='margin: 4px 0;'>")

        // 本月预算
        ext.monthly?.let { period ->
            sb.append("<b>📅 本月预算</b><br><br>")
            sb.append("${buildProgressBar(period.percentage)} ${String.format("%.1f", period.percentage)}%<br>")
            sb.append("🟢 剩余: \$${String.format("%.2f", period.remaining)}<br>")
            sb.append("🔴 已用: \$${String.format("%.2f", period.spent)}<br>")
            sb.append("⚪ 预算: \$${String.format("%.2f", period.budget)}<br><br>")
        }

        // 本周预算
        ext.weekly?.let { period ->
            val weekLabel = if (ext.weeklyWindowStart != null && ext.weeklyWindowEnd != null) {
                val start = shortDateFormat.format(ext.weeklyWindowStart)
                val end = shortDateFormat.format(ext.weeklyWindowEnd)
                "📆 本周预算 ($start ~ $end)"
            } else {
                "📆 本周预算"
            }
            sb.append("<b>$weekLabel</b><br><br>")
            sb.append("${buildProgressBar(period.percentage)} ${String.format("%.1f", period.percentage)}%<br>")
            sb.append("🟢 剩余: \$${String.format("%.2f", period.remaining)}<br>")
            sb.append("🔴 已用: \$${String.format("%.2f", period.spent)}<br>")
            sb.append("⚪ 预算: \$${String.format("%.2f", period.budget)}<br><br>")
        }

        // 今日预算
        ext.daily?.let { period ->
            sb.append("<b>🌅 今日预算</b><br><br>")
            sb.append("${buildProgressBar(period.percentage)} ${String.format("%.1f", period.percentage)}%<br>")
            sb.append("🟢 剩余: \$${String.format("%.2f", period.remaining)}<br>")
            sb.append("🔴 已用: \$${String.format("%.2f", period.spent)}<br>")
            sb.append("⚪ 预算: \$${String.format("%.2f", period.budget)}<br>")
        }

        // 测速结果
        val speedSection = buildSpeedTestSection()
        if (speedSection.isNotEmpty()) {
            sb.append("<br><hr style='margin: 4px 0;'>")
            sb.append(speedSection)
        }

        sb.append("<hr style='margin: 4px 0;'>")
        sb.append("<div style='color: gray; font-size: small;'>$updateInfo</div>")
        sb.append("</body></html>")

        return sb.toString()
    }

    private fun buildCubenceTooltip(info: QuotaInfo, ext: ExtendedQuotaData): String {
        val updateInfo = if (lastUpdateTime > 0) {
            val elapsed = (System.currentTimeMillis() - lastUpdateTime) / 1000
            "🕐 更新于: ${elapsed}秒前"
        } else ""

        val sb = StringBuilder()
        sb.append("<html><body style='width: 280px; padding: 6px; font-family: sans-serif;'>")
        sb.append("<table width='100%'><tr><td align='left'><b>📊 Cubence 配额信息</b></td></tr></table>")
        sb.append("<hr style='margin: 4px 0;'>")

        // Helper to append a data row
        fun appendDataRow(label: String, value: String) {
            sb.append("<tr>")
            sb.append("<td align='left'>$label</td>")
            sb.append("<td align='right'>$value</td>")
            sb.append("</tr>")
        }

        // Helper to append section header
        fun appendSection(title: String) {
            sb.append("<table width='100%'><tr><td align='left'><b>$title</b></td></tr></table>")
            sb.append("<hr style='margin: 4px 0;'>")
        }

        // 账户余额
        ext.balanceUsd?.let {
            appendSection("💰 账户余额")
            sb.append("<table width='100%'>")
            appendDataRow("💵 余额:", "\$${String.format("%.2f", it)}")
            sb.append("</table>")
            sb.append("<br>")
        }

        // 预算周期显示逻辑
        fun appendPeriodSection(title: String, period: BudgetPeriod) {
            appendSection(title)
            // 进度条行
            sb.append("<table width='100%'><tr>")
            sb.append("<td align='left'>${buildProgressBar(period.percentage)}</td>")
            sb.append("<td align='right'>${String.format("%.1f", period.percentage)}%</td>")
            sb.append("</tr></table>")

            // 数据行
            sb.append("<table width='100%'>")
            appendDataRow("🟢 剩余:", "\$${String.format("%.2f", period.remaining)}")
            appendDataRow("🔴 已用:", "\$${String.format("%.2f", period.spent)}")
            appendDataRow("⚪ 限额:", "\$${String.format("%.2f", period.budget)}")
            sb.append("</table>")
            sb.append("<br>")
        }

        // API Key 配额
        ext.apiKeyQuota?.let { appendPeriodSection("🔑 API Key 配额", it) }

        // 5小时限制
        ext.fiveHour?.let { appendPeriodSection("⏱️ 5小时限制窗口", it) }

        // 周限制
        ext.weekly?.let { appendPeriodSection("📅 本周限制", it) }

        // 测速结果 (内联重写以匹配风格)
        if (speedResults.isNotEmpty()) {
            appendSection("🚀 链接测速")
            sb.append("<table width='100%'>")
            speedResults.forEach { result ->
                val color = if (result.status == SpeedTestStatus.SUCCESS) "#62B543" else "#FF0000"
                val icon = if (result.status == SpeedTestStatus.SUCCESS) "✅" else "❌"
                val latencyText = if (result.latency != null) "${result.latency}ms" else "Failed"
                val urlShort = try { java.net.URL(result.url).host } catch (e: Exception) { result.url }

                sb.append("<tr>")
                sb.append("<td align='left'>$icon $urlShort</td>")
                sb.append("<td align='right' style='color: $color;'>$latencyText</td>")
                sb.append("</tr>")
            }
            sb.append("</table>")
            sb.append("<hr style='margin: 4px 0;'>")
        }

        sb.append("<div style='text-align: right; color: gray; font-size: small;'>$updateInfo</div>")
        sb.append("</body></html>")

        return sb.toString()
    }

    private fun buildSpeedTestSection(): String {
        if (speedResults.isEmpty()) {
            return ""
        }

        val sb = StringBuilder()
        sb.append("<b>🚀 链接测速</b><br><br>")

        for(result in speedResults) {
            val host = shortenUrl(result.url)
            val icon = when (result.status) {
                SpeedTestStatus.SUCCESS -> "✅"
                SpeedTestStatus.FAILED -> "❌"
                SpeedTestStatus.PENDING -> "⏳"
            }
            val latency = if (result.status == SpeedTestStatus.SUCCESS && result.latency != null) {
                "${result.latency}ms"
            } else {
                result.error ?: "Failed"
            }
            sb.append("$icon $host: $latency<br>")
        }

        return sb.toString()
    }

    private fun buildProgressBar(percentage: Double): String {
        val filled = (percentage / 5).toInt().coerceIn(0, 20)
        return "█".repeat(filled) + "░".repeat(20 - filled)
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

    override fun getAlignment(): Float = 0f

    override fun getClickConsumer(): Consumer<MouseEvent> = Consumer {
        LOG.info("Widget clicked, refreshing quota")
        refreshQuota()
    }
}
