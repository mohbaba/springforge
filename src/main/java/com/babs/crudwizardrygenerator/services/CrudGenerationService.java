package com.babs.crudwizardrygenerator.services;

import com.babs.crudwizardrygenerator.dtos.EntityData;
import com.babs.crudwizardrygenerator.dtos.PersistenceApi;
import com.babs.crudwizardrygenerator.utils.PackageNamingUtils;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.GlobalSearchScope;
import dtos.FieldData;

import java.util.ArrayList;
import java.util.List;

/** Shared PSI-to-generator adapter used by actions and agent tools. */
public final class CrudGenerationService {
    public record Options(boolean controller, boolean service, boolean repository, boolean dto,
                          boolean serviceInterface) {}

    public GenerationResult generate(Project project, PsiClass selectedClass, Options options,
                                     GenerationInteraction interaction) {
        PsiFile file = selectedClass == null ? null : selectedClass.getContainingFile();
        if (!(file instanceof PsiJavaFile javaFile) || file.getContainingDirectory() == null) {
            return GenerationResult.failure("A Java class inside a source directory is required.");
        }
        String className = selectedClass.getName();
        if (className == null || className.isBlank()) {
            return GenerationResult.failure("The selected class has no name.");
        }
        String entityName = baseEntityName(className);
        String entityPackage = findEntityPackage(project, entityName, javaFile.getPackageName());
        EntityData data = new EntityData(entityName, entityPackage,
                selectedClass.hasAnnotation("lombok.Data"), selectedClass.hasAnnotation("lombok.Builder"),
                persistenceApi(selectedClass), fields(selectedClass), false, options.controller(), options.service(),
                options.repository(), options.dto(), options.serviceInterface());
        return new EntityGenerator(project, data, file.getContainingDirectory(), interaction).generate();
    }

    public EntityData prefillEntity(PsiClass psiClass, String fallbackPackageName) {
        String entityName = psiClass.getName() == null ? "NewEntity" : psiClass.getName();
        String packageName = psiClass.getContainingFile() instanceof PsiJavaFile javaFile
                ? javaFile.getPackageName() : fallbackPackageName;
        return new EntityData(entityName, packageName, psiClass.hasAnnotation("lombok.Data"),
                psiClass.hasAnnotation("lombok.Builder"), persistenceApi(psiClass), declaredFields(psiClass),
                false, true, true, true, true, false);
    }

    private String findEntityPackage(Project project, String entityName, String currentPackage) {
        JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        if (facade.findClass(currentPackage + "." + entityName, scope) != null) return currentPackage;
        for (PsiClass candidate : facade.findClasses(entityName, scope)) {
            if (candidate.getContainingFile() instanceof PsiJavaFile javaFile) return javaFile.getPackageName();
        }
        return PackageNamingUtils.deriveEntityPackageFromLayer(currentPackage);
    }

    private String baseEntityName(String className) {
        for (String suffix : new String[]{"Controller", "Repository", "Service", "Dto"}) {
            if (className.endsWith(suffix)) return className.substring(0, className.length() - suffix.length());
        }
        return className;
    }

    private List<FieldData> fields(PsiClass psiClass) {
        List<FieldData> result = new ArrayList<>();
        for (PsiField field : psiClass.getAllFields()) {
            if (!field.hasModifierProperty(PsiModifier.STATIC)) {
                result.add(new FieldData(field.getName(), field.getType().getPresentableText(), false));
            }
        }
        return result;
    }

    private List<FieldData> declaredFields(PsiClass psiClass) {
        List<FieldData> result = new ArrayList<>();
        for (PsiField field : psiClass.getFields()) {
            if (!field.hasModifierProperty(PsiModifier.STATIC)) {
                result.add(new FieldData(field.getName(), field.getType().getPresentableText(), false));
            }
        }
        return result;
    }

    private PersistenceApi persistenceApi(PsiClass psiClass) {
        if (psiClass.hasAnnotation("javax.persistence.Entity")) return PersistenceApi.JAVAX;
        if (psiClass.hasAnnotation("jakarta.persistence.Entity")) return PersistenceApi.JAKARTA;
        return PersistenceApi.AUTO;
    }
}
