package com.yovinchen.apiquotawatcher.settings

import com.intellij.openapi.options.Configurable
import javax.swing.*
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets

class QuotaSettingsConfigurable : Configurable {

    private var mainPanel: JPanel? = null
    private var enabledCheckBox: JCheckBox? = null
    private var speedTestEnabledCheckBox: JCheckBox? = null
    private var platformTypeComboBox: JComboBox<String>? = null
    private var pollingIntervalField: JTextField? = null
    private var displayStyleComboBox: JComboBox<String>? = null

    // NewAPI fields
    private var newapiBaseUrlField: JTextField? = null
    private var newapiAccessTokenField: JPasswordField? = null
    private var newapiUserIdField: JTextField? = null
    private var newapiSpeedTestUrlsField: JTextArea? = null

    // PackyAPI fields
    private var packyapiBaseUrlField: JTextField? = null
    private var packyapiAccessTokenField: JPasswordField? = null
    private var packyapiUserIdField: JTextField? = null
    private var packyapiSpeedTestUrlsField: JTextArea? = null

    // PackyCode fields (不需要 userId)
    private var packycodeBaseUrlField: JTextField? = null
    private var packycodeAccessTokenField: JPasswordField? = null
    private var packycodeSpeedTestUrlsField: JTextArea? = null

    // Cubence fields (不需要 userId)
    private var cubenceBaseUrlField: JTextField? = null
    private var cubenceAccessTokenField: JPasswordField? = null
    private var cubenceSpeedTestUrlsField: JTextArea? = null

    // 默认值常量
    companion object {
        const val DEFAULT_PACKYAPI_BASE_URL = "https://www.packyapi.com"
        const val DEFAULT_PACKYCODE_BASE_URL = "https://codex.packycode.com"
        val DEFAULT_PACKYAPI_SPEED_TEST_URLS = listOf(
            "https://www.packyapi.com",
            "https://api-slb.packyapi.com"
        )
        val DEFAULT_PACKYCODE_SPEED_TEST_URLS = listOf(
            "https://codex.packycode.com",
            "https://codex-api.packycode.com",
            "https://codex-api-slb.packycode.com"
        )
        const val DEFAULT_CUBENCE_BASE_URL = "https://cubence.com"
        val DEFAULT_CUBENCE_SPEED_TEST_URLS = listOf(
            "https://api-cf.cubence.com",
            "https://api-bwg.cubence.com",
            "https://api-dmit.cubence.com",
            "https://api.cubence.com"
        )
    }

    override fun getDisplayName(): String = "Quota Bar"

    override fun createComponent(): JComponent {
        mainPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(5, 5, 5, 5)
            anchor = GridBagConstraints.WEST
        }

        var row = 0

        // Basic settings section
        addSectionHeader(mainPanel!!, "基础设置", gbc, row++)

        enabledCheckBox = JCheckBox("启用配额监控")
        addField(mainPanel!!, "", enabledCheckBox!!, gbc, row++)

        speedTestEnabledCheckBox = JCheckBox("启用测速")
        addField(mainPanel!!, "", speedTestEnabledCheckBox!!, gbc, row++)

        platformTypeComboBox = JComboBox(arrayOf("newapi", "packyapi", "packycode", "cubence"))
        addField(mainPanel!!, "平台类型:", platformTypeComboBox!!, gbc, row++)

        pollingIntervalField = JTextField(10)
        addField(mainPanel!!, "轮询间隔 (ms):", pollingIntervalField!!, gbc, row++)

        displayStyleComboBox = JComboBox(arrayOf("remaining", "percentage", "both"))
        addField(mainPanel!!, "显示样式:", displayStyleComboBox!!, gbc, row++)

        val resetButton = JButton("恢复默认配置")
        resetButton.addActionListener { resetToDefaults() }
        addField(mainPanel!!, "", resetButton, gbc, row++)

        // NewAPI settings section
        addSectionHeader(mainPanel!!, "NewAPI", gbc, row++)

        newapiBaseUrlField = JTextField(30)
        addField(mainPanel!!, "Base URL:", newapiBaseUrlField!!, gbc, row++)

        newapiAccessTokenField = JPasswordField(30)
        addField(mainPanel!!, "Access Token:", newapiAccessTokenField!!, gbc, row++)

        newapiUserIdField = JTextField(30)
        addField(mainPanel!!, "User ID:", newapiUserIdField!!, gbc, row++)

