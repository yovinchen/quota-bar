package com.yovinchen.apiquotawatcher.action

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.yovinchen.apiquotawatcher.service.QuotaServiceImpl
import com.yovinchen.apiquotawatcher.settings.QuotaSettings

class SpeedTestAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val settings = QuotaSettings.getInstance().state
        if (!settings.speedTestEnabled) {
            showNotification(
                project,
                "Speed Test Disabled",
                "Speed test is disabled in settings.",
                NotificationType.WARNING
            )
            return
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            val service = QuotaServiceImpl.getInstance()
            val speedMs = service.testSpeed()

            ApplicationManager.getApplication().invokeLater {
                if (speedMs != null) {
                    val quality = when {
                        speedMs < 500 -> "Excellent"
                        speedMs < 1000 -> "Good"
                        speedMs < 2000 -> "Fair"
                        else -> "Poor"
                    }
                    showNotification(
                        project,
                        "Speed Test Complete",
                        "Response time: ${speedMs}ms ($quality)",
                        NotificationType.INFORMATION
                    )
                } else {
                    showNotification(
                        project,
                        "Speed Test Failed",
                        "Failed to complete speed test. Please check your connection and settings.",
                        NotificationType.ERROR
                    )
                }
            }
        }
    }

    private fun showNotification(project: Project, title: String, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("API Quota Watcher")
            .createNotification(title, content, type)
            .notify(project)
    }
}
