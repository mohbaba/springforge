# SpringForge Agent Architecture

## Scope

This phase makes SpringForge's existing IntelliJ functionality callable through a local, typed command layer. It does **not** implement HTTP, WebSocket, MCP, ChatGPT, shell execution, builds, tests, dependency installation, or any remote connection.

## Before this change

SpringForge was a single IntelliJ plugin module targeting IntelliJ IDEA Community 2025.1 (build 251) and Java 21. `EntityGenerator` rendered and wrote entities, repositories, services, controllers, and DTOs. `ControllerEndpointGenerator` inspected a selected service method and modified or created a controller. Actions assembled PSI inputs directly, both generators showed UI dialogs themselves, the registered `EntityGeneratorService` was only a stub, and there were no tests.

That shape coupled orchestration, UI interaction, PSI, and writes closely enough that a non-UI caller could not safely reuse the generators.

## Current architecture

```text
IntelliJ UI actions
        |
        v
Application services ----------------- Agent tools
        |                                  |
        v                                  v
Existing generators / IntelliJ PSI   AgentCommandDispatcher
```

`EntityGenerationService`, `CrudGenerationService`, and `ControllerGenerationService` are the shared application boundary. UI actions use `IdeGenerationInteraction`; agent tools use an explicit headless policy. Both call the same generator implementation.

`AgentTool<I,O>` defines a stable name, description, input class, output type, permission metadata, index requirement, mutation flag, and execution method. `AgentToolRegistry` discovers tools by name. `AgentCommandDispatcher` deserializes `arguments`, rejects unknown/index-blocked calls, invokes the typed tool, and converts failures to safe `AgentErrorCode` values. Detailed exceptions go only to the IDE log.

The local developer harness is available at **Tools > SpringForge: Run Local Agent Command**. It accepts an `AgentCommand` JSON object and displays the structured result. This proves the command boundary independently of networking.

## Tool catalog and examples

Every request may include an optional `id`. `arguments` is always an object.

### `get_project_info`

```json
{"id":"1","tool":"get_project_info","arguments":{}}
```

### `get_project_structure`

```json
{"tool":"get_project_structure","arguments":{"path":"src","maxDepth":4,"maxEntries":500}}
```

The tree skips `.git`, `.idea`, build output, generated output, dependency directories, caches, and symlinks. Limits are mandatory internally even when omitted.

### `find_class`

```json
{"tool":"find_class","arguments":{"name":"com.example.UserService"}}
```

### `get_class_details`

```json
{"tool":"get_class_details","arguments":{"name":"com.example.UserService"}}
```

### `find_method`

```json
{"tool":"find_method","arguments":{"className":"com.example.UserService","methodName":"findById","parameterTypes":["java.lang.Long"]}}
```

### `find_usages`

```json
{"tool":"find_usages","arguments":{"className":"com.example.UserService","methodName":"findById","maxResults":100}}
```

### `read_file`

```json
{"tool":"read_file","arguments":{"path":"src/main/java/com/example/User.java","maxBytes":262144}}
```

Only real, existing files inside the project root are accepted. Traversal and symlink escapes are denied.

### `get_project_problems`

```json
{"tool":"get_project_problems","arguments":{}}
```

The 2025.1 SDK does not expose a stable complete project-wide inspection snapshot for this layer. The tool therefore returns an explicit limitation and no fabricated problems. A future scoped daemon/build adapter can extend it.

### `get_git_diff`

```json
{"tool":"get_git_diff","arguments":{}}
```

This phase uses IntelliJ VCS APIs and returns uncommitted changed-file metadata. It intentionally does not run Git or return patch text.

### `generate_entity`

```json
{"tool":"generate_entity","arguments":{"sourceRoot":"src/main/java","packageName":"com.example.domain","entityName":"User","fields":[{"name":"id","type":"Long"},{"name":"email","type":"String"}],"lombokData":true,"persistenceApi":"AUTO","overwrite":false}}
```

### `generate_crud`

```json
{"tool":"generate_crud","arguments":{"entityQualifiedName":"com.example.domain.User","controller":true,"service":true,"repository":true,"dto":true,"serviceInterface":false,"overwrite":false}}
```

### `generate_controller_from_service_method`

```json
{"tool":"generate_controller_from_service_method","arguments":{"serviceClass":"com.example.service.UserService","methodName":"findByEmail","parameterTypes":["java.lang.String"],"createController":true}}
```

### `apply_patch`

```json
{"tool":"apply_patch","arguments":{"replacements":[{"path":"src/main/java/com/example/User.java","expectedText":"private String oldName;","replacementText":"private String displayName;","reformatJava":true}]}}
```

This is intentionally not a general unified-diff engine. It performs bounded, exact, unique replacements in existing text files. Every target is validated before one IntelliJ write command, expected text is an optimistic lock, Java reformatting is optional, and the command is undoable. PSI semantic mutations (`create_class`, `add_method`, `add_field`, `add_annotation`, `rename_symbol`) should become separate tools rather than being simulated through text where PSI is safer.

## Security and threading

- No tool executes a shell, process, build, test, dependency install, Git push, or network request.
- Project paths are normalized, checked under the real project root, and resolved again to prevent traversal/symlink escape.
- Read sizes, structure depth/count, usage count, mutation count, and mutation file size are bounded.
- Mutation tools are marked separately and carry `PROPOSE_CHANGES` / `MODIFY_CODE`; read/search/VCS tools carry narrower permission metadata. Phase 2 must enforce granted permissions before dispatch.
- Index-backed tools declare that dependency and return `INDEX_NOT_READY` during dumb mode.
- PSI reads use IntelliJ read actions. Writes use `WriteCommandAction`, document synchronization, and Java formatting. Generator calls are marshalled to the EDT because the retained generators create write commands and UI actions already execute there.
- Normal responses never contain raw stack traces.

Source remains local unless a caller invokes a tool whose result explicitly returns source/context, such as `read_file`, `find_usages`, or patch before/after data. A future gateway should request only the smallest context required for a task.

## Phase 2 outline

```text
ChatGPT / other AI provider
        |
MCP server / ChatGPT app
        |
Authenticated cloud gateway
        |
outbound persistent WebSocket
        |
IntelliJ plugin
        |
AgentCommandDispatcher -> Agent tools -> services / PSI -> local project
```

Implement an `AgentTransport` outbound WebSocket adapter and let `RemoteSessionManager` own reconnect/lifecycle. Add device/session authentication, TLS certificate validation, request signatures/nonces, protocol versioning, command size/rate limits, cancellation/timeouts, audit logs, and explicit per-project permission/approval UI. Enforce permission metadata before tool lookup reaches execution. Keep provider/MCP translation in the gateway, networking out of generation services, and provider details out of tools. Do not expose inbound ports on the developer machine.

Phase 2 should also add scoped diagnostics, textual VCS diffs if approved, semantic mutation tools, background progress/cancellation, and integration tests for the transport/permission boundary.
