# Testing rules

Read this before writing or changing any test. Full setup/requirements/troubleshooting
(Docker, test DB lifecycle, isolation details) are in `README.md` — this file is about
conventions, not setup.

## The Test / IT split is load-bearing

- `*Test.java` — unit tests, Mockito, no Spring context. Run by Surefire in the `test`
  phase: `./mvnw test`. None of this repo's current `*Test.java` classes open a Spring
  context, so none need Docker — but that's a fact about what these specific classes
  do, not a guarantee Surefire enforces: Surefire's actual default include patterns are
  `**/Test*.java`, `**/*Test.java`, `**/*Tests.java` and `**/*TestCase.java` (no
  explicit `<includes>`/`<excludes>` are configured in `pom.xml`), so don't assume a
  class matching one of the other three patterns would be silently skipped.
- `*IT.java` — integration tests, real Spring context, Testcontainers-backed Postgres
  (`TestcontainersConfiguration` + `@ServiceConnection`). Run by Failsafe in the
  `integration-test`/`verify` phases: `./mvnw verify`. Needs Docker running. Failsafe's
  default include patterns are `**/IT*.java`, `**/*IT.java` and `**/*ITCase.java`.
- A misnamed test class silently runs in neither suite. `./mvnw verify` is the exact
  command CI runs (`mvn --batch-mode clean verify` — CI invokes `mvn` directly, not the
  wrapper) and the real gate for "does this work" — `./mvnw test` alone is not
  sufficient to call a change done if it touches anything Spring-context-dependent.

## Naming

Follow the existing convention: `methodUnderTest_condition_expectedOutcome` (e.g.
`register_throwsIllegalArgument_whenEmailAlreadyExists`,
`searchBooks_withNoMatches_returnsEmptyContent`).

## Auth in integration tests

For the critical authentication/authorization journeys themselves (login, CSRF
rotation, session handling, `/api/admin/**` authorization) never use `@WithMockUser`
or any shortcut that skips the real filter chain or CSRF — mocking the principal there
would test nothing about the actual auth model. Drive these through the real flow with
the helpers in `com.alon.bookstore.support`:

- `CsrfTestSupport.fetchCsrfCookie` / `withCsrf` — get a real CSRF cookie, echo it as
  the header.
- `AuthTestClient` — register/login through the real endpoints, returns a `Session`
  (the `MockHttpSession` + the CSRF cookie to carry forward) that subsequent requests
  reuse via `.session(...)`. Admin fixtures must come from a trusted repository write
  (see `AuthenticationFlowIT.createAdminAndLogIn`), never from `register()`, which
  only ever creates a `USER`.

`@WithMockUser` is not blanket-forbidden elsewhere: a focused test whose subject is
business logic unrelated to authentication itself (e.g. a `@WebMvcTest` asserting a
controller's JSON shape) may use it, as long as it still goes through the real
security filter chain and CSRF rather than disabling them under test.

## Shared Testcontainers context

IT classes with an identical Spring context configuration (same annotations, same
`@Import`s) share one container and schema within a single `mvn` run — the same way
they'd share an `ApplicationContext`. Concretely: `BookSpecificationIT`,
`BookRepositoryIT`, `UserRepositoryIT`, and `FlywayMigrationIT` currently share one
group and rely on the Flyway-seeded 50-book / 2-role catalog; `AuthenticationFlowIT`
and `BookFilterApiIT` (which add `@AutoConfigureMockMvc`) are a separate group.

If a new or changed IT test inserts/deletes rows in a table another suite asserts
exact counts against, clean up in `@AfterEach` (see `BookRepositoryIT`,
`UserRepositoryIT`) — don't assume test isolation that the shared-context model
doesn't actually provide. A randomly generated email/ISBN per test (see
`AuthTestClient.uniqueEmail`) only avoids collisions *between* runs/tests; it does
nothing to stop a row from leaking into another suite's exact-count assertions, so it
is not a substitute for explicit cleanup.

## Coverage expectations for new work

- New service logic → a `*Test.java` with Mockito mocks for its dependencies (see
  `BookServiceTest`, `AuthServiceTest`).
- New repository query/constraint behavior, or anything that depends on the real
  schema/Postgres semantics → a `*IT.java` (see `BookRepositoryIT`,
  `BookSpecificationIT`).
- New or changed endpoint → extend the relevant `*ApiIT`/`*FlowIT` for the real
  request/response/security behavior, not just a unit test of the service it calls.
- New exception type reaching `GlobalExceptionHandler` → confirm which
  `@ExceptionHandler` actually catches it (it may already be covered by an existing
  one it's a subtype of, needing no new handler) and add a case in
  `GlobalExceptionHandlerTest` for whichever handler method ends up responsible (plain
  unit test, no Spring context — it instantiates the handler directly).
