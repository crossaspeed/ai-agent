# AI Agent Operations Guide (AGENTS.md)

Welcome to the `ai-agent` repository. This document provides essential instructions, commands, and code style guidelines for AI coding agents operating in this project. Read this carefully before generating or modifying any code.

## 🛠️ Tech Stack & Environment
- **Language**: Java 17
- **Framework**: Spring Boot 3.2.6 (WebFlux, Data MongoDB)
- **Build Tool**: Maven
- **Database**: MongoDB
- **AI Integration**: LangChain4j (OpenAI models, Reactive `Flux` streams)
- **Documentation**: SpringDoc / Knife4j (Swagger 3.x)
- **Boilerplate Reduction**: Lombok

---

## 🚀 Build, Test, and Execution Commands

**Important**: Ensure you execute Maven commands in the project root directory (`D:\daima\ai-agent\ai-agent`).

### 📦 Building
- **Clean and Package (skip tests)**:
  ```bash
  mvn clean package -DskipTests
  ```
- **Compile classes only** (useful for syntax checks):
  ```bash
  mvn compile test-compile
  ```

### 🧪 Testing
We use **JUnit 5** and Spring Boot Test.
- **Run all tests**:
  ```bash
  mvn test
  ```
- **Run a single test class** (Crucial for isolated verification):
  ```bash
  mvn test -Dtest="ClassNameTest"
  # Example: mvn test -Dtest="XiaocControllerTest"
  ```
- **Run a single test method**:
  ```bash
  mvn test -Dtest="ClassNameTest#methodName"
  # Example: mvn test -Dtest="XiaocControllerTest#historyShouldReturnChatHistoryByMemoryId"
  ```

---

## 🏗️ Architecture & Project Structure

The codebase follows a standard Spring Boot layer structure:
- `com.it.ai.aiagent.controller`: REST API endpoints.
- `com.it.ai.aiagent.bean`: Data models, Entities, and DTOs (Request/Response objects).
- `com.it.ai.aiagent.store`: Persistence logic, MongoDB interactions, and Custom Memory Stores.
- `com.it.ai.aiagent.assistant`: LangChain4j `@AiService` declarative interfaces.
- `com.it.ai.aiagent.config`: Spring `@Configuration` and Bean definitions.

---

## ✍️ Code Style & Guidelines

### 1. Naming Conventions
- **Classes**: `PascalCase` (e.g., `XiaocController`, `ChatForm`).
- **Methods/Variables**: `camelCase` (e.g., `getMessages`, `mongoChatMemoryStore`).
- **REST Endpoints**: lowercase, hyphen-separated or standard camelCase, no trailing slashes (e.g., `/agent/chat`).

### 2. Lombok & Boilerplate
- **ALWAYS use Lombok** to reduce boilerplate.
- Use `@Data` for DTOs and Beans.
- Use `@AllArgsConstructor` and `@NoArgsConstructor` for entities needing instantiation.
- Do NOT manually write getters, setters, or `toString()` methods unless custom logic is strictly required.

### 3. API & Controller Standards
- Expose REST endpoints using `@RestController` and `@RequestMapping`.
- Differentiate methods clearly: `@GetMapping`, `@PostMapping`, etc.
- **Path Variables**: Always specify the name explicitly to prevent compilation metadata issues: `@PathVariable("memoryId") Long memoryId`.
- **Swagger/OpenAPI**: Decorate controllers with `@Tag(name = "...")` and methods with `@Operation(summary = "...")` to maintain API documentation.
- Stream responses (like AI typing) should return `Flux<String>`.

### 4. Database (MongoDB)
- Use Spring Data MongoDB.
- Map entities with `@Document("collection_name")`.
- Map the primary key with `@Id` (typically mapped to an `ObjectId`).
- Use `MongoTemplate` for dynamic or complex queries (`Query`, `Criteria`, `Update`), particularly for `upsert` operations.

### 5. AI Service Layer (LangChain4j)
- Interfaces define the LLM contract. Use `@AiService`.
- Wire explicitly if necessary (`wiringMode = AiServiceWiringMode.EXPLICIT`).
- Specify configurations like `chatModel`, `streamingChatModel`, and `chatMemoryProvider`.
- Use `@SystemMessage` (from file or string) and `@UserMessage` annotations to define prompt behavior.

### 6. Dependency Injection
- Use `@Autowired` for field or constructor injection (Field injection is prevalent in this codebase).
- Ensure components are registered as Spring beans (`@Component`, `@Service`, `@Configuration`).

### 7. Testing Practices
- **Integration Tests**: Use `@SpringBootTest` for testing full context.
- **Controller/Web Layer Tests**: Use `@WebMvcTest(YourController.class)` combined with `@MockBean` for dependencies to ensure fast execution without loading the full application context.
- Verify component interactions using Mockito's `when(...).thenReturn(...)` and `verify(...)`.
- Test assertions should ideally use `MockMvc` for HTTP endpoints.

### 8. Imports and Formatting
- Remove unused imports before finalizing a file.
- Group imports: Java standard, Spring, 3rd party (Langchain, Mongo), and local project imports.
- Maintain consistent indentation (4 spaces).

---
**Remember**: Your goal is to blend seamlessly into the existing repository. Match the exact patterns you see in neighboring files, do not introduce major refactors during bug fixes, and verify your logic by compiling or running the relevant isolated test!