package com.babs.crudwizardrygenerator.agent.tools;

import com.babs.crudwizardrygenerator.agent.AgentDtos;
import com.babs.crudwizardrygenerator.agent.AgentErrorCode;
import com.babs.crudwizardrygenerator.agent.AgentPermission;
import com.babs.crudwizardrygenerator.agent.AgentPsiSupport;
import com.babs.crudwizardrygenerator.agent.AgentTool;
import com.babs.crudwizardrygenerator.agent.AgentToolException;
import com.babs.crudwizardrygenerator.agent.ProjectPathGuard;
import com.babs.crudwizardrygenerator.dtos.EntityData;
import com.babs.crudwizardrygenerator.dtos.PersistenceApi;
import com.babs.crudwizardrygenerator.services.ControllerGenerationService;
import com.babs.crudwizardrygenerator.services.CrudGenerationService;
import com.babs.crudwizardrygenerator.services.EntityGenerationService;
import com.babs.crudwizardrygenerator.services.GenerationInteraction;
import com.babs.crudwizardrygenerator.services.GenerationResult;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.util.IncorrectOperationException;
import dtos.FieldData;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public final class GenerationTools {
    private static final Pattern JAVA_IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
    private static final Pattern JAVA_TYPE = Pattern.compile(
            "[A-Za-z_$][A-Za-z0-9_$.]*(\\s*<\\s*[^;{}()]+\\s*>)?(\\s*\\[\\s*])*"
    );
    private GenerationTools() {}

    public static final class GenerateEntity implements AgentTool<AgentDtos.GenerateEntityInput, AgentDtos.GenerationResult> {
        public String name() { return "generate_entity"; }
        public String description() { return "Generates an entity through the same entity generation service used by the IDE action."; }
        public Class<AgentDtos.GenerateEntityInput> inputType() { return AgentDtos.GenerateEntityInput.class; }
        public boolean requiresIndex() { return true; }
        public boolean mutating() { return true; }
        public Set<AgentPermission> requiredPermissions() { return Set.of(AgentPermission.SEARCH_CODE, AgentPermission.MODIFY_CODE); }

        public AgentDtos.GenerationResult execute(Project project, AgentDtos.GenerateEntityInput input) {
            if (input == null) throw invalid("arguments are required.");
            validateIdentifier(input.entityName(), "entityName");
            validatePackage(input.packageName());
            if (input.fields() == null || input.fields().isEmpty()) throw invalid("fields must not be empty.");
            List<FieldData> fields = input.fields().stream().map(field -> {
                if (field == null) throw invalid("fields may not contain null entries.");
                validateIdentifier(field.name(), "field name");
                AgentPsiSupport.requireText(field.type(), "field type");
                if (!JAVA_TYPE.matcher(field.type()).matches()) {
                    throw invalid("field type contains unsupported Java type syntax: " + field.type());
                }
                return new FieldData(field.name(), field.type(), Boolean.TRUE.equals(field.nullable()));
            }).toList();
            PersistenceApi api;
            try {
                api = input.persistenceApi() == null ? PersistenceApi.AUTO
                        : PersistenceApi.valueOf(input.persistenceApi().trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw invalid("persistenceApi must be AUTO, JAKARTA, or JAVAX.");
            }
            EntityData data = new EntityData(input.entityName(), input.packageName(),
                    input.lombokData() == null || input.lombokData(), Boolean.TRUE.equals(input.lombokBuilder()), api,
                    fields, true, false, false, false, false, false);
            return onEdt(() -> {
                for (FieldData field : fields) {
                    try {
                        JavaPsiFacade.getElementFactory(project).createTypeFromText(field.getType(), null);
                    } catch (IncorrectOperationException ex) {
                        throw invalid("field type is not valid Java type syntax: " + field.getType());
                    }
                }
                PsiDirectory source = resolveSourceDirectory(project, input.sourceRoot());
                GenerationResult result = project.getService(EntityGenerationService.class).generate(project, data, source,
                        GenerationInteraction.headless(Boolean.TRUE.equals(input.overwrite()), false));
                return dto(result);
            });
        }
    }

    public static final class GenerateCrud implements AgentTool<AgentDtos.GenerateCrudInput, AgentDtos.GenerationResult> {
        public String name() { return "generate_crud"; }
        public String description() { return "Generates selected CRUD layers through the shared CRUD generation service."; }
        public Class<AgentDtos.GenerateCrudInput> inputType() { return AgentDtos.GenerateCrudInput.class; }
        public boolean requiresIndex() { return true; }
        public boolean mutating() { return true; }
        public Set<AgentPermission> requiredPermissions() { return Set.of(AgentPermission.SEARCH_CODE, AgentPermission.MODIFY_CODE); }

        public AgentDtos.GenerationResult execute(Project project, AgentDtos.GenerateCrudInput input) {
            if (input == null) throw invalid("arguments are required.");
            AgentPsiSupport.requireText(input.entityQualifiedName(), "entityQualifiedName");
            boolean controller = input.controller() == null || input.controller();
            boolean service = input.service() == null || input.service();
            boolean repository = input.repository() == null || input.repository();
            boolean dto = input.dto() == null || input.dto();
            if (!controller && !service && !repository && !dto) {
                throw invalid("At least one CRUD layer must be selected.");
            }
            return onEdt(() -> {
                PsiClass entity = AgentPsiSupport.requireClass(project, input.entityQualifiedName());
                CrudGenerationService.Options options = new CrudGenerationService.Options(
                        controller, service, repository, dto,
                        Boolean.TRUE.equals(input.serviceInterface()));
                return dto(project.getService(CrudGenerationService.class).generate(project, entity, options,
                        GenerationInteraction.headless(Boolean.TRUE.equals(input.overwrite()), false)));
            });
        }
    }

    public static final class GenerateController implements AgentTool<AgentDtos.GenerateControllerInput, AgentDtos.GenerationResult> {
        public String name() { return "generate_controller_from_service_method"; }
        public String description() { return "Generates a controller endpoint from a service method using the shared controller service."; }
        public Class<AgentDtos.GenerateControllerInput> inputType() { return AgentDtos.GenerateControllerInput.class; }
        public boolean requiresIndex() { return true; }
        public boolean mutating() { return true; }
        public Set<AgentPermission> requiredPermissions() { return Set.of(AgentPermission.SEARCH_CODE, AgentPermission.MODIFY_CODE); }

        public AgentDtos.GenerationResult execute(Project project, AgentDtos.GenerateControllerInput input) {
            if (input == null) throw invalid("arguments are required.");
            AgentPsiSupport.requireText(input.serviceClass(), "serviceClass");
            AgentPsiSupport.requireText(input.methodName(), "methodName");
            return onEdt(() -> {
                PsiClass serviceClass = AgentPsiSupport.requireClass(project, input.serviceClass());
                PsiMethod method = AgentPsiSupport.requireMethod(serviceClass, input.methodName(), input.parameterTypes());
                return dto(project.getService(ControllerGenerationService.class).generateFromServiceMethod(project, method,
                        GenerationInteraction.headless(false, Boolean.TRUE.equals(input.createController()))));
            });
        }
    }

    private static PsiDirectory resolveSourceDirectory(Project project, String requestedRoot) {
        VirtualFile root = null;
        if (requestedRoot != null && !requestedRoot.isBlank()) {
            Path path = ProjectPathGuard.resolveExisting(project, requestedRoot);
            root = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path);
            if (root == null || !ProjectFileIndex.getInstance(project).isInSourceContent(root)) {
                throw invalid("sourceRoot must identify a source directory in the current project.");
            }
        } else {
            for (Module module : ModuleManager.getInstance(project).getModules()) {
                VirtualFile[] roots = ModuleRootManager.getInstance(module).getSourceRoots();
                if (roots.length > 0) {
                    root = roots[0];
                    break;
                }
            }
        }
        PsiDirectory directory = root == null ? null : PsiManager.getInstance(project).findDirectory(root);
        if (directory == null) throw invalid("No project source root is available; provide sourceRoot.");
        return directory;
    }

    private static void validateIdentifier(String value, String field) {
        AgentPsiSupport.requireText(value, field);
        if (!JAVA_IDENTIFIER.matcher(value).matches()) throw invalid(field + " is not a valid Java identifier.");
    }

    private static void validatePackage(String packageName) {
        AgentPsiSupport.requireText(packageName, "packageName");
        for (String part : packageName.split("\\.")) validateIdentifier(part, "packageName");
    }

    private static AgentDtos.GenerationResult dto(GenerationResult result) {
        if (!result.success()) {
            String message = result.warnings().isEmpty() ? "Generation failed." : result.warnings().get(0);
            throw new AgentToolException(AgentErrorCode.GENERATION_FAILED, message);
        }
        return new AgentDtos.GenerationResult(result.success(), result.createdFiles(), result.modifiedFiles(), result.warnings());
    }

    private static AgentToolException invalid(String message) {
        return new AgentToolException(AgentErrorCode.INVALID_ARGUMENTS, message);
    }

    private static <T> T onEdt(java.util.concurrent.Callable<T> operation) {
        if (ApplicationManager.getApplication().isDispatchThread()) {
            try { return operation.call(); } catch (AgentToolException ex) { throw ex; }
            catch (Exception ex) { throw new AgentToolException(AgentErrorCode.GENERATION_FAILED, "Generation failed.", ex); }
        }
        AtomicReference<T> value = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        ApplicationManager.getApplication().invokeAndWait(() -> {
            try { value.set(operation.call()); } catch (Throwable ex) { failure.set(ex); }
        });
        if (failure.get() instanceof AgentToolException ex) throw ex;
        if (failure.get() != null) throw new AgentToolException(AgentErrorCode.GENERATION_FAILED, "Generation failed.", failure.get());
        return value.get();
    }
}
