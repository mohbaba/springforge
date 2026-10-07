package com.babs.crudwizardrygenerator.actions;

import com.babs.crudwizardrygenerator.services.CrudGenerationService;
import com.babs.crudwizardrygenerator.services.IdeGenerationInteraction;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

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

        project.getService(CrudGenerationService.class).generate(project, psiClass,
                new CrudGenerationService.Options(isGenerateController(), isGenerateService(),
                        isGenerateRepository(), isGenerateDto(), isGenerateServiceInterface()),
                new IdeGenerationInteraction(project));
    }

    protected abstract boolean isGenerateController();
    protected abstract boolean isGenerateService();
    protected abstract boolean isGenerateRepository();
    protected abstract boolean isGenerateDto();
    
    protected boolean isGenerateServiceInterface() {
        return false;
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
