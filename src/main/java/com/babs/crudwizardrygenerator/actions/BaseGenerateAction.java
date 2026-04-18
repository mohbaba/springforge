package com.babs.crudwizardrygenerator.actions;

import com.babs.crudwizardrygenerator.dtos.EntityData;
import com.babs.crudwizardrygenerator.services.EntityGenerator;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import dtos.FieldData;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseGenerateAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        PsiFile psiFile = getPsiFile(e);
        if (!(psiFile instanceof PsiJavaFile javaFile)) return;

        PsiClass[] classes = javaFile.getClasses();
        if (classes.length == 0) return;
        PsiClass psiClass = classes[0];

        String className = psiClass.getName();
        if (className == null) return;

        String entityName = getEntityName(className);
        String packageName = getEntityPackage(project, entityName, javaFile.getPackageName());
        
        List<FieldData> fields = extractFields(psiClass);

        EntityData entityData = new EntityData(
                entityName,
                packageName,
                hasLombokAnnotation(psiClass, "lombok.Data"),
                hasLombokAnnotation(psiClass, "lombok.Builder"),
                fields,
                false, // generateEntity - false because we are generating from an existing class
                isGenerateController(),
                isGenerateService(),
                isGenerateRepository(),
                isGenerateDto(),
                isGenerateServiceInterface()
        );

        PsiDirectory dir = psiFile.getContainingDirectory();
        new EntityGenerator(project, entityData, dir).generate();
    }

    protected abstract boolean isGenerateController();
    protected abstract boolean isGenerateService();
    protected abstract boolean isGenerateRepository();
    protected abstract boolean isGenerateDto();
    
    protected boolean isGenerateServiceInterface() {
        return false;
    }

    private String getEntityName(String className) {
        if (className.endsWith("Dto")) return className.substring(0, className.length() - 3);
        if (className.endsWith("Controller")) return className.substring(0, className.length() - 10);
        if (className.endsWith("Service")) return className.substring(0, className.length() - 7);
        if (className.endsWith("Repository")) return className.substring(0, className.length() - 10);
        return className;
    }

    private String getEntityPackage(Project project, String entityName, String currentPackage) {
        // Try to find the entity class in the project
        PsiClass[] classes = JavaPsiFacade.getInstance(project).findClasses(currentPackage + "." + entityName, GlobalSearchScope.projectScope(project));
        if (classes.length > 0) return currentPackage;

        // Search globally for the entity name
        classes = JavaPsiFacade.getInstance(project).findClasses(entityName, GlobalSearchScope.projectScope(project));
        for (PsiClass cls : classes) {
            PsiFile file = cls.getContainingFile();
            if (file instanceof PsiJavaFile) {
                return ((PsiJavaFile) file).getPackageName();
            }
        }
        
        // Fallback heuristics
        if (currentPackage.endsWith(".dto")) return currentPackage.substring(0, currentPackage.length() - 4) + ".entity";
        if (currentPackage.endsWith(".controller")) return currentPackage.substring(0, currentPackage.length() - 11) + ".entity";
        if (currentPackage.endsWith(".service")) return currentPackage.substring(0, currentPackage.length() - 8) + ".entity";
        if (currentPackage.endsWith(".repository")) return currentPackage.substring(0, currentPackage.length() - 11) + ".entity";
        
        return currentPackage;
    }

    private List<FieldData> extractFields(PsiClass psiClass) {
        List<FieldData> fields = new ArrayList<>();
        for (PsiField field : psiClass.getAllFields()) {
            // Skip static fields
            if (field.hasModifierProperty(PsiModifier.STATIC)) continue;
            
            String name = field.getName();
            String type = field.getType().getPresentableText();
            fields.add(new FieldData(name, type, false));
        }
        return fields;
    }
    
    private boolean hasLombokAnnotation(PsiClass psiClass, String annotationFqn) {
        return psiClass.hasAnnotation(annotationFqn);
    }
    
    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        PsiFile psiFile = getPsiFile(e);
        
        boolean isEnabled = project != null && psiFile instanceof PsiJavaFile;
        e.getPresentation().setEnabledAndVisible(isEnabled);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    private PsiFile getPsiFile(AnActionEvent e) {
        PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
        if (file == null) {
            PsiElement element = e.getData(CommonDataKeys.PSI_ELEMENT);
            if (element instanceof PsiFile) {
                file = (PsiFile) element;
            } else if (element instanceof PsiClass) {
                file = element.getContainingFile();
            }
        }
        return file;
    }
}
