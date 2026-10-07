package com.babs.crudwizardrygenerator.agent;

/** Permission capabilities are metadata today and an enforcement seam for Phase 2. */
public enum AgentPermission {
    READ_PROJECT,
    READ_SOURCE,
    SEARCH_CODE,
    PROPOSE_CHANGES,
    MODIFY_CODE,
    RUN_TESTS,
    RUN_BUILD,
    GIT_READ
}
