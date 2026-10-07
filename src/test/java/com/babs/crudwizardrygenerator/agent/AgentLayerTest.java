package com.babs.crudwizardrygenerator.agent;

import com.google.gson.JsonObject;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

public class AgentLayerTest extends BasePlatformTestCase {
    private AgentCommandDispatcher dispatcher;
    private AgentToolRegistry registry;
    private Path testProjectRoot;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        registry = new AgentToolRegistry();
        dispatcher = new AgentCommandDispatcher(registry);
        testProjectRoot = Path.of(getProject().getBasePath());
        Files.createDirectories(testProjectRoot);
    }

    public void testRegistryContainsStableInitialToolSet() {
        Set<String> names = registry.all().stream().map(AgentTool::name).collect(Collectors.toSet());
        assertTrue(names.contains("get_project_info"));
        assertTrue(names.contains("find_class"));
        assertTrue(names.contains("read_file"));
        assertTrue(names.contains("generate_entity"));
        assertTrue(names.contains("generate_crud"));
        assertTrue(names.contains("generate_controller_from_service_method"));
        assertTrue(names.contains("apply_patch"));
        assertEquals(13, names.size());
    }

    public void testUnknownToolIsStructuredFailure() {
        AgentCommandResult result = dispatch("does_not_exist", new JsonObject());
        assertFalse(result.success());
        assertEquals(AgentErrorCode.TOOL_NOT_FOUND, result.error().code());
    }

    public void testProjectInfoAndBoundedStructure() throws Exception {
        Files.createDirectories(testProjectRoot.resolve("src"));
        Files.writeString(testProjectRoot.resolve("src/Visible.java"), "class Visible {}");
        Files.createDirectories(testProjectRoot.resolve(".git"));
        Files.writeString(testProjectRoot.resolve(".git/hidden"), "secret metadata");

        AgentCommandResult info = dispatch("get_project_info", new JsonObject());
        assertTrue(info.success());
        assertEquals(getProject().getName(), info.result().getAsJsonObject().get("name").getAsString());

        JsonObject structureArgs = new JsonObject();
        structureArgs.addProperty("maxDepth", 4);
        structureArgs.addProperty("maxEntries", 100);
        AgentCommandResult structure = dispatch("get_project_structure", structureArgs);
        assertTrue(structure.success());
        String json = structure.result().toString();
        assertTrue(json.contains("Visible.java"));
        assertFalse(json.contains("hidden"));
    }

    public void testMalformedArgumentsAreStructuredFailure() {
        AgentCommandResult result = dispatch("find_class", new JsonObject());
        assertFalse(result.success());
        assertEquals(AgentErrorCode.INVALID_ARGUMENTS, result.error().code());
    }

    public void testFindClassAndClassDetailsUsePsi() {
        myFixture.addFileToProject("demo/UserService.java",
                "package demo; @Deprecated public class UserService { private String name; public Long find(Long id) { return id; } }");
        JsonObject find = new JsonObject();
        find.addProperty("name", "demo.UserService");
        AgentCommandResult found = dispatch("find_class", find);
        assertTrue(found.success());
        assertEquals("demo.UserService", found.result().getAsJsonObject().getAsJsonArray("matches")
                .get(0).getAsJsonObject().get("qualifiedName").getAsString());

        AgentCommandResult details = dispatch("get_class_details", find);
        assertTrue(details.success());
        assertEquals("name", details.result().getAsJsonObject().getAsJsonArray("fields")
                .get(0).getAsJsonObject().get("name").getAsString());

        JsonObject method = new JsonObject();
        method.addProperty("className", "demo.UserService");
        method.addProperty("methodName", "find");
        AgentCommandResult methods = dispatch("find_method", method);
        assertTrue(methods.success());
        assertEquals(1, methods.result().getAsJsonObject().getAsJsonArray("matches").size());
    }

    public void testReadFileRejectsOutsideProjectAndReadsInsideProject() throws Exception {
        Files.createDirectories(testProjectRoot.resolve("notes"));
        Files.writeString(testProjectRoot.resolve("notes/readme.txt"), "local only");
        JsonObject allowed = new JsonObject();
        allowed.addProperty("path", "notes/readme.txt");
        AgentCommandResult read = dispatch("read_file", allowed);
        assertTrue(read.success());
        assertEquals("local only", read.result().getAsJsonObject().get("content").getAsString());

        Path outside = Files.createTempFile("springforge-agent-outside", ".txt");
        try {
            JsonObject denied = new JsonObject();
            denied.addProperty("path", outside.toString());
            AgentCommandResult result = dispatch("read_file", denied);
            assertFalse(result.success());
            assertEquals(AgentErrorCode.PATH_OUTSIDE_PROJECT, result.error().code());
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    public void testApplyPatchValidatesAndModifiesProjectText() throws Exception {
        Files.createDirectories(testProjectRoot.resolve("src"));
        Files.writeString(testProjectRoot.resolve("src/Sample.java"),
                "class Sample { String value = \"before\"; }");
        JsonObject replacement = new JsonObject();
        replacement.addProperty("path", "src/Sample.java");
        replacement.addProperty("expectedText", "\"before\"");
        replacement.addProperty("replacementText", "\"after\"");
        JsonObject arguments = new JsonObject();
        var replacements = new com.google.gson.JsonArray();
        replacements.add(replacement);
        arguments.add("replacements", replacements);

        AgentCommandResult result = dispatch("apply_patch", arguments);
        assertTrue(result.success());
        assertEquals("class Sample { String value = \"after\"; }",
                result.result().getAsJsonObject().getAsJsonArray("changes").get(0)
                        .getAsJsonObject().get("after").getAsString());
    }

    public void testGenerateCrudInvokesSharedGenerator() {
        myFixture.addFileToProject("demo/entity/User.java",
                "package demo.entity; public class User { private Long id; }");
        JsonObject arguments = new JsonObject();
        arguments.addProperty("entityQualifiedName", "demo.entity.User");
        arguments.addProperty("controller", false);
        arguments.addProperty("service", false);
        arguments.addProperty("repository", true);
        arguments.addProperty("dto", false);
        arguments.addProperty("overwrite", false);

        AgentCommandResult result = dispatch("generate_crud", arguments);
        assertTrue(result.success());
        assertNotNull(myFixture.findFileInTempDir("demo/repository/UserRepository.java"));
        assertTrue(result.result().getAsJsonObject().getAsJsonArray("createdFiles").size() > 0);
    }

    public void testGenerateControllerInvokesSharedGenerator() {
        myFixture.addFileToProject("demo/service/UserService.java",
                "package demo.service; public class UserService { public void deleteById(long id) { } }");
        myFixture.addFileToProject("demo/controller/UserController.java",
                "package demo.controller; import demo.service.UserService; public class UserController { private UserService userService; }");
        JsonObject arguments = new JsonObject();
        arguments.addProperty("serviceClass", "demo.service.UserService");
        arguments.addProperty("methodName", "deleteById");
        arguments.addProperty("createController", true);

        AgentCommandResult result = dispatch("generate_controller_from_service_method", arguments);
        assertTrue(result.error() == null ? result.toString() : result.error().toString(), result.success());
        var generation = result.result().getAsJsonObject();
        assertTrue(generation.toString(), generation.get("success").getAsBoolean());
        assertTrue(generation.getAsJsonArray("createdFiles").size()
                + generation.getAsJsonArray("modifiedFiles").size() > 0);
    }

    public void testApplyPatchRejectsStaleExpectedText() throws Exception {
        Files.writeString(testProjectRoot.resolve("stale.txt"), "current");
        JsonObject replacement = new JsonObject();
        replacement.addProperty("path", "stale.txt");
        replacement.addProperty("expectedText", "old");
        replacement.addProperty("replacementText", "new");
        var replacements = new com.google.gson.JsonArray();
        replacements.add(replacement);
        JsonObject arguments = new JsonObject();
        arguments.add("replacements", replacements);
        AgentCommandResult result = dispatch("apply_patch", arguments);
        assertFalse(result.success());
        assertEquals(AgentErrorCode.WRITE_FAILED, result.error().code());
    }

    private AgentCommandResult dispatch(String tool, JsonObject arguments) {
        return dispatcher.dispatch(getProject(), new AgentCommand("test", tool, arguments));
    }
}
