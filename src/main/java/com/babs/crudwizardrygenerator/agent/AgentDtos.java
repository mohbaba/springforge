package com.babs.crudwizardrygenerator.agent;

import java.util.List;

/** Stable structured inputs and outputs for the initial tool surface. */
public final class AgentDtos {
    private AgentDtos() {
    }

    public record EmptyInput() {}
    public record ProjectInfoResult(String name, String basePath, List<String> modules, List<String> sourceRoots) {}
    public record ProjectStructureInput(String path, Integer maxDepth, Integer maxEntries) {}
    public record ProjectNode(String name, String path, boolean directory, List<ProjectNode> children) {}
    public record ProjectStructureResult(ProjectNode root, int entries, boolean truncated, List<String> warnings) {}
    public record FindClassInput(String name) {}
    public record ClassSummary(String simpleName, String qualifiedName, String filePath, String packageName, String type) {}
    public record FindClassResult(List<ClassSummary> matches) {}
    public record ClassDetailsInput(String name) {}
    public record FieldDetails(String name, String type, List<String> annotations, List<String> modifiers) {}
    public record ParameterDetails(String name, String type) {}
    public record MethodDetails(String name, String returnType, List<ParameterDetails> parameters,
                                List<String> annotations, List<String> modifiers, String containingClass) {}
    public record ClassDetailsResult(ClassSummary classInfo, List<String> annotations, String superClass,
                                     List<String> interfaces, List<FieldDetails> fields, List<MethodDetails> methods) {}
    public record FindMethodInput(String className, String methodName, List<String> parameterTypes) {}
    public record FindMethodResult(List<MethodDetails> matches) {}
    public record FindUsagesInput(String className, String methodName, Integer maxResults) {}
    public record UsageInfo(String filePath, int line, String context) {}
    public record FindUsagesResult(List<UsageInfo> usages, boolean truncated) {}
    public record ReadFileInput(String path, Integer maxBytes) {}
    public record ReadFileResult(String path, String content, int bytes, boolean truncated) {}
    public record ProjectProblemsResult(List<String> problems, List<String> limitations) {}
    public record GitChange(String path, String status) {}
    public record GitDiffResult(boolean gitAvailable, List<GitChange> changes, List<String> limitations) {}

    public record EntityFieldInput(String name, String type, Boolean nullable) {}
    public record GenerateEntityInput(String sourceRoot, String packageName, String entityName,
                                      List<EntityFieldInput> fields, Boolean lombokData, Boolean lombokBuilder,
                                      String persistenceApi, Boolean overwrite) {}
    public record GenerateCrudInput(String entityQualifiedName, Boolean controller, Boolean service,
                                    Boolean repository, Boolean dto, Boolean serviceInterface, Boolean overwrite) {}
    public record GenerateControllerInput(String serviceClass, String methodName,
                                          List<String> parameterTypes, Boolean createController) {}
    public record GenerationResult(boolean success, List<String> createdFiles, List<String> modifiedFiles,
                                   List<String> warnings) {}

    public record TextReplacement(String path, String expectedText, String replacementText, Boolean reformatJava) {}
    public record ApplyPatchInput(List<TextReplacement> replacements) {}
    public record FileChangeResult(String path, String before, String after) {}
    public record ApplyPatchResult(List<String> modifiedFiles, List<FileChangeResult> changes) {}
}
