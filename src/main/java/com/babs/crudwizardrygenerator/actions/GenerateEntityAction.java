package com.babs.crudwizardrygenerator.actions;

import com.babs.crudwizardrygenerator.dtos.EntityData;
import com.babs.crudwizardrygenerator.services.EntityGenerator;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiPackage;
import org.jetbrains.annotations.NotNull;
import ui.GenerateEntityDialog;

public class GenerateEntityAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {

        Project project = e.getProject();
        if (project == null) return;

        PsiDirectory clickedDirectory =
                e.getData(CommonDataKeys.PSI_ELEMENT) instanceof PsiDirectory dir ? dir : null;

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
}