package com.yovinchen.apiquotawatcher.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(
    name = "com.yovinchen.apiquotawatcher.settings.QuotaSettings",
    storages = [Storage("ApiQuotaWatcher.xml")]
)
class QuotaSettings : PersistentStateComponent<QuotaSettings.State> {

    data class State(
        var enabled: Boolean = true,
        var speedTestEnabled: Boolean = true,
        var platformType: String = "newapi",
        var pollingInterval: Long = 60000,

        // 小组件开关
        var widgetStatusIcon: Boolean = true,
        var widgetPercentage: Boolean = true,
        var widgetUsed: Boolean = false,
        var widgetTotal: Boolean = false,
        var widgetLatency: Boolean = true,

        // NewAPI settings
        var newapiBaseUrl: String = "",
        var newapiAccessToken: String = "",
        var newapiUserId: String = "",
        var newapiSpeedTestUrls: MutableList<String> = mutableListOf(),

        // PackyAPI settings
        var packyapiBaseUrl: String = "https://www.packyapi.com",
        var packyapiAccessToken: String = "",
        var packyapiUserId: String = "",
        var packyapiSpeedTestUrls: MutableList<String> = mutableListOf(
            "https://www.packyapi.com",
            "https://api-slb.packyapi.com"
        ),

        // PackyCode settings (包月，不需要 userId)
        var packycodeBaseUrl: String = "https://codex.packycode.com",
        var packycodeAccessToken: String = "",
        var packycodeSpeedTestUrls: MutableList<String> = mutableListOf(
            "https://codex.packycode.com",
            "https://codex-api.packycode.com",
            "https://codex-api-slb.packycode.com"
        ),

        // Cubence settings
        var cubenceBaseUrl: String = "https://cubence.com",
        var cubenceAccessToken: String = "",
        var cubenceSpeedTestUrls: MutableList<String> = mutableListOf(
            "https://cubence.com"
        )
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        fun getInstance(): QuotaSettings {
            return ApplicationManager.getApplication().getService(QuotaSettings::class.java)
        }
    }
}
