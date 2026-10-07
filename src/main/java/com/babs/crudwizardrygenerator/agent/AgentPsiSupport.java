package com.babs.crudwizardrygenerator.agent;

import com.babs.crudwizardrygenerator.agent.AgentDtos.ClassDetailsResult;
import com.babs.crudwizardrygenerator.agent.AgentDtos.ClassSummary;
import com.babs.crudwizardrygenerator.agent.AgentDtos.FieldDetails;
import com.babs.crudwizardrygenerator.agent.AgentDtos.MethodDetails;
import com.babs.crudwizardrygenerator.agent.AgentDtos.ParameterDetails;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class AgentPsiSupport {
    private static final List<String> MODIFIERS = List.of(
            PsiModifier.PUBLIC, PsiModifier.PROTECTED, PsiModifier.PRIVATE, PsiModifier.STATIC,
            PsiModifier.ABSTRACT, PsiModifier.FINAL, PsiModifier.NATIVE, PsiModifier.SYNCHRONIZED,
            PsiModifier.TRANSIENT, PsiModifier.VOLATILE, PsiModifier.DEFAULT
    );

    private AgentPsiSupport() {
    }

    public static List<PsiClass> findClasses(Project project, String name) {
        requireText(name, "name");
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        if (name.contains(".")) {
            return Arrays.asList(JavaPsiFacade.getInstance(project).findClasses(name, scope));
        }
        return Arrays.asList(PsiShortNamesCache.getInstance(project).getClassesByName(name, scope));
    }

    public static PsiClass requireClass(Project project, String name) {
        List<PsiClass> classes = findClasses(project, name);
        if (classes.isEmpty()) {
            throw new AgentToolException(AgentErrorCode.CLASS_NOT_FOUND, "Class not found: " + name);
        }
        if (classes.size() > 1 && !name.contains(".")) {
            throw new AgentToolException(AgentErrorCode.INVALID_ARGUMENTS,
                    "Class name is ambiguous; use a qualified name: " + name);
        }
        return classes.get(0);
    }

    public static ClassSummary summarize(Project project, PsiClass psiClass) {
        VirtualFile file = psiClass.getContainingFile() == null ? null : psiClass.getContainingFile().getVirtualFile();
        String packageName = psiClass.getContainingFile() instanceof PsiJavaFile javaFile
                ? javaFile.getPackageName() : "";
        return new ClassSummary(
                nullToEmpty(psiClass.getName()),
                nullToEmpty(psiClass.getQualifiedName()),
                file == null ? "" : ProjectPathGuard.relative(project, file.getPath()),
                packageName,
                classType(psiClass)
        );
    }

    public static ClassDetailsResult details(Project project, PsiClass psiClass) {
        List<FieldDetails> fields = Arrays.stream(psiClass.getFields())
                .map(field -> new FieldDetails(field.getName(), field.getType().getCanonicalText(),
                        annotations(field.getModifierList()), modifiers(field.getModifierList())))
                .toList();
        List<MethodDetails> methods = Arrays.stream(psiClass.getMethods())
                .map(AgentPsiSupport::methodDetails)
                .toList();
        String superClass = psiClass.getSuperClass() == null ? null : psiClass.getSuperClass().getQualifiedName();
        List<String> interfaces = Arrays.stream(psiClass.getInterfaces())
                .map(PsiClass::getQualifiedName).map(AgentPsiSupport::nullToEmpty).toList();
        return new ClassDetailsResult(summarize(project, psiClass), annotations(psiClass.getModifierList()),
                superClass, interfaces, fields, methods);
    }

    public static MethodDetails methodDetails(PsiMethod method) {
        List<ParameterDetails> parameters = Arrays.stream(method.getParameterList().getParameters())
                .map(parameter -> new ParameterDetails(parameter.getName(), parameter.getType().getCanonicalText()))
                .toList();
        String containingClass = method.getContainingClass() == null ? "" :
                nullToEmpty(method.getContainingClass().getQualifiedName());
        return new MethodDetails(method.getName(), method.getReturnType() == null ? null :
                method.getReturnType().getCanonicalText(), parameters, annotations(method.getModifierList()),
                modifiers(method.getModifierList()), containingClass);
    }

    public static List<PsiMethod> findMethods(PsiClass psiClass, String methodName, List<String> parameterTypes) {
        requireText(methodName, "methodName");
        List<PsiMethod> matches = new ArrayList<>();
        for (PsiMethod method : psiClass.findMethodsByName(methodName, true)) {
            if (parameterTypes == null || parameterTypes.isEmpty() || parametersMatch(method, parameterTypes)) {
                matches.add(method);
            }
        }
        return matches;
    }

    public static PsiMethod requireMethod(PsiClass psiClass, String methodName, List<String> parameterTypes) {
        List<PsiMethod> matches = findMethods(psiClass, methodName, parameterTypes);
        if (matches.isEmpty()) {
            throw new AgentToolException(AgentErrorCode.METHOD_NOT_FOUND, "Method not found: " + methodName);
        }
        if (matches.size() > 1 && (parameterTypes == null || parameterTypes.isEmpty())) {
            throw new AgentToolException(AgentErrorCode.INVALID_ARGUMENTS,
                    "Method is overloaded; provide parameterTypes.");
        }
        return matches.get(0);
    }

    public static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new AgentToolException(AgentErrorCode.INVALID_ARGUMENTS, field + " is required.");
        }
    }

    private static boolean parametersMatch(PsiMethod method, List<String> requested) {
        PsiParameter[] parameters = method.getParameterList().getParameters();
        if (parameters.length != requested.size()) return false;
        for (int i = 0; i < parameters.length; i++) {
            String canonical = parameters[i].getType().getCanonicalText();
            String presentable = parameters[i].getType().getPresentableText();
            if (!requested.get(i).equals(canonical) && !requested.get(i).equals(presentable)) return false;
        }
        return true;
    }

    private static List<String> annotations(PsiModifierList modifierList) {
        if (modifierList == null) return List.of();
        return Arrays.stream(modifierList.getAnnotations())
                .map(PsiAnnotation::getQualifiedName).map(AgentPsiSupport::nullToEmpty).toList();
    }

    private static List<String> modifiers(PsiModifierList modifierList) {
        if (modifierList == null) return List.of();
        return MODIFIERS.stream().filter(modifierList::hasModifierProperty).toList();
    }

    private static String classType(PsiClass psiClass) {
        if (psiClass.isAnnotationType()) return "annotation";
        if (psiClass.isRecord()) return "record";
        if (psiClass.isEnum()) return "enum";
        if (psiClass.isInterface()) return "interface";
        return "class";
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
