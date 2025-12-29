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
    private var lastDisplayText: String = ""
    private var lastTooltipContent: String = ""

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

    private var lastRenderedQuotaInfo: QuotaInfo? = null
    private var lastRenderedSpeedResults: List<SpeedTestResult> = emptyList()
    // 用于显示的测速结果（只在状态变化时更新，避免延迟微小波动导致 tooltip 变化）
    private var displaySpeedResults: List<SpeedTestResult> = emptyList()

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
                var newSpeedResults: List<SpeedTestResult> = emptyList()
                
                if (newQuotaInfo == null) {
                    lastError = "无法获取配额信息，请检查配置"
                }

                val settings = QuotaSettings.getInstance().state
                if (settings.speedTestEnabled) {
                    // 关键修复：强制按 URL 排序，确保列表顺序一致，防止因顺序不同导致的 UI 变化
                    newSpeedResults = service.testSpeedAll().sortedBy { it.url }
                }
                
                // 检查数据是否发生了显著变化
                // 注意：这里传入 newQuotaInfo，不要直接使用成员变量，因为成员变量还没更新
                val shouldUpdate = isSignificantChange(newQuotaInfo, newSpeedResults)

                if (shouldUpdate) {
                     // 只有数据显著变化时，才更新成员变量和渲染缓存
                     if (newQuotaInfo != null) {
                         quotaInfo = newQuotaInfo
                         lastRenderedQuotaInfo = newQuotaInfo
                         lastUpdateTime = System.currentTimeMillis()
                         LOG.info("Quota updated: used=${newQuotaInfo.used}")
                     }

                     speedResults = newSpeedResults
                     lastRenderedSpeedResults = newSpeedResults
                     // 只在状态变化时更新显示用的测速结果
                     displaySpeedResults = newSpeedResults

                     // 成功获取数据，清除错误状态
                     lastError = null
                }

                ApplicationManager.getApplication().invokeLater {
                    // 安全更新：只有当确实需要更新时才调用
                    if (shouldUpdate) {
                         requestStatusBarUpdate()
                    }
                }

            } catch (e: Exception) {
                LOG.warn("Error fetching quota (transient)", e)
                // 关键修复：如果已有缓存数据，忽略临时错误，防止 UI 闪烁成错误状态
                // 只有当完全没有数据时，才显示错误
                if (quotaInfo == null) {
                    lastError = e.message ?: "未知错误"
                    ApplicationManager.getApplication().invokeLater {
                        requestStatusBarUpdate()
                    }
                }
            } finally {
                isLoading = false
            }
        }
    }

    private fun isSignificantChange(newQuota: QuotaInfo?, newSpeed: List<SpeedTestResult>): Boolean {
        // 1. 配额信息变化检测（使用容差比较，避免浮点精度问题）
        if (newQuota == null && lastRenderedQuotaInfo == null) {
            // 都为 null，检查测速
        } else if (newQuota == null || lastRenderedQuotaInfo == null) {
            return true // 一个为 null，另一个不为 null
        } else {
            // 使用容差比较 Double 值（0.001 = $0.001 精度）
            val tolerance = 0.001
            if (Math.abs(newQuota.used - lastRenderedQuotaInfo!!.used) > tolerance) return true
            if (Math.abs(newQuota.total - lastRenderedQuotaInfo!!.total) > tolerance) return true
            if (Math.abs(newQuota.remaining - lastRenderedQuotaInfo!!.remaining) > tolerance) return true
            if (Math.abs(newQuota.percentage - lastRenderedQuotaInfo!!.percentage) > 0.1) return true
        }

        // 2. 测速结果防抖（只检测状态变化，忽略延迟微小波动）
        if (newSpeed.size != lastRenderedSpeedResults.size) return true

        val oldMap = lastRenderedSpeedResults.associateBy { it.url }
        for (item in newSpeed) {
            val oldItem = oldMap[item.url] ?: return true
            // 只检测状态变化（成功 <-> 失败）
            if (oldItem.status != item.status) return true
        }

        return false
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

        // 测速延迟（使用显示用的缓存结果）
        if (settings.widgetLatency && displaySpeedResults.isNotEmpty()) {
            val minLatency = displaySpeedResults
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
        val usedPct = if (info.total > 0) (info.used / info.total) * 100 else 0.0

        val content = StringBuilder()
        content.append(sectionTitle(getPlatformName(settings.platformType)))
        content.append(buildQuotaTable(listOf(
            QuotaRow("总额度", info.used, info.total, usedPct)
        )))
        if (speedSection.isNotEmpty()) {
            content.append(speedSection)
        }

        return wrapTooltip(content.toString())
    }

    @Suppress("UNUSED_PARAMETER")
    private fun buildExtendedTooltip(info: QuotaInfo, ext: ExtendedQuotaData): String {
        val sb = StringBuilder()
        sb.append(sectionTitle("PackyCode"))

        // 账户信息表格
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

        // 额度使用表格
        val quotaRows = mutableListOf<QuotaRow>()
        ext.monthly?.let { quotaRows.add(QuotaRow("本月", it.spent, it.budget, it.percentage)) }
        ext.weekly?.let { quotaRows.add(QuotaRow("本周", it.spent, it.budget, it.percentage)) }
        ext.daily?.let { quotaRows.add(QuotaRow("今日", it.spent, it.budget, it.percentage)) }
        if (quotaRows.isNotEmpty()) {
            sb.append(sectionTitle("额度使用"))
            sb.append(buildQuotaTable(quotaRows))
        }

        val speedSection = buildSpeedTestSection()
        if (speedSection.isNotEmpty()) {
            sb.append(speedSection)
        }

        return wrapTooltip(sb.toString())
    }

    @Suppress("UNUSED_PARAMETER")
    private fun buildCubenceTooltip(info: QuotaInfo, ext: ExtendedQuotaData): String {
        val sb = StringBuilder()
        sb.append(sectionTitle("Cubence"))

        ext.balanceUsd?.let {
            sb.append(buildTable(listOf("项目", "金额"), listOf(listOf("余额", formatCurrency(it)))))
        }

        // 额度使用表格
        val quotaRows = mutableListOf<QuotaRow>()
        ext.apiKeyQuota?.let { quotaRows.add(QuotaRow("API Key", it.spent, it.budget, it.percentage)) }
        ext.fiveHour?.let { quotaRows.add(QuotaRow("5小时", it.spent, it.budget, it.percentage)) }
        ext.weekly?.let { quotaRows.add(QuotaRow("本周", it.spent, it.budget, it.percentage)) }
        if (quotaRows.isNotEmpty()) {
            sb.append(sectionTitle("额度使用"))
            sb.append(buildQuotaTable(quotaRows))
        }

        val speedSection = buildSpeedTestSection()
        if (speedSection.isNotEmpty()) {
            sb.append(speedSection)
        }

        return wrapTooltip(sb.toString())
    }

    private fun buildSpeedTestSection(): String {
        if (displaySpeedResults.isEmpty()) {
            return ""
        }

        val rows = displaySpeedResults.map { result ->
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
        return "<html><body style='padding:8px;'>$content</body></html>"
    }

    private fun sectionTitle(title: String): String {
        return "<p><b>$title</b></p>"
    }

    private fun paragraph(text: String): String {
        return "<p>$text</p>"
    }

    private fun buildTable(headers: List<String>, rows: List<List<String>>): String {
        val sb = StringBuilder()
        sb.append("<table cellpadding='2' cellspacing='0'>")

        if (headers.isNotEmpty()) {
            sb.append("<tr>")
            headers.forEachIndexed { index, header ->
                val align = if (index == 0) "left" else "right"
                sb.append("<td align='$align'><font color='#888888'>$header</font></td>")
            }
            sb.append("</tr>")
        }

        for (row in rows) {
            sb.append("<tr>")
            row.forEachIndexed { index, cell ->
                val align = if (index == 0) "left" else "right"
                sb.append("<td align='$align'>$cell</td>")
            }
            sb.append("</tr>")
        }

        sb.append("</table>")
        return sb.toString()
    }

    /**
     * 额度行数据
     */
    private data class QuotaRow(
        val label: String,
        val used: Double,
        val total: Double,
        val percentage: Double
    )

    /**
     * 构建带进度条的额度表格
     */
    private fun buildQuotaTable(rows: List<QuotaRow>): String {
        val sb = StringBuilder()
        sb.append("<table cellpadding='2' cellspacing='0'>")

        // 表头
        sb.append("<tr>")
        sb.append("<td align='left'><font color='#888888'>周期</font></td>")
        sb.append("<td align='right'><font color='#888888'>已用</font></td>")
        sb.append("<td align='right'><font color='#888888'>预算</font></td>")
        sb.append("<td align='left'><font color='#888888'>进度</font></td>")
        sb.append("</tr>")

        // 数据行
        for (row in rows) {
            sb.append("<tr>")
            sb.append("<td align='left'>${row.label}</td>")
            sb.append("<td align='right'>${formatCurrency(row.used)}</td>")
            sb.append("<td align='right'>${formatCurrency(row.total)}</td>")
            sb.append("<td align='left'>${buildProgressBar(row.percentage)}</td>")
            sb.append("</tr>")
        }

        sb.append("</table>")
        return sb.toString()
    }

    /**
     * 构建进度条（使用 Unicode 字符，兼容 IDEA HTML 渲染）
     */
    private fun buildProgressBar(percentage: Double): String {
        val pct = percentage.coerceIn(0.0, 100.0)
        val filled = (pct / 10).toInt()
        val empty = 10 - filled
        val bar = "█".repeat(filled) + "░".repeat(empty)
        return "$bar ${String.format("%.1f", pct)}%"
    }

    private fun formatCurrency(value: Double): String = "$${String.format("%.2f", value)}"

    private fun formatPercentage(value: Double): String = "${String.format("%.1f", value)}%"

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
     * 避免悬浮提示闪烁：只有当显示内容真正变化时才更新 widget
     * 鼠标停留在状态栏时延迟更新，鼠标离开后再刷新
     */
    private fun requestStatusBarUpdate() {
        // 计算当前显示内容
        val currentText = getText()
        val currentTooltip = getTooltipText()

        // 如果内容没有变化，不需要更新 UI
        if (currentText == lastDisplayText && currentTooltip == lastTooltipContent) {
            return
        }

        val component = statusBar?.component ?: return

        // 更健壮的鼠标悬停检测：使用绝对坐标判断
        var isMouseOver = false
        try {
            val pointerInfo = java.awt.MouseInfo.getPointerInfo()
            if (pointerInfo != null) {
                val point = pointerInfo.location
                javax.swing.SwingUtilities.convertPointFromScreen(point, component)
                isMouseOver = component.contains(point)
            }
        } catch (e: Exception) {
            // 忽略异常，默认为未悬停
            LOG.warn("Error checking mouse position", e)
        }

        if (isMouseOver) {
            // 鼠标悬停时，不进行更新，以免打断 Tooltip 显示
            // 启动一个延时任务，稍后再试
            if (!pendingUiUpdate) {
                pendingUiUpdate = true
                ApplicationManager.getApplication().executeOnPooledThread {
                    try {
                        Thread.sleep(1000)
                    } catch (ignored: InterruptedException) {
                    }
                    
                    ApplicationManager.getApplication().invokeLater {
                        pendingUiUpdate = false
                        // 重新检查是否可以更新
                        requestStatusBarUpdate()
                    }
                }
            }
            return
        }

        // 鼠标未悬停，安全更新
        lastDisplayText = currentText
        lastTooltipContent = currentTooltip
        pendingUiUpdate = false
        statusBar?.updateWidget(ID)
    }

    override fun getAlignment(): Float = 0f

    override fun getClickConsumer(): Consumer<MouseEvent> = Consumer {
        LOG.info("Widget clicked, refreshing quota")
        refreshQuota()
    }
}