        newapiSpeedTestUrlsField = JTextArea(3, 30).apply {
            toolTipText = "每行一个测速地址"
            lineWrap = true
        }
        addField(mainPanel!!, "测速地址:", JScrollPane(newapiSpeedTestUrlsField), gbc, row++)

        // PackyAPI settings section
        addSectionHeader(mainPanel!!, "PackyAPI", gbc, row++)

        packyapiBaseUrlField = JTextField(30).apply {
            toolTipText = "默认: $DEFAULT_PACKYAPI_BASE_URL"
        }
        addField(mainPanel!!, "Base URL:", packyapiBaseUrlField!!, gbc, row++)

        packyapiAccessTokenField = JPasswordField(30)
        addField(mainPanel!!, "Access Token:", packyapiAccessTokenField!!, gbc, row++)

        packyapiUserIdField = JTextField(30)
        addField(mainPanel!!, "User ID:", packyapiUserIdField!!, gbc, row++)

        packyapiSpeedTestUrlsField = JTextArea(3, 30).apply {
            toolTipText = "每行一个测速地址"
            lineWrap = true
        }
        addField(mainPanel!!, "测速地址:", JScrollPane(packyapiSpeedTestUrlsField), gbc, row++)

        // PackyCode settings section (不需要 userId)
        addSectionHeader(mainPanel!!, "PackyCode", gbc, row++)

        packycodeBaseUrlField = JTextField(30).apply {
            toolTipText = "默认: $DEFAULT_PACKYCODE_BASE_URL"
        }
        addField(mainPanel!!, "Base URL:", packycodeBaseUrlField!!, gbc, row++)

        packycodeAccessTokenField = JPasswordField(30)
        addField(mainPanel!!, "Access Token:", packycodeAccessTokenField!!, gbc, row++)

        packycodeSpeedTestUrlsField = JTextArea(3, 30).apply {
            toolTipText = "每行一个测速地址"
            lineWrap = true
        }
        addField(mainPanel!!, "测速地址:", JScrollPane(packycodeSpeedTestUrlsField), gbc, row++)

        // Cubence settings section (不需要 userId)
        addSectionHeader(mainPanel!!, "Cubence", gbc, row++)

        cubenceBaseUrlField = JTextField(30).apply {
            toolTipText = "默认: $DEFAULT_CUBENCE_BASE_URL"
        }
        addField(mainPanel!!, "Base URL:", cubenceBaseUrlField!!, gbc, row++)

        cubenceAccessTokenField = JPasswordField(30)
        addField(mainPanel!!, "Access Token:", cubenceAccessTokenField!!, gbc, row++)

        cubenceSpeedTestUrlsField = JTextArea(3, 30).apply {
            toolTipText = "每行一个测速地址"
            lineWrap = true
        }
        addField(mainPanel!!, "测速地址:", JScrollPane(cubenceSpeedTestUrlsField), gbc, row++)

        // Add filler
        gbc.gridy = row
        gbc.weighty = 1.0
        gbc.fill = GridBagConstraints.BOTH
        mainPanel!!.add(JPanel(), gbc)

