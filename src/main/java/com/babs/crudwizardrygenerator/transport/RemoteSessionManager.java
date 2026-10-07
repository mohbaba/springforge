package com.babs.crudwizardrygenerator.transport;

import java.util.Objects;

/** Lifecycle holder only; authentication, reconnect, and protocol logic belong to Phase 2. */
public final class RemoteSessionManager {
    private final AgentTransport transport;

    public RemoteSessionManager(AgentTransport transport) {
        this.transport = Objects.requireNonNull(transport);
    }

    public void start() { transport.connect(); }
    public void stop() { transport.disconnect(); }
    public boolean isConnected() { return transport.isConnected(); }
}
