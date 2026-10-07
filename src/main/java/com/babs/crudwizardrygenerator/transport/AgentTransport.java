package com.babs.crudwizardrygenerator.transport;

/** Phase 2 seam for an authenticated outbound transport. No network implementation exists yet. */
public interface AgentTransport {
    void connect();
    void disconnect();
    boolean isConnected();
}
