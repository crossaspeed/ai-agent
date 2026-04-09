# Project Guidelines

## Scope
- This root file is the default instruction set for the backend and shared repository workflows.
- Frontend-specific behavior is defined in [frontend/AGENTS.md](frontend/AGENTS.md) and takes precedence for files under frontend.

## Build And Test
- Run Maven commands from the repository root.
- Backend compile check:
  ```bash
  mvn compile test-compile
  ```
- Backend run locally:
  ```bash
  mvn spring-boot:run
  ```
- Backend package (skip tests):
  ```bash
  mvn clean package -DskipTests
  ```
- Backend tests:
  ```bash
  mvn test
  mvn test -Dtest="ClassNameTest"
  mvn test -Dtest="ClassNameTest#methodName"
  ```
- Frontend (from frontend directory):
  ```bash
  npm ci
  npm run dev
  npm run build
  npm run lint
  ```

## Architecture
- Backend stack: Java 17, Spring Boot 3.2.6, LangChain4j, MongoDB, MySQL.
- Main backend packages under src/main/java/com/it/ai/aiagent:
  - controller: REST endpoints.
  - service: business orchestration.
  - store: persistence and repositories.
  - assistant: LangChain4j `@AiService` interfaces.
  - config: Spring bean configuration.
  - bean: DTO/entity/view models.
- Frontend stack: Next.js 16 + React 19 in frontend.

## Conventions
- Naming:
  - Classes use PascalCase.
  - Methods and fields use camelCase.
  - REST paths are lowercase (hyphenated or camelCase), no trailing slash.
- Lombok:
  - Prefer Lombok for boilerplate.
  - Use `@Data` for DTO/bean types unless custom behavior is required.
- Controller and API:
  - Use `@RestController` and explicit mapping annotations.
  - Always use explicit path-variable names, for example `@PathVariable("memoryId") Long memoryId`.
  - Keep OpenAPI annotations (`@Tag`, `@Operation`) in controllers.
- Persistence:
  - Mongo entities use `@Document` and `@Id`.
  - Prefer `MongoTemplate` for dynamic queries and upsert flows.
- LangChain4j:
  - Keep `@AiService` wiring consistent with configured bean names (`chatModel`, `streamingChatModel`, `chatMemoryProvider`, optional retriever).
- Dependency injection:
  - Field injection with `@Autowired` is common in this repository; follow nearby file style.
- Testing:
  - Use `@WebMvcTest` + `@MockBean` for controller slices.
  - Use `@SpringBootTest` for integration tests.
  - Verify interactions with Mockito and assert HTTP behavior with MockMvc when applicable.
- Formatting:
  - Use 4-space indentation.
  - Remove unused imports and keep import groups clean.

## Common Pitfalls
- Missing `@PathVariable("...")` names can cause binding/metadata issues.
- SSE responses can stall behind Nginx buffering; keep streaming-related proxy settings aligned with deploy configs.
- Local runtime failures are often due to unavailable MongoDB/MySQL or missing environment variables.
- Do not commit real API keys or database passwords in `application.yml`; prefer environment variables and production config templates.
- If Lombok annotations are not recognized in IDE, verify annotation processing and confirm with Maven compile.

## Documentation Map (Link, Do Not Duplicate)
- Project overview and quick start: [README.md](README.md)
- Frontend agent defaults: [frontend/AGENTS.md](frontend/AGENTS.md)
- Contribution workflow: [CONTRIBUTING.md](CONTRIBUTING.md)
- Backend runtime and bean lifecycle details: [BACKEND_PROJECT_FLOW.md](BACKEND_PROJECT_FLOW.md)
- Deployment and HTTPS operations: [deploy/README.md](deploy/README.md)
- Feishu callback/long-connection trace: [docs/feishu-callback-reading-map.md](docs/feishu-callback-reading-map.md)
- Security reporting policy: [SECURITY.md](SECURITY.md)
- Release history: [CHANGELOG.md](CHANGELOG.md)

## Working Style
- Prefer minimal, targeted changes over broad refactors.
- Match existing patterns in neighboring files before introducing new abstractions.
- After code changes, run the smallest relevant compile/test command to validate behavior.