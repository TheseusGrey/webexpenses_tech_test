# AGENTS.md

## Stack

- Java 21, Spring Boot 4.1.0, Gradle 9.x
- Spring Security with OAuth2 Resource Server (JWT, RSA-256)
- JPA/Hibernate with PostgreSQL (prod) and H2 (tests)
- Lombok for entities, Java records for DTOs
- Jackson 3.x (`tools.jackson.databind`, not `com.fasterxml`)
- Podman Compose for local deployment

## Commands

```bash
just test          # Unit/integration tests (H2, no containers needed)
just e2e           # Spins up compose, runs E2E against live stack
just up            # Start compose stack (app + postgres)
just down          # Stop compose stack
just build         # Build jar, skip tests
./gradlew test     # Direct Gradle equivalent of `just test`
./gradlew e2eTest  # Direct Gradle equivalent (requires app already running)
```

## Source Sets

| Path | Purpose | Runs against |
|------|---------|--------------|
| `src/test/` | MockMvc integration tests | H2 in-memory, `@ActiveProfiles("test")` |
| `src/e2e/` | Black-box HTTP flow tests | Live compose stack on `localhost:8080` |

E2E tests use raw `java.net.http.HttpClient` — no Spring context, no DI.

## Spring Boot 4 Gotchas

- Jackson 3: imports are `tools.jackson.databind`, not `com.fasterxml.jackson.databind`
- `AutoConfigureMockMvc` moved to `org.springframework.boot.webmvc.test.autoconfigure`
- `JsonNode.asText()` is deprecated; use `.asString()`
- Jakarta namespace: `jakarta.validation`, `jakarta.annotation`
- Spring Security 7: `NimbusJwtEncoder.withKeyPair(pub, priv).build()` builder API

## Architecture

```
controller/   REST endpoints + GlobalExceptionHandler (@RestControllerAdvice)
service/      Business logic, throws ResponseStatusException for error responses
repository/   Spring Data JPA interfaces
entity/       JPA entities (Lombok @Builder, @Data)
dto/          Java records with validation annotations
config/       SecurityConfig (JWT, method security), DataSeeder
```

- Role enforcement uses `@RolesAllowed` (JSR-250, enabled via `@EnableMethodSecurity(jsr250Enabled = true)`)
- JWT custom claim `"role"` is mapped to `ROLE_` prefixed authority via `JwtAuthenticationConverter`
- RSA key pair is generated in-memory at startup (no external key file)
- `DataSeeder` (CommandLineRunner) idempotently inserts seed users with BCrypt passwords

## Testing Conventions

- Test class naming: `*ControllerTest` for MockMvc tests, `*FlowTest` for E2E
- Test method naming: `actionDescription_context_expectedResult`
- Auth helpers: `asEmployee()`, `asJane()`, `asApprover()` provide JWT RequestPostProcessors
- E2E tests are `@TestMethodOrder(OrderAnnotation.class)` with shared state across methods

## Database

- Schema: `src/main/resources/db/schema.sql` (reference only — Hibernate manages DDL via `ddl-auto: update`)
- Compose Postgres exposed on host port **5433** (not 5432) to avoid conflicts
- Tables: `users`, `expense_claims`, `claim_audit_events`

## Conventions

- No service interfaces — concrete classes only
- DTOs are records, entities are classes (Lombok)
- Errors surfaced via `ResponseStatusException` caught by `GlobalExceptionHandler`
- Query params for resource filtering (`?id=`, `?status=`), path params for identity (`/claims/{id}`)
