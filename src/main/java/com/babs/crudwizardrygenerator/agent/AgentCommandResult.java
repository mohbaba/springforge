package com.babs.crudwizardrygenerator.agent;

import com.google.gson.JsonElement;

public record AgentCommandResult(String id, boolean success, JsonElement result, AgentError error) {
    public static AgentCommandResult success(String id, JsonElement result) {
        return new AgentCommandResult(id, true, result, null);
    }

    public static AgentCommandResult failure(String id, AgentErrorCode code, String message) {
        return new AgentCommandResult(id, false, null, new AgentError(code, message));
    }
}
