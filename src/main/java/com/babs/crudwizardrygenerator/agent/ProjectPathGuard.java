package com.babs.crudwizardrygenerator.agent;

import com.intellij.openapi.project.Project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Resolves caller-supplied paths without allowing traversal or symlink escapes. */
public final class ProjectPathGuard {
    private ProjectPathGuard() {
    }

    public static Path projectRoot(Project project) {
        String basePath = project.getBasePath();
        if (basePath == null || basePath.isBlank()) {
            throw new AgentToolException(AgentErrorCode.PROJECT_NOT_AVAILABLE, "The project has no local base path.");
        }
        try {
            return Path.of(basePath).toRealPath();
        } catch (IOException ex) {
            throw new AgentToolException(AgentErrorCode.PROJECT_NOT_AVAILABLE,
                    "The project base path cannot be resolved.", ex);
        }
    }

    public static Path resolveExisting(Project project, String untrustedPath) {
        if (untrustedPath == null || untrustedPath.isBlank()) {
            throw new AgentToolException(AgentErrorCode.INVALID_ARGUMENTS, "path is required.");
        }
        Path root = projectRoot(project);
        Path supplied = Path.of(untrustedPath);
        Path candidate = supplied.isAbsolute() ? supplied.normalize() : root.resolve(supplied).normalize();
        if (!candidate.startsWith(root)) {
            throw new AgentToolException(AgentErrorCode.PATH_OUTSIDE_PROJECT,
                    "Path is outside the current project.");
        }
        if (!Files.exists(candidate)) {
            throw new AgentToolException(AgentErrorCode.FILE_NOT_FOUND, "Project path was not found: " + untrustedPath);
        }
        try {
            Path realPath = candidate.toRealPath();
            if (!realPath.startsWith(root)) {
                throw new AgentToolException(AgentErrorCode.PATH_OUTSIDE_PROJECT,
                        "Path resolves outside the current project.");
            }
            return realPath;
        } catch (IOException ex) {
            throw new AgentToolException(AgentErrorCode.FILE_NOT_FOUND,
                    "Project path cannot be resolved: " + untrustedPath, ex);
        }
    }

    public static String relative(Project project, String absolutePath) {
        Path root = projectRoot(project);
        Path path = Path.of(absolutePath).toAbsolutePath().normalize();
        if (!path.startsWith(root)) {
            return path.getFileName() == null ? "" : path.getFileName().toString();
        }
        return root.relativize(path).toString().replace('\\', '/');
    }
}
