package com.babs.crudwizardrygenerator.agent;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/** Converts untrusted command JSON into typed inputs and contains all failures. */
public final class AgentCommandDispatcher {
    private static final Logger LOG = Logger.getInstance(AgentCommandDispatcher.class);
    private final AgentToolRegistry registry;
    private final Gson gson;

    public AgentCommandDispatcher(Project project) {
        this(project.getService(AgentToolRegistry.class));
    }

    public AgentCommandDispatcher(AgentToolRegistry registry) {
        this(registry, new Gson());
    }

    AgentCommandDispatcher(AgentToolRegistry registry, Gson gson) {
        this.registry = registry;
        this.gson = gson;
    }

    public AgentCommandResult dispatch(Project project, @NotNull AgentCommand command) {
        String commandId = command.id();
        if (project == null || project.isDisposed()) {
            return AgentCommandResult.failure(commandId, AgentErrorCode.PROJECT_NOT_AVAILABLE, "The project is not available.");
        }
        if (command.tool() == null || command.tool().isBlank()) {
            return AgentCommandResult.failure(commandId, AgentErrorCode.INVALID_ARGUMENTS, "tool is required.");
        }

        AgentTool<?, ?> tool = registry.get(command.tool());
        if (tool == null) {
            return AgentCommandResult.failure(commandId, AgentErrorCode.TOOL_NOT_FOUND,
                    "Unknown tool: " + command.tool());
        }
        if (tool.requiresIndex() && DumbService.isDumb(project)) {
            return AgentCommandResult.failure(commandId, AgentErrorCode.INDEX_NOT_READY,
                    "The tool requires project indices; wait for indexing to finish and retry.");
        }

        try {
            return dispatchTyped(project, command, tool);
        } catch (AgentToolException ex) {
            LOG.info("Agent tool failed safely: " + tool.name(), ex);
            return AgentCommandResult.failure(commandId, ex.getCode(), safeMessage(ex));
        } catch (JsonParseException | IllegalArgumentException ex) {
            return AgentCommandResult.failure(commandId, AgentErrorCode.INVALID_ARGUMENTS, safeMessage(ex));
        } catch (Throwable ex) {
            LOG.warn("Unhandled failure in agent tool " + tool.name(), ex);
            return AgentCommandResult.failure(commandId, AgentErrorCode.INTERNAL_ERROR,
                    "The tool failed unexpectedly. See the IDE log for details.");
        }
    }

    private <I, O> AgentCommandResult dispatchTyped(Project project, AgentCommand command, AgentTool<I, O> tool) {
        I input = gson.fromJson(command.arguments(), tool.inputType());
        O output = tool.execute(project, input);
        return AgentCommandResult.success(command.id(), gson.toJsonTree(output));
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? "Invalid command arguments." : message;
    }
}
