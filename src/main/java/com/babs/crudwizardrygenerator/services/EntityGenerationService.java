package com.babs.crudwizardrygenerator.services;

import com.babs.crudwizardrygenerator.dtos.EntityData;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;

public final class EntityGenerationService {
    public GenerationResult generate(Project project, EntityData data, PsiDirectory sourceDirectory,
                                     GenerationInteraction interaction) {
        if (data == null || sourceDirectory == null) {
            return GenerationResult.failure("Entity data and a source directory are required.");
        }
        return new EntityGenerator(project, data, sourceDirectory, interaction).generate();
    }
}
