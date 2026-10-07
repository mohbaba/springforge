package com.babs.crudwizardrygenerator.services;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

public final class IdeGenerationInteraction implements GenerationInteraction {
    private static final String NOTIFICATION_GROUP = "SpringForge Notifications";
    private final Project project;

    public IdeGenerationInteraction(Project project) {
        this.project = project;
    }

    public boolean confirmOverwrite(String fileName, String packageName) {
        return Messages.showYesNoDialog(project,
                "File '" + fileName + "' already exists in " + packageName + ".\nDo you want to overwrite it?",
                "File Already Exists", Messages.getQuestionIcon()) == Messages.YES;
    }

    public boolean confirmCreateController(String serviceName) {
        return Messages.showYesNoDialog(project,
                "No matching controller class was found for " + serviceName +
                        ".\nDo you want to create one and add this endpoint?",
                "Generate Controller Endpoint", Messages.getQuestionIcon()) == Messages.YES;
    }

    public void info(String message) {
        ApplicationManager.getApplication().invokeLater(() ->
                Messages.showInfoMessage(project, message, "SpringForge"));
    }

    public void success(String message) {
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)
                        .createNotification(message, NotificationType.INFORMATION).notify(project);
            } catch (Throwable ignored) {
                // The notification is best-effort; generation has already succeeded.
            }
        });
    }
}
