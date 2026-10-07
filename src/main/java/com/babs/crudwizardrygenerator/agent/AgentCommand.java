package com.babs.crudwizardrygenerator.agent;

import com.google.gson.JsonObject;

public record AgentCommand(String id, String tool, JsonObject arguments) {
    public AgentCommand {
        arguments = arguments == null ? new JsonObject() : arguments;
    }
}
