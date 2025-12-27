package com.yovinchen.apiquotawatcher.widget

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

class QuotaStatusBarWidgetFactory : StatusBarWidgetFactory {

    override fun getId(): String = "ApiQuotaWatcher"

    override fun getDisplayName(): String = "API Quota Watcher"

    override fun isAvailable(project: Project): Boolean = true

    override fun createWidget(project: Project): StatusBarWidget {
        return QuotaStatusBarWidget(project)
    }

    override fun disposeWidget(widget: StatusBarWidget) {
        widget.dispose()
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}
