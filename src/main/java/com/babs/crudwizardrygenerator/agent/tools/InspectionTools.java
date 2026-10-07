package com.babs.crudwizardrygenerator.agent.tools;

import com.babs.crudwizardrygenerator.agent.AgentDtos;
import com.babs.crudwizardrygenerator.agent.AgentErrorCode;
import com.babs.crudwizardrygenerator.agent.AgentPermission;
import com.babs.crudwizardrygenerator.agent.AgentPsiSupport;
import com.babs.crudwizardrygenerator.agent.AgentTool;
import com.babs.crudwizardrygenerator.agent.AgentToolException;
import com.babs.crudwizardrygenerator.agent.ProjectPathGuard;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class InspectionTools {
    private static final Set<String> EXCLUDED_DIRECTORIES = Set.of(
            ".git", ".idea", ".gradle", ".intellijPlatform", ".kotlin", "build", "target", "out",
            "node_modules", ".cache", "dist", "generated"
    );

    private InspectionTools() {
    }

    public static final class GetProjectInfo implements AgentTool<AgentDtos.EmptyInput, AgentDtos.ProjectInfoResult> {
        public String name() { return "get_project_info"; }
        public String description() { return "Returns the open project's name, base path, modules, and source roots."; }
        public Class<AgentDtos.EmptyInput> inputType() { return AgentDtos.EmptyInput.class; }

        public AgentDtos.ProjectInfoResult execute(Project project, AgentDtos.EmptyInput input) {
            return ReadAction.compute(() -> {
                List<String> modules = new ArrayList<>();
                List<String> roots = new ArrayList<>();
                for (Module module : ModuleManager.getInstance(project).getModules()) {
                    modules.add(module.getName());
                    for (VirtualFile root : ModuleRootManager.getInstance(module).getSourceRoots()) {
                        roots.add(ProjectPathGuard.relative(project, root.getPath()));
                    }
                }
                return new AgentDtos.ProjectInfoResult(project.getName(), project.getBasePath(), modules, roots);
            });
        }
    }

    public static final class GetProjectStructure implements AgentTool<AgentDtos.ProjectStructureInput, AgentDtos.ProjectStructureResult> {
        public String name() { return "get_project_structure"; }
        public String description() { return "Returns a bounded project tree while excluding IDE, VCS, build, and dependency output."; }
        public Class<AgentDtos.ProjectStructureInput> inputType() { return AgentDtos.ProjectStructureInput.class; }
        public Set<AgentPermission> requiredPermissions() { return Set.of(AgentPermission.READ_PROJECT); }

        public AgentDtos.ProjectStructureResult execute(Project project, AgentDtos.ProjectStructureInput input) {
            int maxDepth = bounded(input == null ? null : input.maxDepth(), 1, 12, 5);
            int maxEntries = bounded(input == null ? null : input.maxEntries(), 1, 5000, 500);
            Path start = input == null || input.path() == null || input.path().isBlank()
                    ? ProjectPathGuard.projectRoot(project)
                    : ProjectPathGuard.resolveExisting(project, input.path());
            if (!Files.isDirectory(start)) {
                throw new AgentToolException(AgentErrorCode.INVALID_ARGUMENTS, "Structure path must be a directory.");
            }
            Counter counter = new Counter(maxEntries);
            AgentDtos.ProjectNode root = walk(project, start, 0, maxDepth, counter);
            List<String> warnings = counter.truncated ? List.of("Result was truncated by maxEntries or maxDepth.") : List.of();
            return new AgentDtos.ProjectStructureResult(root, counter.count, counter.truncated, warnings);
        }

        private AgentDtos.ProjectNode walk(Project project, Path path, int depth, int maxDepth, Counter counter) {
            counter.count++;
            if (depth >= maxDepth || counter.count >= counter.limit) {
                counter.truncated = depth >= maxDepth || counter.count >= counter.limit;
                return node(project, path, Files.isDirectory(path), List.of());
            }
            if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                return node(project, path, false, List.of());
            }
            List<AgentDtos.ProjectNode> children = new ArrayList<>();
            try (var stream = Files.list(path)) {
                List<Path> entries = stream
                        .filter(child -> !Files.isSymbolicLink(child))
                        .filter(child -> !EXCLUDED_DIRECTORIES.contains(child.getFileName().toString()))
                        .sorted(Comparator.comparing(child -> child.getFileName().toString().toLowerCase()))
                        .toList();
                for (Path child : entries) {
                    if (counter.count >= counter.limit) {
                        counter.truncated = true;
                        break;
                    }
                    children.add(walk(project, child, depth + 1, maxDepth, counter));
                }
            } catch (IOException ex) {
                throw new AgentToolException(AgentErrorCode.FILE_NOT_FOUND, "Cannot inspect project structure.", ex);
            }
            return node(project, path, true, children);
        }

        private AgentDtos.ProjectNode node(Project project, Path path, boolean directory, List<AgentDtos.ProjectNode> children) {
            String name = path.getFileName() == null ? project.getName() : path.getFileName().toString();
            return new AgentDtos.ProjectNode(name, ProjectPathGuard.relative(project, path.toString()), directory, children);
        }
    }

    public static final class FindClass implements AgentTool<AgentDtos.FindClassInput, AgentDtos.FindClassResult> {
        public String name() { return "find_class"; }
        public String description() { return "Finds Java classes by simple or qualified name using IntelliJ indices."; }
        public Class<AgentDtos.FindClassInput> inputType() { return AgentDtos.FindClassInput.class; }
        public boolean requiresIndex() { return true; }
        public Set<AgentPermission> requiredPermissions() { return Set.of(AgentPermission.SEARCH_CODE); }

        public AgentDtos.FindClassResult execute(Project project, AgentDtos.FindClassInput input) {
            if (input == null) throw invalidInput();
            return ReadAction.compute(() -> new AgentDtos.FindClassResult(
                    AgentPsiSupport.findClasses(project, input.name()).stream()
                            .map(psiClass -> AgentPsiSupport.summarize(project, psiClass)).toList()));
        }
    }

    public static final class GetClassDetails implements AgentTool<AgentDtos.ClassDetailsInput, AgentDtos.ClassDetailsResult> {
        public String name() { return "get_class_details"; }
        public String description() { return "Returns PSI-derived class annotations, inheritance, fields, and methods."; }
        public Class<AgentDtos.ClassDetailsInput> inputType() { return AgentDtos.ClassDetailsInput.class; }
        public boolean requiresIndex() { return true; }
        public Set<AgentPermission> requiredPermissions() { return Set.of(AgentPermission.READ_SOURCE, AgentPermission.SEARCH_CODE); }

        public AgentDtos.ClassDetailsResult execute(Project project, AgentDtos.ClassDetailsInput input) {
            if (input == null) throw invalidInput();
            return ReadAction.compute(() -> AgentPsiSupport.details(project,
                    AgentPsiSupport.requireClass(project, input.name())));
        }
    }

    public static final class FindMethod implements AgentTool<AgentDtos.FindMethodInput, AgentDtos.FindMethodResult> {
        public String name() { return "find_method"; }
        public String description() { return "Finds methods on a Java class using PSI, with optional parameter-type disambiguation."; }
        public Class<AgentDtos.FindMethodInput> inputType() { return AgentDtos.FindMethodInput.class; }
        public boolean requiresIndex() { return true; }
        public Set<AgentPermission> requiredPermissions() { return Set.of(AgentPermission.SEARCH_CODE); }

        public AgentDtos.FindMethodResult execute(Project project, AgentDtos.FindMethodInput input) {
            if (input == null) throw invalidInput();
            return ReadAction.compute(() -> {
                PsiClass psiClass = AgentPsiSupport.requireClass(project, input.className());
                return new AgentDtos.FindMethodResult(AgentPsiSupport.findMethods(psiClass, input.methodName(), input.parameterTypes())
                        .stream().map(AgentPsiSupport::methodDetails).toList());
            });
        }
    }

    public static final class FindUsages implements AgentTool<AgentDtos.FindUsagesInput, AgentDtos.FindUsagesResult> {
        public String name() { return "find_usages"; }
        public String description() { return "Finds PSI references to a class or one of its methods inside the project."; }
        public Class<AgentDtos.FindUsagesInput> inputType() { return AgentDtos.FindUsagesInput.class; }
        public boolean requiresIndex() { return true; }
        public Set<AgentPermission> requiredPermissions() { return Set.of(AgentPermission.SEARCH_CODE, AgentPermission.READ_SOURCE); }

        public AgentDtos.FindUsagesResult execute(Project project, AgentDtos.FindUsagesInput input) {
            if (input == null) throw invalidInput();
            int limit = bounded(input.maxResults(), 1, 500, 100);
            return ReadAction.compute(() -> {
                PsiClass psiClass = AgentPsiSupport.requireClass(project, input.className());
                PsiElement target = psiClass;
                if (input.methodName() != null && !input.methodName().isBlank()) {
                    target = AgentPsiSupport.requireMethod(psiClass, input.methodName(), null);
                }
                List<AgentDtos.UsageInfo> usages = new ArrayList<>();
                boolean truncated = false;
                for (PsiReference reference : ReferencesSearch.search(target, GlobalSearchScope.projectScope(project))) {
                    if (usages.size() >= limit) {
                        truncated = true;
                        break;
                    }
                    PsiElement element = reference.getElement();
                    PsiFile file = element.getContainingFile();
                    if (file == null || file.getVirtualFile() == null) continue;
                    var document = PsiDocumentManager.getInstance(project).getDocument(file);
                    int line = document == null ? -1 : document.getLineNumber(element.getTextOffset()) + 1;
                    String context = element.getParent() == null ? element.getText() : element.getParent().getText();
                    if (context.length() > 240) context = context.substring(0, 240);
                    usages.add(new AgentDtos.UsageInfo(ProjectPathGuard.relative(project, file.getVirtualFile().getPath()), line, context));
                }
                return new AgentDtos.FindUsagesResult(usages, truncated);
            });
        }
    }

    public static final class ReadFile implements AgentTool<AgentDtos.ReadFileInput, AgentDtos.ReadFileResult> {
        private static final int HARD_LIMIT = 1_048_576;
        public String name() { return "read_file"; }
        public String description() { return "Reads a bounded UTF-8 file only after resolving it inside the current project."; }
        public Class<AgentDtos.ReadFileInput> inputType() { return AgentDtos.ReadFileInput.class; }
        public Set<AgentPermission> requiredPermissions() { return Set.of(AgentPermission.READ_SOURCE); }

        public AgentDtos.ReadFileResult execute(Project project, AgentDtos.ReadFileInput input) {
            if (input == null) throw invalidInput();
            Path path = ProjectPathGuard.resolveExisting(project, input.path());
            if (!Files.isRegularFile(path)) {
                throw new AgentToolException(AgentErrorCode.FILE_NOT_FOUND, "Path is not a regular file.");
            }
            int limit = bounded(input.maxBytes(), 1, HARD_LIMIT, 256 * 1024);
            try {
                long size = Files.size(path);
                byte[] bytes;
                try (var stream = Files.newInputStream(path)) {
                    bytes = stream.readNBytes(limit + 1);
                }
                boolean truncated = bytes.length > limit || size > limit;
                int length = Math.min(bytes.length, limit);
                return new AgentDtos.ReadFileResult(ProjectPathGuard.relative(project, path.toString()),
                        new String(bytes, 0, length, StandardCharsets.UTF_8), length, truncated);
            } catch (IOException ex) {
                throw new AgentToolException(AgentErrorCode.FILE_NOT_FOUND, "Could not read project file.", ex);
            }
        }
    }

    public static final class GetProjectProblems implements AgentTool<AgentDtos.EmptyInput, AgentDtos.ProjectProblemsResult> {
        public String name() { return "get_project_problems"; }
        public String description() { return "Reports currently supported project diagnostics and explicit limitations."; }
        public Class<AgentDtos.EmptyInput> inputType() { return AgentDtos.EmptyInput.class; }

        public AgentDtos.ProjectProblemsResult execute(Project project, AgentDtos.EmptyInput input) {
            return new AgentDtos.ProjectProblemsResult(List.of(), List.of(
                    "The IntelliJ inspection UI does not expose a stable complete project-wide problem snapshot to this SDK layer.",
                    "This first version intentionally returns no fabricated diagnostics; Phase 2 can add scoped daemon or build-result adapters."
            ));
        }
    }

    public static final class GetGitDiff implements AgentTool<AgentDtos.EmptyInput, AgentDtos.GitDiffResult> {
        public String name() { return "get_git_diff"; }
        public String description() { return "Returns structured uncommitted changed-file information through IntelliJ VCS APIs."; }
        public Class<AgentDtos.EmptyInput> inputType() { return AgentDtos.EmptyInput.class; }
        public Set<AgentPermission> requiredPermissions() { return Set.of(AgentPermission.GIT_READ); }

        public AgentDtos.GitDiffResult execute(Project project, AgentDtos.EmptyInput input) {
            return ReadAction.compute(() -> {
                boolean available = ProjectLevelVcsManager.getInstance(project).getAllActiveVcss().length > 0;
                if (!available) return new AgentDtos.GitDiffResult(false, List.of(), List.of("No active VCS was detected."));
                List<AgentDtos.GitChange> changes = ChangeListManager.getInstance(project).getAllChanges().stream()
                        .map(change -> new AgentDtos.GitChange(
                                ProjectPathGuard.relative(project, ChangesUtil.getFilePath(change).getPath()),
                                change.getType().toString()))
                        .toList();
                return new AgentDtos.GitDiffResult(true, changes,
                        List.of("This first version returns changed-file metadata, not patch text."));
            });
        }
    }

    private static AgentToolException invalidInput() {
        return new AgentToolException(AgentErrorCode.INVALID_ARGUMENTS, "arguments must be an object.");
    }

    private static int bounded(Integer requested, int minimum, int maximum, int fallback) {
        if (requested == null) return fallback;
        if (requested < minimum || requested > maximum) {
            throw new AgentToolException(AgentErrorCode.INVALID_ARGUMENTS,
                    "Limit must be between " + minimum + " and " + maximum + ".");
        }
        return requested;
    }

    private static final class Counter {
        private final int limit;
        private int count;
        private boolean truncated;

        private Counter(int limit) {
            this.limit = limit;
        }
    }
}
