# Bookstore

This project is a Spring Boot application with four runtime environments:

- local
- dev
- pp
- prod

## Environment configuration

The default local configuration lives in `src/main/resources/application.properties`.

Additional environment-specific settings are provided in:

- `src/main/resources/application-dev.properties`
- `src/main/resources/application-pp.properties`
- `src/main/resources/application-prod.properties`

The active profile is controlled by `spring.profiles.active`, with the default set to `local`:

```properties
spring.profiles.active=${APP_PROFILE:local}
```

You can override it with the `APP_PROFILE` environment variable or with Maven when starting the app.

## Run the app

### Local

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### Dev

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### PP

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=pp
```

### Prod

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

## Database configuration

The app uses PostgreSQL and reads the connection details from environment variables with sensible defaults:

```bash
DB_URL=jdbc:postgresql://localhost:5432/bookstore_db
DB_USERNAME=myuser
DB_PASSWORD=secret
```

Example:

```bash
DB_URL=jdbc:postgresql://localhost:5432/bookstore_db DB_USERNAME=myuser DB_PASSWORD=secret ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## CORS configuration

CORS origins are configurable per environment using `app.cors.allowed-origins`.

Example override:

```bash
CORS_ALLOWED_ORIGINS="http://localhost:3000,http://localhost:4200" ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Default values:

- local: `http://localhost:8080`
- dev: `http://localhost:8081`
- pp: `http://localhost:8082`
- prod: `http://localhost:8083`

## Testing

### Requirements

- Java 21
- Docker (running), for the Testcontainers-backed integration tests. No local PostgreSQL install is needed - a real `postgres:16-alpine` container (pinned in `TestcontainersConfiguration` to match the RDS engine version in `infra/variables.tf`, not `latest`) is started and torn down automatically per test run.

### Commands

Unit tests only (picked up by Surefire's default include patterns - `*Test.java` plus
`Test*.java`/`*Tests.java`/`*TestCase.java`, though this repo's convention sticks to
`*Test.java` - run in the `test` phase; none of these classes open a Spring context,
so no Docker is required):

```bash
./mvnw test
```

Everything, including the Testcontainers-backed integration tests (`*IT.java`, run by Failsafe in the `integration-test`/`verify` phases):

```bash
./mvnw verify
```

This is also the exact command CI runs (`.github/workflows/dev.yml`, `pp.yml`, `prod.yml`) in their `Build and test` step.

### Test database lifecycle and isolation

Each `@SpringBootTest` class that imports `TestcontainersConfiguration` starts (or reuses, via Spring's test context caching) a fresh `postgres:16-alpine` container (pinned to match `infra/variables.tf`'s `rds_engine_version`, not `latest`), against which Flyway runs every migration in `src/main/resources/db/migration` before the test class executes. There is no shared, pre-existing database - every run starts from a clean schema. Whether two test classes actually reuse the same container/schema instead of each starting a fresh one depends entirely on whether Spring's test context caching treats their contexts as identical (see the groupings below) - it is not guaranteed just by both importing `TestcontainersConfiguration`.

Test classes with an identical Spring context configuration (same annotations, same imports) share one container and one schema within a single `mvn` run, the same way they'd share one `ApplicationContext`. In practice this means:

- `BookSpecificationIT`, `BookRepositoryIT`, `UserRepositoryIT` and `FlywayMigrationIT` share a container and rely on the 50-book, 2-role catalog seeded by `V1.5__add_50_books.sql` and `V1.2__add_role_tbl.sql`. Tests that insert rows (e.g. `BookRepositoryIT`) clean up after themselves so they don't change those counts for the others.
- `AuthenticationFlowIT` and `BookFilterApiIT` additionally use `@AutoConfigureMockMvc`, which gives them a separate context (and container) from the group above. Every user they register uses a randomly generated email, so tests never collide with each other - note this avoids *collisions*, not *cleanup*: it works here specifically because nothing in this group asserts an exact total user count, unlike the book-catalog group above, where inserted rows are explicitly deleted in `@AfterEach` (see `BookRepositoryIT`/`UserRepositoryIT`) because the shared counts there do matter.

Integration tests exercise the real Spring Security filter chain (real login, real sessions, real cookie-based CSRF) rather than `@WithMockUser`; see `com.alon.bookstore.support.AuthTestClient` and `CsrfTestSupport` for the MockMvc helpers that carry a session and a matching CSRF token across requests the way a browser would.

### Troubleshooting

- **Integration tests hang or fail with a Docker connection error**: Docker Desktop (or your Docker daemon) isn't running. Start it and re-run.
- **`Testcontainers version mismatch` or image pull errors**: you're offline or your registry credentials/proxy block pulling `postgres:16-alpine`; pre-pull the image (`docker pull postgres:16-alpine`) or configure a mirror.
- **A `*IT.java` test isn't running at all**: Failsafe only picks up classes matching `**/IT*.java`, `**/*IT.java` or `**/*ITCase.java`; Surefire only picks up `**/Test*.java`, `**/*Test.java`, `**/*Tests.java` or `**/*TestCase.java` (this repo's convention is `*Test.java`/`*IT.java`, but those are the full default patterns, not the only ones Maven honors). A misnamed class silently runs in neither phase - check the `target/surefire-reports` and `target/failsafe-reports` directories to see what actually executed.
- **Flaky failures only when running the full suite, not a single class**: likely cross-class state leakage in the shared-container group described above (a test added/changed rows without cleaning up). Check `@AfterEach`/cleanup logic in the newly added test before changing assertions.
