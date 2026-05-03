package com.babs.crudwizardrygenerator.actions;

import com.babs.crudwizardrygenerator.services.ControllerEndpointGenerator;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

public class GenerateControllerEndpointAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        PsiMethod method = getTargetMethod(e);
        if (project == null || method == null) {
            return;
        }

        new ControllerEndpointGenerator(project).generateFor(method);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        PsiMethod method = getTargetMethod(e);
        boolean visible = project != null && method != null && isServiceMethod(method);
        e.getPresentation().setEnabledAndVisible(visible);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    private PsiMethod getTargetMethod(AnActionEvent e) {
        Object element = e.getData(CommonDataKeys.PSI_ELEMENT);
        if (element instanceof PsiMethod method) {
            return method.isConstructor() ? null : method;
        }
        if (element instanceof com.intellij.psi.PsiElement psiElement) {
            PsiMethod method = PsiTreeUtil.getParentOfType(psiElement, PsiMethod.class, false);
            if (method != null && !method.isConstructor()) {
                return method;
            }
        }
        return null;
    }

    private boolean isServiceMethod(PsiMethod method) {
        PsiClass psiClass = method.getContainingClass();
        if (psiClass == null) {
            return false;
        }

        String className = psiClass.getName();
        String qualifiedName = psiClass.getQualifiedName();

        if (className != null && (className.endsWith("Service") || className.endsWith("ServiceImpl"))) {
            return true;
        }
        if (qualifiedName != null && (qualifiedName.contains(".service.") || qualifiedName.contains(".services."))) {
            return true;
        }
        return psiClass.hasAnnotation("org.springframework.stereotype.Service");
    }
}
