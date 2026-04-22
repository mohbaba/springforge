package com.babs.crudwizardrygenerator.actions;

import com.babs.crudwizardrygenerator.dtos.EntityData;
import com.babs.crudwizardrygenerator.services.EntityGenerator;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import ui.GenerateEntityDialog;

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

        // Resolve package name from the clicked directory
        PsiPackage psiPackage = JavaDirectoryService.getInstance().getPackage(clickedDirectory);
        String currentPackageName = (psiPackage != null) ? psiPackage.getQualifiedName() : "";

        GenerateEntityDialog dialog = new GenerateEntityDialog(project, currentPackageName);
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
}