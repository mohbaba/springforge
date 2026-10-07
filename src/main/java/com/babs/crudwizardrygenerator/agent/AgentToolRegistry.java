package com.babs.crudwizardrygenerator.agent;

import com.babs.crudwizardrygenerator.agent.tools.ApplyPatchTool;
import com.babs.crudwizardrygenerator.agent.tools.GenerationTools;
import com.babs.crudwizardrygenerator.agent.tools.InspectionTools;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AgentToolRegistry {
    private final Map<String, AgentTool<?, ?>> tools = new LinkedHashMap<>();

    public AgentToolRegistry() {
        register(new InspectionTools.GetProjectInfo());
        register(new InspectionTools.GetProjectStructure());
        register(new InspectionTools.FindClass());
        register(new InspectionTools.GetClassDetails());
        register(new InspectionTools.FindMethod());
        register(new InspectionTools.FindUsages());
        register(new InspectionTools.ReadFile());
        register(new InspectionTools.GetProjectProblems());
        register(new InspectionTools.GetGitDiff());
        register(new GenerationTools.GenerateEntity());
        register(new GenerationTools.GenerateCrud());
        register(new GenerationTools.GenerateController());
        register(new ApplyPatchTool());
    }

    public synchronized void register(AgentTool<?, ?> tool) {
        if (tool == null || tool.name() == null || tool.name().isBlank()) {
            throw new IllegalArgumentException("Tool and stable tool name are required.");
        }
        if (tools.putIfAbsent(tool.name(), tool) != null) {
            throw new IllegalArgumentException("Duplicate agent tool name: " + tool.name());
        }
    }

    public synchronized AgentTool<?, ?> get(String name) {
        return tools.get(name);
    }

    public synchronized Collection<AgentTool<?, ?>> all() {
        return Collections.unmodifiableCollection(tools.values());
    }
}