        reset()
        return mainPanel!!
    }

    private fun addSectionHeader(panel: JPanel, title: String, gbc: GridBagConstraints, row: Int) {
        gbc.gridy = row
        gbc.gridx = 0
        gbc.gridwidth = 2
        val label = JLabel("<html><b>$title</b></html>")
        label.border = BorderFactory.createEmptyBorder(10, 0, 5, 0)
        panel.add(label, gbc)
        gbc.gridwidth = 1
    }

    private fun addField(panel: JPanel, label: String, component: JComponent, gbc: GridBagConstraints, row: Int) {
        gbc.gridy = row
        if (label.isNotEmpty()) {
            gbc.gridx = 0
            gbc.weightx = 0.0
            panel.add(JLabel(label), gbc)
            gbc.gridx = 1
            gbc.weightx = 1.0
        } else {
            gbc.gridx = 0
            gbc.gridwidth = 2
            gbc.weightx = 1.0
        }
        panel.add(component, gbc)
        gbc.gridwidth = 1
    }
    
    private fun urlsToText(urls: List<String>): String = urls.joinToString("\n")
    
    private fun textToUrls(text: String): MutableList<String> {
        return text.split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toMutableList()
    }

    override fun isModified(): Boolean {
        val settings = QuotaSettings.getInstance().state
        return enabledCheckBox?.isSelected != settings.enabled ||
                speedTestEnabledCheckBox?.isSelected != settings.speedTestEnabled ||
                platformTypeComboBox?.selectedItem != settings.platformType ||
                pollingIntervalField?.text != settings.pollingInterval.toString() ||
                displayStyleComboBox?.selectedItem != settings.displayStyle ||
                newapiBaseUrlField?.text != settings.newapiBaseUrl ||
                String(newapiAccessTokenField?.password ?: charArrayOf()) != settings.newapiAccessToken ||
                newapiUserIdField?.text != settings.newapiUserId ||
                newapiSpeedTestUrlsField?.text != urlsToText(settings.newapiSpeedTestUrls) ||
                packyapiBaseUrlField?.text != settings.packyapiBaseUrl ||
                String(packyapiAccessTokenField?.password ?: charArrayOf()) != settings.packyapiAccessToken ||
                packyapiUserIdField?.text != settings.packyapiUserId ||
                packyapiSpeedTestUrlsField?.text != urlsToText(settings.packyapiSpeedTestUrls) ||
                packycodeBaseUrlField?.text != settings.packycodeBaseUrl ||
                String(packycodeAccessTokenField?.password ?: charArrayOf()) != settings.packycodeAccessToken ||
                packycodeSpeedTestUrlsField?.text != urlsToText(settings.packycodeSpeedTestUrls) ||
                cubenceBaseUrlField?.text != settings.cubenceBaseUrl ||
                String(cubenceAccessTokenField?.password ?: charArrayOf()) != settings.cubenceAccessToken ||
                cubenceSpeedTestUrlsField?.text != urlsToText(settings.cubenceSpeedTestUrls)
    }

    override fun apply() {
        val settings = QuotaSettings.getInstance().state
        settings.enabled = enabledCheckBox?.isSelected ?: true
        settings.speedTestEnabled = speedTestEnabledCheckBox?.isSelected ?: true
        settings.platformType = platformTypeComboBox?.selectedItem as? String ?: "newapi"
        settings.pollingInterval = pollingIntervalField?.text?.toLongOrNull() ?: 60000
        settings.displayStyle = displayStyleComboBox?.selectedItem as? String ?: "remaining"

        settings.newapiBaseUrl = newapiBaseUrlField?.text ?: ""
        settings.newapiAccessToken = String(newapiAccessTokenField?.password ?: charArrayOf())
        settings.newapiUserId = newapiUserIdField?.text ?: ""
        settings.newapiSpeedTestUrls = textToUrls(newapiSpeedTestUrlsField?.text ?: "")

        // PackyAPI：如果为空，使用默认值
        val packyapiUrl = packyapiBaseUrlField?.text?.trim() ?: ""
        settings.packyapiBaseUrl = packyapiUrl.ifEmpty { DEFAULT_PACKYAPI_BASE_URL }
        settings.packyapiAccessToken = String(packyapiAccessTokenField?.password ?: charArrayOf())
        settings.packyapiUserId = packyapiUserIdField?.text ?: ""
        val packyapiSpeedUrls = textToUrls(packyapiSpeedTestUrlsField?.text ?: "")
        settings.packyapiSpeedTestUrls = if (packyapiSpeedUrls.isEmpty()) DEFAULT_PACKYAPI_SPEED_TEST_URLS.toMutableList() else packyapiSpeedUrls

        // PackyCode：如果为空，使用默认值
        val packycodeUrl = packycodeBaseUrlField?.text?.trim() ?: ""
        settings.packycodeBaseUrl = packycodeUrl.ifEmpty { DEFAULT_PACKYCODE_BASE_URL }
        settings.packycodeAccessToken = String(packycodeAccessTokenField?.password ?: charArrayOf())
        val packycodeSpeedUrls = textToUrls(packycodeSpeedTestUrlsField?.text ?: "")
        settings.packycodeSpeedTestUrls = if (packycodeSpeedUrls.isEmpty()) DEFAULT_PACKYCODE_SPEED_TEST_URLS.toMutableList() else packycodeSpeedUrls

        // Cubence：如果为空，使用默认值
        val cubenceUrl = cubenceBaseUrlField?.text?.trim() ?: ""
        settings.cubenceBaseUrl = cubenceUrl.ifEmpty { DEFAULT_CUBENCE_BASE_URL }
        settings.cubenceAccessToken = String(cubenceAccessTokenField?.password ?: charArrayOf())
        val cubenceSpeedUrls = textToUrls(cubenceSpeedTestUrlsField?.text ?: "")
        settings.cubenceSpeedTestUrls = if (cubenceSpeedUrls.isEmpty()) DEFAULT_CUBENCE_SPEED_TEST_URLS.toMutableList() else cubenceSpeedUrls
    }

    override fun reset() {
        val settings = QuotaSettings.getInstance().state
        enabledCheckBox?.isSelected = settings.enabled
        speedTestEnabledCheckBox?.isSelected = settings.speedTestEnabled
        platformTypeComboBox?.selectedItem = settings.platformType
        pollingIntervalField?.text = settings.pollingInterval.toString()
        displayStyleComboBox?.selectedItem = settings.displayStyle

        newapiBaseUrlField?.text = settings.newapiBaseUrl
        newapiAccessTokenField?.text = settings.newapiAccessToken
        newapiUserIdField?.text = settings.newapiUserId
        newapiSpeedTestUrlsField?.text = urlsToText(settings.newapiSpeedTestUrls)

        // PackyAPI：如果为空，显示默认值
        packyapiBaseUrlField?.text = settings.packyapiBaseUrl.ifEmpty { DEFAULT_PACKYAPI_BASE_URL }
        packyapiAccessTokenField?.text = settings.packyapiAccessToken
        packyapiUserIdField?.text = settings.packyapiUserId
        val packyapiUrls = if (settings.packyapiSpeedTestUrls.isEmpty()) DEFAULT_PACKYAPI_SPEED_TEST_URLS else settings.packyapiSpeedTestUrls
        packyapiSpeedTestUrlsField?.text = urlsToText(packyapiUrls)

        // PackyCode：如果为空，显示默认值
        packycodeBaseUrlField?.text = settings.packycodeBaseUrl.ifEmpty { DEFAULT_PACKYCODE_BASE_URL }
        packycodeAccessTokenField?.text = settings.packycodeAccessToken
        val packycodeUrls = if (settings.packycodeSpeedTestUrls.isEmpty()) DEFAULT_PACKYCODE_SPEED_TEST_URLS else settings.packycodeSpeedTestUrls
        packycodeSpeedTestUrlsField?.text = urlsToText(packycodeUrls)

        // Cubence：如果为空，显示默认值
        cubenceBaseUrlField?.text = settings.cubenceBaseUrl.ifEmpty { DEFAULT_CUBENCE_BASE_URL }
        cubenceAccessTokenField?.text = settings.cubenceAccessToken
        val cubenceUrls = if (settings.cubenceSpeedTestUrls.isEmpty()) DEFAULT_CUBENCE_SPEED_TEST_URLS else settings.cubenceSpeedTestUrls
        cubenceSpeedTestUrlsField?.text = urlsToText(cubenceUrls)
    }

    private fun resetToDefaults() {
        enabledCheckBox?.isSelected = true
        speedTestEnabledCheckBox?.isSelected = true
        platformTypeComboBox?.selectedItem = "newapi"
        pollingIntervalField?.text = "60000"
        displayStyleComboBox?.selectedItem = "remaining"

        newapiBaseUrlField?.text = ""
        newapiAccessTokenField?.text = ""
        newapiUserIdField?.text = ""
        newapiSpeedTestUrlsField?.text = ""

        packyapiBaseUrlField?.text = DEFAULT_PACKYAPI_BASE_URL
        packyapiAccessTokenField?.text = ""
        packyapiUserIdField?.text = ""
        packyapiSpeedTestUrlsField?.text = urlsToText(DEFAULT_PACKYAPI_SPEED_TEST_URLS)

        packycodeBaseUrlField?.text = DEFAULT_PACKYCODE_BASE_URL
        packycodeAccessTokenField?.text = ""
        packycodeSpeedTestUrlsField?.text = urlsToText(DEFAULT_PACKYCODE_SPEED_TEST_URLS)

        cubenceBaseUrlField?.text = DEFAULT_CUBENCE_BASE_URL
        cubenceAccessTokenField?.text = ""
        cubenceSpeedTestUrlsField?.text = urlsToText(DEFAULT_CUBENCE_SPEED_TEST_URLS)
    }
}
