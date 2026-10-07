package com.babs.crudwizardrygenerator.agent.tools;

import com.babs.crudwizardrygenerator.agent.AgentDtos;
import com.babs.crudwizardrygenerator.agent.AgentErrorCode;
import com.babs.crudwizardrygenerator.agent.AgentPermission;
import com.babs.crudwizardrygenerator.agent.AgentTool;
import com.babs.crudwizardrygenerator.agent.AgentToolException;
import com.babs.crudwizardrygenerator.agent.ProjectPathGuard;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A deliberately constrained mutation prototype: exact text replacements in existing project files.
 * Exact expected text acts as an optimistic lock and all paths are validated before the write command.
 */
public final class ApplyPatchTool implements AgentTool<AgentDtos.ApplyPatchInput, AgentDtos.ApplyPatchResult> {
    private static final int MAX_REPLACEMENTS = 20;
    private static final int MAX_FILE_BYTES = 1024 * 1024;
    private static final int MAX_TOTAL_BYTES = 4 * 1024 * 1024;

    public String name() { return "apply_patch"; }
    public String description() { return "Atomically applies bounded exact-text replacements to existing project files with undo support."; }
    public Class<AgentDtos.ApplyPatchInput> inputType() { return AgentDtos.ApplyPatchInput.class; }
    public boolean mutating() { return true; }
    public Set<AgentPermission> requiredPermissions() { return Set.of(AgentPermission.PROPOSE_CHANGES, AgentPermission.MODIFY_CODE); }

    public AgentDtos.ApplyPatchResult execute(Project project, AgentDtos.ApplyPatchInput input) {
        if (input == null || input.replacements() == null || input.replacements().isEmpty()) {
            throw new AgentToolException(AgentErrorCode.INVALID_ARGUMENTS, "replacements must contain at least one change.");
        }
        if (input.replacements().size() > MAX_REPLACEMENTS) {
            throw new AgentToolException(AgentErrorCode.INVALID_ARGUMENTS, "Too many replacements in one command.");
        }

        List<PreparedChange> prepared = prepare(project, input.replacements());
        AtomicReference<AgentDtos.ApplyPatchResult> result = new AtomicReference<>();
        try {
            WriteCommandAction.runWriteCommandAction(project, "SpringForge Agent apply_patch", null, () -> {
                List<PreparedChange> applied = new ArrayList<>();
                try {
                    for (PreparedChange change : prepared) {
                        applied.add(change);
                        change.document.setText(change.after);
                        PsiDocumentManager.getInstance(project).commitDocument(change.document);
                        if (change.reformatJava && change.virtualFile.getName().endsWith(".java")) {
                            PsiFile psiFile = PsiManager.getInstance(project).findFile(change.virtualFile);
                            if (psiFile != null) {
                                CodeStyleManager.getInstance(project).reformat(psiFile);
                                PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(change.document);
                            }
                        }
                    }
                    List<AgentDtos.FileChangeResult> details = prepared.stream()
                            .map(change -> new AgentDtos.FileChangeResult(change.relativePath, change.before,
                                    change.document.getText()))
                            .toList();
                    result.set(new AgentDtos.ApplyPatchResult(
                            prepared.stream().map(change -> change.relativePath).toList(), details));
                } catch (Throwable failure) {
                    for (PreparedChange change : applied) {
                        change.document.setText(change.before);
                        PsiDocumentManager.getInstance(project).commitDocument(change.document);
                    }
                    throw failure;
                }
            });
        } catch (Throwable ex) {
            throw new AgentToolException(AgentErrorCode.WRITE_FAILED, "No patch changes were retained.", ex);
        }
        return result.get();
    }

    private List<PreparedChange> prepare(Project project, List<AgentDtos.TextReplacement> replacements) {
        List<PreparedChange> prepared = new ArrayList<>();
        Set<Path> seen = new HashSet<>();
        long totalBytes = 0;
        for (AgentDtos.TextReplacement replacement : replacements) {
            if (replacement == null || replacement.path() == null || replacement.expectedText() == null
                    || replacement.replacementText() == null) {
                throw new AgentToolException(AgentErrorCode.INVALID_ARGUMENTS,
                        "Each replacement requires path, expectedText, and replacementText.");
            }
            Path path = ProjectPathGuard.resolveExisting(project, replacement.path());
            if (!Files.isRegularFile(path) || !seen.add(path)) {
                throw new AgentToolException(AgentErrorCode.INVALID_ARGUMENTS,
                        "Each target must be a unique existing regular file.");
            }
            try {
                long size = Files.size(path);
                if (size > MAX_FILE_BYTES) {
                    throw new AgentToolException(AgentErrorCode.INVALID_ARGUMENTS, "Patch target exceeds 1 MiB: " + replacement.path());
                }
                totalBytes += size;
                if (totalBytes > MAX_TOTAL_BYTES) {
                    throw new AgentToolException(AgentErrorCode.INVALID_ARGUMENTS, "Combined patch targets exceed 4 MiB.");
                }
            } catch (java.io.IOException ex) {
                throw new AgentToolException(AgentErrorCode.FILE_NOT_FOUND, "Cannot inspect patch target.", ex);
            }
            VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path);
            Document document = virtualFile == null ? null : FileDocumentManager.getInstance().getDocument(virtualFile);
            if (virtualFile == null || document == null) {
                throw new AgentToolException(AgentErrorCode.UNSUPPORTED_OPERATION,
                        "Patch target is not a text file: " + replacement.path());
            }
            String before = document.getText();
            int first = before.indexOf(replacement.expectedText());
            if (first < 0) {
                throw new AgentToolException(AgentErrorCode.WRITE_FAILED,
                        "Expected text was not found; the file may have changed: " + replacement.path());
            }
            if (before.indexOf(replacement.expectedText(), first + replacement.expectedText().length()) >= 0) {
                throw new AgentToolException(AgentErrorCode.INVALID_ARGUMENTS,
                        "Expected text is not unique in: " + replacement.path());
            }
            String after = before.substring(0, first) + replacement.replacementText()
                    + before.substring(first + replacement.expectedText().length());
            prepared.add(new PreparedChange(ProjectPathGuard.relative(project, path.toString()), virtualFile, document,
                    before, after, Boolean.TRUE.equals(replacement.reformatJava())));
        }
        return prepared;
    }

    private record PreparedChange(String relativePath, VirtualFile virtualFile, Document document,
                                  String before, String after, boolean reformatJava) {}
}
