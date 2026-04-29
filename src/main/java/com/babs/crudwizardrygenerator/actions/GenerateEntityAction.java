package com.babs.crudwizardrygenerator.actions;

import com.babs.crudwizardrygenerator.dtos.EntityData;
import com.babs.crudwizardrygenerator.dtos.PersistenceApi;
import com.babs.crudwizardrygenerator.services.EntityGenerator;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import dtos.FieldData;
import org.jetbrains.annotations.NotNull;
import ui.GenerateEntityDialog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GenerateEntityAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        PsiDirectory clickedDirectory = getTargetDirectory(e);
        if (clickedDirectory == null) return;

        VirtualFile vf = clickedDirectory.getVirtualFile();
        ProjectFileIndex index = ProjectFileIndex.getInstance(project);

        if (!index.isInSource(vf)) {
            return;
        }

        PsiClass targetClass = getTargetClass(e);

        // Resolve package name from the clicked directory
        PsiPackage psiPackage = JavaDirectoryService.getInstance().getPackage(clickedDirectory);
        String currentPackageName = (psiPackage != null) ? psiPackage.getQualifiedName() : "";

        List<EntityData> initialEntities = targetClass != null
                ? Collections.singletonList(createPrefilledEntityData(targetClass, currentPackageName))
                : Collections.emptyList();

        GenerateEntityDialog dialog = new GenerateEntityDialog(project, currentPackageName, initialEntities);
        dialog.show();

        if (dialog.isOK()) {
            for (EntityData entity : dialog.toEntityDataList()) {
                new EntityGenerator(project, entity, clickedDirectory).generate();
            }
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        PsiDirectory dir = getTargetDirectory(e);
        
        boolean isEnabled = project != null && dir != null && isInsideSourceRoot(project, dir);
        e.getPresentation().setEnabledAndVisible(isEnabled);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    private PsiDirectory getTargetDirectory(AnActionEvent e) {
        Object element = e.getData(CommonDataKeys.PSI_ELEMENT);
        if (element instanceof PsiDirectory) {
            return (PsiDirectory) element;
        }
        
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        if (psiFile != null) {
            return psiFile.getContainingDirectory();
        }
        
        if (element instanceof PsiElement) {
            PsiFile file = ((PsiElement) element).getContainingFile();
            if (file != null) {
                return file.getContainingDirectory();
            }
        }
        return null;
    }

    private boolean isInsideSourceRoot(Project project, PsiDirectory dir) {
        VirtualFile vf = dir.getVirtualFile();
        return ProjectFileIndex.getInstance(project).isInSource(vf);
    }

    private PsiClass getTargetClass(AnActionEvent e) {
        PsiElement element = e.getData(CommonDataKeys.PSI_ELEMENT);
        if (element instanceof PsiClass psiClass) {
            return psiClass;
        }

        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        if (psiFile instanceof PsiJavaFile javaFile) {
            PsiClass[] classes = javaFile.getClasses();
            if (classes.length > 0) {
                return classes[0];
            }
        }

        return null;
    }

    private EntityData createPrefilledEntityData(PsiClass psiClass, String fallbackPackageName) {
        String entityName = psiClass.getName();
        if (entityName == null) {
            entityName = "NewEntity";
        }

        String packageName = fallbackPackageName;
        PsiFile containingFile = psiClass.getContainingFile();
        if (containingFile instanceof PsiJavaFile javaFile) {
            packageName = javaFile.getPackageName();
        }

        return new EntityData(
                entityName,
                packageName,
                psiClass.hasAnnotation("lombok.Data"),
                psiClass.hasAnnotation("lombok.Builder"),
                detectPersistenceApi(psiClass),
                extractDeclaredFields(psiClass),
                false,
                true,
                true,
                true,
                true,
                false
        );
    }

    private List<FieldData> extractDeclaredFields(PsiClass psiClass) {
        List<FieldData> fields = new ArrayList<>();
        for (PsiField field : psiClass.getFields()) {
            if (field.hasModifierProperty(PsiModifier.STATIC)) continue;
            fields.add(new FieldData(field.getName(), field.getType().getPresentableText(), false));
        }
        return fields;
    }

    private PersistenceApi detectPersistenceApi(PsiClass psiClass) {
        if (psiClass.hasAnnotation("javax.persistence.Entity")) {
            return PersistenceApi.JAVAX;
        }
        if (psiClass.hasAnnotation("jakarta.persistence.Entity")) {
            return PersistenceApi.JAKARTA;
        }
        return PersistenceApi.AUTO;
    }
}
