package com.babs.crudwizardrygenerator.services;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;

public final class ControllerGenerationService {
    public GenerationResult generateFromServiceMethod(Project project, PsiMethod method,
                                                      GenerationInteraction interaction) {
        if (method == null) return GenerationResult.failure("A service method is required.");
        return new ControllerEndpointGenerator(project).generateFor(method, interaction);
    }
}
