package com.babs.crudwizardrygenerator.agent;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

/** Small developer-only JSON harness; networking is intentionally absent. */
public final class LocalAgentCommandAction extends AnAction {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String EXAMPLE = "{\n" +
            "  \"id\": \"local-1\",\n" +
            "  \"tool\": \"find_class\",\n" +
            "  \"arguments\": { \"name\": \"UserService\" }\n" +
            "}";

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) return;
        String json = Messages.showMultilineInputDialog(project,
                "Enter an AgentCommand JSON object:", "SpringForge Local Agent Command",
                EXAMPLE, Messages.getQuestionIcon(), null);
        if (json == null) return;

        AgentCommandResult result;
        try {
            AgentCommand command = GSON.fromJson(json, AgentCommand.class);
            if (command == null) throw new JsonParseException("Command must be a JSON object.");
            result = project.getService(AgentCommandDispatcher.class).dispatch(project, command);
        } catch (JsonParseException ex) {
            result = AgentCommandResult.failure(null, AgentErrorCode.INVALID_ARGUMENTS,
                    ex.getMessage() == null ? "Invalid JSON." : ex.getMessage());
        }
        Messages.showMultilineInputDialog(project, "Structured result (copy as needed):",
                "SpringForge Agent Result", GSON.toJson(result), Messages.getInformationIcon(), null);
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabledAndVisible(event.getProject() != null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
