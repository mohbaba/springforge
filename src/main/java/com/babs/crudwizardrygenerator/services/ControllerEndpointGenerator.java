package com.babs.crudwizardrygenerator.services;

import com.babs.crudwizardrygenerator.utils.PackageNamingUtils;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class ControllerEndpointGenerator {
    private static final String TITLE = "Generate Controller Endpoint";
    private static final String NOTIFICATION_GROUP = "SpringForge Notifications";

    private final Project project;

    public ControllerEndpointGenerator(Project project) {
        this.project = project;
    }

    public void generateFor(PsiMethod serviceMethod) {
        PsiClass serviceClass = serviceMethod.getContainingClass();
        if (serviceClass == null) {
            return;
        }

        PsiClass controllerContractClass = resolveControllerContractClass(serviceClass, serviceMethod);

        PsiClass controllerClass = findControllerClass(controllerContractClass);
        boolean shouldCreateController = false;
        if (controllerClass == null) {
            int result = Messages.showYesNoDialog(
                    project,
                    "No matching controller class was found for " + safeName(controllerContractClass.getName())
                            + ".\nDo you want to create one and add this endpoint?",
                    TITLE,
                    Messages.getQuestionIcon()
            );
            if (result != Messages.YES) {
                return;
            }
            shouldCreateController = true;
        }

        String serviceFieldName = shouldCreateController
                ? deriveServiceFieldName(controllerContractClass)
                : findServiceFieldName(controllerClass, controllerContractClass);
        if (!shouldCreateController && serviceFieldName == null) {
            Messages.showInfoMessage(
                    project,
                    "Found " + safeName(controllerClass.getName()) + ", but it does not appear to inject "
                            + safeName(controllerContractClass.getName()) + ".",
                    TITLE
            );
            return;
        }

        if (!shouldCreateController && controllerAlreadyCallsServiceMethod(controllerClass, serviceMethod, serviceFieldName)) {
            Messages.showInfoMessage(
                    project,
                    "A controller method already appears to call " + serviceMethod.getName() + "().",
                    TITLE
            );
            return;
        }

        EndpointSpec endpointSpec;
        try {
            endpointSpec = buildEndpointSpec(serviceMethod);
        } catch (UnsupportedOperationException ex) {
            Messages.showInfoMessage(project, ex.getMessage(), TITLE);
            return;
        }

        AtomicReference<String> successMessage = new AtomicReference<>();
        PsiClass finalControllerClass = controllerClass;
        boolean finalShouldCreateController = shouldCreateController;
        String methodText = buildMethodText(serviceMethod, serviceFieldName, endpointSpec);

        WriteCommandAction.runWriteCommandAction(project, () -> {
            PsiClass targetControllerClass = finalControllerClass;
            if (finalShouldCreateController) {
                targetControllerClass = createControllerClass(controllerContractClass);
                if (targetControllerClass == null) {
                    return;
                }
            }

            PsiFile controllerFile = targetControllerClass.getContainingFile();
            ensureEndpointImports(controllerFile, endpointSpec);

            PsiMethod generatedMethod = createControllerMethod(methodText);
            targetControllerClass.add(generatedMethod);

            CodeStyleManager.getInstance(project).reformat(controllerFile);
            JavaCodeStyleManager.getInstance(project).shortenClassReferences(controllerFile);

            successMessage.set(
                    "Controller endpoint generated in " + safeName(controllerClassName(targetControllerClass))
                            + " for " + serviceMethod.getName() + "()."
            );
        });

        if (successMessage.get() != null) {
            notifySuccess(successMessage.get());
        }
    }

    private PsiClass findControllerClass(PsiClass serviceClass) {
        String serviceName = serviceClass.getName();
        String qualifiedName = serviceClass.getQualifiedName();
        if (serviceName == null || qualifiedName == null) {
            return null;
        }

        String baseName = extractBaseName(serviceName);
        String controllerName = baseName + "Controller";
        String servicePackage = qualifiedName.substring(0, qualifiedName.lastIndexOf('.'));

        JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);

        for (String candidatePackage : candidateControllerPackages(servicePackage)) {
            PsiClass psiClass = psiFacade.findClass(candidatePackage + "." + controllerName, scope);
            if (psiClass != null) {
                return psiClass;
            }
        }

        PsiClass[] candidates = psiFacade.findClasses(controllerName, scope);
        if (candidates.length == 0) {
            return null;
        }

        String rootPackage = rootPackage(servicePackage);
        for (PsiClass candidate : candidates) {
            String candidateQName = candidate.getQualifiedName();
            if (candidateQName != null && candidateQName.startsWith(rootPackage)) {
                return candidate;
            }
        }

        return candidates[0];
    }

    private List<String> candidateControllerPackages(String servicePackage) {
        return PackageNamingUtils.candidateControllerPackagesFromService(servicePackage);
    }

    private String deriveControllerPackage(PsiClass serviceClass) {
        String qualifiedName = serviceClass.getQualifiedName();
        if (qualifiedName == null || !qualifiedName.contains(".")) {
            return "controller";
        }

        String servicePackage = qualifiedName.substring(0, qualifiedName.lastIndexOf('.'));
        List<String> packages = candidateControllerPackages(servicePackage);
        return packages.isEmpty() ? servicePackage + ".controller" : packages.get(0);
    }

    private String rootPackage(String packageName) {
        int serviceIndex = packageName.indexOf(".service");
        if (serviceIndex > 0) {
            return packageName.substring(0, serviceIndex);
        }
        return packageName;
    }

    private String findServiceFieldName(PsiClass controllerClass, PsiClass serviceClass) {
        Set<String> acceptableSimpleNames = new LinkedHashSet<>();
        Set<String> acceptableQualifiedNames = new LinkedHashSet<>();

        collectServiceTypeNames(serviceClass, acceptableSimpleNames, acceptableQualifiedNames);

        for (PsiField field : controllerClass.getAllFields()) {
            PsiType fieldType = field.getType();
            if (!(fieldType instanceof PsiClassType classType)) {
                continue;
            }

            PsiClass resolvedClass = classType.resolve();
            if (resolvedClass != null) {
                String fieldQName = resolvedClass.getQualifiedName();
                String fieldName = resolvedClass.getName();
                if ((fieldQName != null && acceptableQualifiedNames.contains(fieldQName))
                        || (fieldName != null && acceptableSimpleNames.contains(fieldName))) {
                    return field.getName();
                }
            }
        }

        String baseName = extractBaseName(safeName(serviceClass.getName()));
        String fallback = Character.toLowerCase(baseName.charAt(0)) + baseName.substring(1) + "Service";
        for (PsiField field : controllerClass.getAllFields()) {
            if (field.getName().equals(fallback)) {
                return field.getName();
            }
        }

        return null;
    }

    private String deriveServiceFieldName(PsiClass serviceClass) {
        String baseName = extractBaseName(safeName(serviceClass.getName()));
        return Character.toLowerCase(baseName.charAt(0)) + baseName.substring(1) + "Service";
    }

    private PsiClass preferredInjectedServiceClass(PsiClass serviceClass) {
        String className = serviceClass.getName();
        if (className != null && className.endsWith("ServiceImpl")) {
            for (PsiClass psiInterface : serviceClass.getInterfaces()) {
                String interfaceName = psiInterface.getName();
                if (interfaceName != null && interfaceName.endsWith("Service")) {
                    return psiInterface;
                }
            }
        }
        return serviceClass;
    }

    private PsiClass resolveControllerContractClass(PsiClass serviceClass, PsiMethod serviceMethod) {
        PsiClass preferred = preferredInjectedServiceClass(serviceClass);
        if (preferred == serviceClass) {
            return serviceClass;
        }

        for (PsiMethod candidate : preferred.getMethods()) {
            if (sameSignature(candidate, serviceMethod)) {
                return preferred;
            }
        }

        return serviceClass;
    }

    private PsiClass createControllerClass(PsiClass serviceClass) {
        String controllerPackage = deriveControllerPackage(serviceClass);
        String controllerName = extractBaseName(safeName(serviceClass.getName())) + "Controller";
        PsiClass injectedServiceClass = preferredInjectedServiceClass(serviceClass);
        String serviceType = injectedServiceClass.getQualifiedName();
        if (serviceType == null) {
            serviceType = safeName(injectedServiceClass.getName());
        }

        PsiDirectory targetDir = resolveTargetDirectory(serviceClass, controllerPackage);
        if (targetDir == null) {
            return null;
        }

        PsiFile existingFile = targetDir.findFile(controllerName + ".java");
        if (existingFile instanceof com.intellij.psi.PsiJavaFile javaFile) {
            PsiClass[] classes = javaFile.getClasses();
            return classes.length > 0 ? classes[0] : null;
        }

        String fieldName = deriveServiceFieldName(serviceClass);
        String content = buildControllerClassText(controllerPackage, controllerName, serviceType, fieldName, extractBaseName(safeName(serviceClass.getName())));

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText(controllerName + ".java", com.intellij.ide.highlighter.JavaFileType.INSTANCE, content);
        PsiFile createdFile = (PsiFile) targetDir.add(file);
        if (createdFile instanceof com.intellij.psi.PsiJavaFile javaFile) {
            PsiClass[] classes = javaFile.getClasses();
            return classes.length > 0 ? classes[0] : null;
        }
        return null;
    }

    private PsiDirectory resolveTargetDirectory(PsiClass serviceClass, String packageName) {
        PsiFile containingFile = serviceClass.getContainingFile();
        if (containingFile == null) {
            return null;
        }

        VirtualFile sourceRoot = ProjectFileIndex.getInstance(project).getSourceRootForFile(containingFile.getVirtualFile());
        if (sourceRoot == null) {
            return null;
        }

        PsiDirectory currentDir = PsiManager.getInstance(project).findDirectory(sourceRoot);
        if (currentDir == null) {
            return null;
        }

        if (packageName == null || packageName.isBlank()) {
            return currentDir;
        }

        String[] parts = packageName.split("\\.");
        for (String part : parts) {
            if (part.isEmpty()) continue;
            PsiDirectory subDir = currentDir.findSubdirectory(part);
            if (subDir == null) {
                subDir = currentDir.createSubdirectory(part);
            }
            currentDir = subDir;
        }

        return currentDir;
    }

    private String buildControllerClassText(
            String controllerPackage,
            String controllerName,
            String serviceType,
            String serviceFieldName,
            String baseName
    ) {
        StringBuilder sb = new StringBuilder();
        if (controllerPackage != null && !controllerPackage.isBlank()) {
            sb.append("package ").append(controllerPackage).append(";\n\n");
        }

        sb.append("import ").append(serviceType).append(";\n");
        sb.append("import lombok.RequiredArgsConstructor;\n");
        sb.append("import org.springframework.web.bind.annotation.RequestMapping;\n");
        sb.append("import org.springframework.web.bind.annotation.RestController;\n\n");
        sb.append("@RestController\n");
        sb.append("@RequestMapping(\"").append(defaultControllerBasePath(baseName)).append("\")\n");
        sb.append("@RequiredArgsConstructor\n");
        sb.append("public class ").append(controllerName).append(" {\n\n");
        sb.append("    private final ").append(simpleName(serviceType)).append(" ").append(serviceFieldName).append(";\n");
        sb.append("}\n");
        return sb.toString();
    }

    private String defaultControllerBasePath(String baseName) {
        if (baseName.endsWith("y") && baseName.length() > 1) {
            return "/" + Character.toLowerCase(baseName.charAt(0)) + baseName.substring(1, baseName.length() - 1) + "ies";
        }
        return "/" + Character.toLowerCase(baseName.charAt(0)) + baseName.substring(1) + "s";
    }

    private String simpleName(String qualifiedName) {
        int lastDot = qualifiedName.lastIndexOf('.');
        return lastDot >= 0 ? qualifiedName.substring(lastDot + 1) : qualifiedName;
    }

    private boolean sameSignature(PsiMethod left, PsiMethod right) {
        if (!left.getName().equals(right.getName())) {
            return false;
        }
        PsiParameter[] leftParams = left.getParameterList().getParameters();
        PsiParameter[] rightParams = right.getParameterList().getParameters();
        if (leftParams.length != rightParams.length) {
            return false;
        }
        for (int i = 0; i < leftParams.length; i++) {
            if (!leftParams[i].getType().equals(rightParams[i].getType())) {
                return false;
            }
        }
        return true;
    }

    private void collectServiceTypeNames(
            PsiClass serviceClass,
            Set<String> acceptableSimpleNames,
            Set<String> acceptableQualifiedNames
    ) {
        String className = serviceClass.getName();
        String qualifiedName = serviceClass.getQualifiedName();

        if (className != null) {
            acceptableSimpleNames.add(className);
            acceptableSimpleNames.add(extractBaseName(className) + "Service");
        }
        if (qualifiedName != null) {
            acceptableQualifiedNames.add(qualifiedName);
        }

        for (PsiClass psiInterface : serviceClass.getInterfaces()) {
            String interfaceName = psiInterface.getName();
            String interfaceQName = psiInterface.getQualifiedName();
            if (interfaceName != null) {
                acceptableSimpleNames.add(interfaceName);
            }
            if (interfaceQName != null) {
                acceptableQualifiedNames.add(interfaceQName);
            }
        }
    }

    private boolean controllerAlreadyCallsServiceMethod(PsiClass controllerClass, PsiMethod serviceMethod, String serviceFieldName) {
        String invocationText = serviceFieldName + "." + serviceMethod.getName() + "(";
        for (PsiMethod method : controllerClass.getMethods()) {
            PsiCodeBlock body = method.getBody();
            if (body != null && body.getText().contains(invocationText)) {
                return true;
            }
        }
        return false;
    }

    private EndpointSpec buildEndpointSpec(PsiMethod serviceMethod) {
        HttpMethod httpMethod = inferHttpMethod(serviceMethod.getName());
        List<ParameterSpec> parameters = new ArrayList<>();

        int complexParameterCount = 0;
        ParameterSpec requestBodyParameter = null;
        List<String> pathVariables = new ArrayList<>();

        for (PsiParameter parameter : serviceMethod.getParameterList().getParameters()) {
            ParameterBinding binding;
            boolean simpleType = isSimpleType(parameter.getType());

            if (!simpleType) {
                complexParameterCount++;
                if (complexParameterCount > 1) {
                    throw new UnsupportedOperationException(
                            "This action currently supports at most one complex parameter per service method."
                    );
                }
                binding = ParameterBinding.REQUEST_BODY;
            } else if (shouldUsePathVariable(httpMethod, parameter)) {
                binding = ParameterBinding.PATH_VARIABLE;
                pathVariables.add(parameter.getName());
            } else if (httpMethod == HttpMethod.GET || httpMethod == HttpMethod.DELETE) {
                binding = ParameterBinding.REQUEST_PARAM;
            } else {
                binding = ParameterBinding.REQUEST_PARAM;
            }

            ParameterSpec parameterSpec = new ParameterSpec(parameter, binding);
            if (binding == ParameterBinding.REQUEST_BODY) {
                requestBodyParameter = parameterSpec;
            }
            parameters.add(parameterSpec);
        }

        if ((httpMethod == HttpMethod.GET || httpMethod == HttpMethod.DELETE) && requestBodyParameter != null) {
            throw new UnsupportedOperationException(
                    "This action cannot safely generate GET or DELETE endpoints for methods that require a request body."
            );
        }

        String path = buildPath(serviceMethod.getName(), pathVariables);
        return new EndpointSpec(httpMethod, path, parameters);
    }

    private HttpMethod inferHttpMethod(String methodName) {
        String lowerName = methodName.toLowerCase(Locale.ROOT);
        if (startsWithAny(lowerName, "get", "find", "list", "search", "count", "exists")) {
            return HttpMethod.GET;
        }
        if (startsWithAny(lowerName, "delete", "remove")) {
            return HttpMethod.DELETE;
        }
        if (startsWithAny(lowerName, "update", "edit")) {
            return HttpMethod.PUT;
        }
        if (startsWithAny(lowerName, "patch")) {
            return HttpMethod.PATCH;
        }
        return HttpMethod.POST;
    }

    private String buildPath(String methodName, List<String> pathVariables) {
        String path = "/" + derivePathSegment(methodName);
        for (String pathVariable : pathVariables) {
            path += "/{" + pathVariable + "}";
        }
        return path;
    }

    private String derivePathSegment(String methodName) {
        String remainder = methodName;

        if (methodName.startsWith("findBy")) {
            remainder = "by" + methodName.substring("findBy".length());
        } else if (methodName.startsWith("getBy")) {
            remainder = "by" + methodName.substring("getBy".length());
        } else if (methodName.startsWith("deleteBy")) {
            remainder = "by" + methodName.substring("deleteBy".length());
        } else if (methodName.startsWith("removeBy")) {
            remainder = "by" + methodName.substring("removeBy".length());
        } else {
            for (String prefix : new String[]{"get", "find", "list", "search", "count", "exists", "create", "save", "add", "register", "update", "edit", "delete", "remove", "patch"}) {
                if (methodName.startsWith(prefix) && methodName.length() > prefix.length()) {
                    remainder = methodName.substring(prefix.length());
                    break;
                }
            }
        }

        String segment = toKebabCase(remainder);
        return segment.isBlank() ? toKebabCase(methodName) : segment;
    }

    private boolean shouldUsePathVariable(HttpMethod httpMethod, PsiParameter parameter) {
        if (!(httpMethod == HttpMethod.GET || httpMethod == HttpMethod.DELETE || httpMethod == HttpMethod.PUT || httpMethod == HttpMethod.PATCH)) {
            return false;
        }

        String name = parameter.getName().toLowerCase(Locale.ROOT);
        return name.equals("id") || name.endsWith("id");
    }

    private boolean isSimpleType(PsiType type) {
        if (type instanceof PsiPrimitiveType) {
            return true;
        }
        if (!(type instanceof PsiClassType classType)) {
            return false;
        }

        PsiClass psiClass = classType.resolve();
        if (psiClass == null) {
            return false;
        }

        if (psiClass.isEnum()) {
            return true;
        }

        String qualifiedName = psiClass.getQualifiedName();
        return "java.lang.String".equals(qualifiedName)
                || "java.lang.Integer".equals(qualifiedName)
                || "java.lang.Long".equals(qualifiedName)
                || "java.lang.Double".equals(qualifiedName)
                || "java.lang.Float".equals(qualifiedName)
                || "java.lang.Short".equals(qualifiedName)
                || "java.lang.Byte".equals(qualifiedName)
                || "java.lang.Boolean".equals(qualifiedName)
                || "java.lang.Character".equals(qualifiedName)
                || "java.math.BigDecimal".equals(qualifiedName)
                || "java.math.BigInteger".equals(qualifiedName)
                || "java.time.LocalDate".equals(qualifiedName)
                || "java.time.LocalDateTime".equals(qualifiedName)
                || "java.time.LocalTime".equals(qualifiedName)
                || "java.util.UUID".equals(qualifiedName);
    }

    private String buildMethodText(PsiMethod serviceMethod, String serviceFieldName, EndpointSpec endpointSpec) {
        String returnTypeText = buildControllerReturnType(serviceMethod.getReturnType());
        StringBuilder methodText = new StringBuilder();

        methodText.append("@")
                .append(endpointSpec.httpMethod.annotationClass)
                .append("(\"")
                .append(endpointSpec.path)
                .append("\")\n");
        methodText.append("public ")
                .append(returnTypeText)
                .append(" ")
                .append(serviceMethod.getName())
                .append("(")
                .append(buildParameterText(endpointSpec.parameters))
                .append(") {\n");

        String invocation = serviceFieldName + "." + serviceMethod.getName() + "(" + buildInvocationArgs(endpointSpec.parameters) + ")";
        PsiType returnType = serviceMethod.getReturnType();
        if (returnType == null || PsiType.VOID.equals(returnType)) {
            methodText.append("    ").append(invocation).append(";\n");
            methodText.append("    return ResponseEntity.noContent().build();\n");
        } else {
            methodText.append("    return ResponseEntity.ok(")
                    .append(invocation)
                    .append(");\n");
        }
        methodText.append("}\n");

        return methodText.toString();
    }

    private PsiMethod createControllerMethod(String methodText) {
        String dummyClassText = "class Dummy {\n" + methodText + "}\n";
        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText("Dummy.java", com.intellij.ide.highlighter.JavaFileType.INSTANCE, dummyClassText);
        if (file instanceof PsiJavaFile javaFile) {
            PsiClass[] classes = javaFile.getClasses();
            if (classes.length > 0) {
                PsiMethod[] methods = classes[0].getMethods();
                if (methods.length > 0) {
                    return methods[0];
                }
            }
        }
        throw new IllegalStateException("Could not create controller method from generated text.");
    }

    private String buildControllerReturnType(PsiType returnType) {
        if (returnType == null || PsiType.VOID.equals(returnType)) {
            return "ResponseEntity<Void>";
        }
        return "ResponseEntity<" + returnType.getCanonicalText() + ">";
    }

    private String buildParameterText(List<ParameterSpec> parameters) {
        List<String> parameterTexts = new ArrayList<>();
        for (ParameterSpec parameter : parameters) {
            StringBuilder text = new StringBuilder();
            if (parameter.binding == ParameterBinding.PATH_VARIABLE) {
                text.append("@PathVariable ");
            } else if (parameter.binding == ParameterBinding.REQUEST_PARAM) {
                text.append("@RequestParam ");
            } else if (parameter.binding == ParameterBinding.REQUEST_BODY) {
                text.append("@RequestBody ");
            }

            text.append(parameter.parameter.getType().getCanonicalText())
                    .append(" ")
                    .append(parameter.parameter.getName());
            parameterTexts.add(text.toString());
        }
        return String.join(", ", parameterTexts);
    }

    private String buildInvocationArgs(List<ParameterSpec> parameters) {
        List<String> args = new ArrayList<>();
        for (ParameterSpec parameter : parameters) {
            args.add(parameter.parameter.getName());
        }
        return String.join(", ", args);
    }

    private void ensureEndpointImports(PsiFile controllerFile, EndpointSpec endpointSpec) {
        if (!(controllerFile instanceof com.intellij.psi.PsiJavaFile javaFile)) {
            return;
        }

        ensureImport(javaFile, "org.springframework.http.ResponseEntity");
        ensureImport(javaFile, endpointSpec.httpMethod.importClass);

        for (ParameterSpec parameter : endpointSpec.parameters) {
            if (parameter.binding == ParameterBinding.PATH_VARIABLE) {
                ensureImport(javaFile, "org.springframework.web.bind.annotation.PathVariable");
            } else if (parameter.binding == ParameterBinding.REQUEST_PARAM) {
                ensureImport(javaFile, "org.springframework.web.bind.annotation.RequestParam");
            } else if (parameter.binding == ParameterBinding.REQUEST_BODY) {
                ensureImport(javaFile, "org.springframework.web.bind.annotation.RequestBody");
            }
        }
    }

    private void ensureImport(com.intellij.psi.PsiJavaFile javaFile, String qualifiedName) {
        PsiImportList importList = javaFile.getImportList();
        if (importList == null || importList.findSingleClassImportStatement(qualifiedName) != null) {
            return;
        }

        PsiClass importClass = JavaPsiFacade.getInstance(project).findClass(qualifiedName, GlobalSearchScope.allScope(project));
        if (importClass == null) {
            return;
        }

        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        importList.add(factory.createImportStatement(importClass));
    }

    private String extractBaseName(String className) {
        if (className.endsWith("ServiceImpl")) {
            return className.substring(0, className.length() - "ServiceImpl".length());
        }
        if (className.endsWith("Service")) {
            return className.substring(0, className.length() - "Service".length());
        }
        return className;
    }

    private String toKebabCase(String value) {
        if (value == null || value.isBlank()) {
            return "custom-action";
        }

        StringBuilder kebab = new StringBuilder();
        char[] chars = value.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char current = chars[i];
            if (Character.isUpperCase(current) && i > 0) {
                kebab.append('-');
            }
            kebab.append(Character.toLowerCase(current));
        }
        return kebab.toString();
    }

    private boolean startsWithAny(String value, String... prefixes) {
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private String safeName(String value) {
        return value == null || value.isBlank() ? "the selected class" : value;
    }

    private String controllerClassName(PsiClass psiClass) {
        return psiClass != null ? psiClass.getName() : null;
    }

    private void notifySuccess(String message) {
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                NotificationGroupManager.getInstance()
                        .getNotificationGroup(NOTIFICATION_GROUP)
                        .createNotification(message, NotificationType.INFORMATION)
                        .notify(project);
            } catch (Throwable ignored) {
                // Fall back to a visible IDE dialog if the notification channel is unavailable.
            }

            Messages.showInfoMessage(project, message, TITLE);
        });
    }

    private enum HttpMethod {
        GET("GetMapping", "org.springframework.web.bind.annotation.GetMapping"),
        POST("PostMapping", "org.springframework.web.bind.annotation.PostMapping"),
        PUT("PutMapping", "org.springframework.web.bind.annotation.PutMapping"),
        PATCH("PatchMapping", "org.springframework.web.bind.annotation.PatchMapping"),
        DELETE("DeleteMapping", "org.springframework.web.bind.annotation.DeleteMapping");

        private final String annotationClass;
        private final String importClass;

        HttpMethod(String annotationClass, String importClass) {
            this.annotationClass = annotationClass;
            this.importClass = importClass;
        }
    }

    private enum ParameterBinding {
        PATH_VARIABLE,
        REQUEST_PARAM,
        REQUEST_BODY
    }

    private record ParameterSpec(PsiParameter parameter, ParameterBinding binding) {}

    private record EndpointSpec(HttpMethod httpMethod, String path, List<ParameterSpec> parameters) {}
}
