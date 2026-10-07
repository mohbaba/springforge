package com.babs.crudwizardrygenerator.agent;

public class AgentToolException extends RuntimeException {
    private final AgentErrorCode code;

    public AgentToolException(AgentErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public AgentToolException(AgentErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public AgentErrorCode getCode() {
        return code;
    }
}
