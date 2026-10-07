package com.babs.crudwizardrygenerator.agent;

import com.intellij.openapi.project.Project;

import java.util.Set;

/** A transport- and AI-provider-independent operation over the currently open project. */
public interface AgentTool<I, O> {
    String name();

    String description();

    Class<I> inputType();

    default Set<AgentPermission> requiredPermissions() {
        return Set.of(AgentPermission.READ_PROJECT);
    }

    default boolean requiresIndex() {
        return false;
    }

    default boolean mutating() {
        return false;
    }

    O execute(Project project, I input);
}
