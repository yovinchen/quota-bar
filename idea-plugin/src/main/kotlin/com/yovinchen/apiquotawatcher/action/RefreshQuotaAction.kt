package com.yovinchen.apiquotawatcher.action

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.yovinchen.apiquotawatcher.service.QuotaServiceImpl
import com.yovinchen.apiquotawatcher.widget.QuotaStatusBarWidget

class RefreshQuotaAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        ApplicationManager.getApplication().executeOnPooledThread {
            val service = QuotaServiceImpl.getInstance()
            val info = service.fetchQuota()

            ApplicationManager.getApplication().invokeLater {
                if (info != null) {
                    val text = service.getDisplayText(info)
                    showNotification(
                        project,
                        "API Quota Refreshed",
                        "Remaining: $text\nUsed: $${String.format("%.2f", info.used)} / Total: $${String.format("%.2f", info.total)}",
                        NotificationType.INFORMATION
                    )
                } else {
                    showNotification(
                        project,
                        "Quota Refresh Failed",
                        "Failed to fetch quota information. Please check your settings.",
                        NotificationType.ERROR
                    )
                }

                // Refresh the status bar widget
                refreshWidget(project)
            }
        }
    }

    private fun refreshWidget(project: Project) {
        val statusBar = WindowManager.getInstance().getStatusBar(project) ?: return
        val widget = statusBar.getWidget(QuotaStatusBarWidget.ID)
        if (widget is QuotaStatusBarWidget) {
            widget.refreshQuota()
        }
    }

    private fun showNotification(project: Project, title: String, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("API Quota Watcher")
            .createNotification(title, content, type)
            .notify(project)
    }
}
