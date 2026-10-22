# Bookstore — Claude Code guide

Spring Boot 4 (Java 21) bookstore API. Package-by-feature: `book`, `order` (entities
exist; controller/service/repository layer is not built out yet — don't assume it's
wired up), `auth`, `user`, `shared` (cross-cutting config/exception/logging/model).
Four environments (local/dev/pp/prod) — see `README.md` for how to run/configure each
and for the full testing guide; don't duplicate that here.

## Architecture invariants

These are load-bearing conventions, not style preferences. Breaking them silently
breaks the app or CI:

- **Schema is Flyway-owned.** `spring.jpa.hibernate.ddl-auto=validate` — Hibernate
  never creates or alters tables. Any entity field/column change needs a new migration
  in `src/main/resources/db/migration` (`Vx.y__description.sql`). Never edit or renumber
  an existing migration — add a new one. See `.claude/rules/persistence.md`.
- **Controllers never return entities or Spring's `Page`.** Responses go through a
  MapStruct `*DtoOut` (or `PageResponse<T>` for paginated results); requests are
  validated `*Request` types. See `.claude/rules/persistence.md`.
- **Auth is session-based with cookie CSRF, not JWT.** `HttpSessionSecurityContextRepository`
  + `CookieCsrfTokenRepository` (`.spa()` mode). Don't introduce bearer tokens or a
  stateless filter unless explicitly asked — it would conflict with this model. Route
  authorization rules live centrally in `SecurityConfig`. See `.claude/rules/security.md`.
- **Errors are `ProblemDetail` via `GlobalExceptionHandler`.** `IllegalArgumentException`
  maps to 400 (invalid input) and dedicated exceptions for genuine conflicts (e.g.
  `EmailAlreadyExistsException`) map to 409 — invalid input is not automatically a
  conflict, so don't reintroduce a blanket `IllegalArgumentException` → 409 mapping. A
  new exception type a controller/service can throw needs *some* handler to reach (a
  deliberately generic client-facing message where it could leak internals) — that can
  be an existing mapping it already matches (e.g. a new exception that extends
  `IllegalArgumentException` needs no handler of its own), not necessarily a new
  `@ExceptionHandler`.
- **Tests are split by what they need.** `*Test.java` = Surefire, Mockito, no Spring
  context. `*IT.java` = Failsafe, Testcontainers Postgres, real Spring context —
  including the real security filter chain (no `@WithMockUser`). See
  `.claude/rules/testing.md`.
- **CI's `mvn --batch-mode clean verify` is the authoritative build.** The Dockerfile
  consumes a prebuilt jar; it does not run Maven. See `.claude/rules/infra-cicd.md`
  before touching the Dockerfile, workflows, or `infra/` (Terraform).

## Before working in a specific area, read the matching rules file

- Touching entities, repositories, migrations, mappers, pagination, or filters →
  `.claude/rules/persistence.md`
- Touching `SecurityConfig`, `auth/*`, CSRF, roles/authorization →
  `.claude/rules/security.md`
- Writing or changing any test → `.claude/rules/testing.md`
- Touching `Dockerfile`, `.github/workflows/*`, or `infra/` (Terraform) →
  `.claude/rules/infra-cicd.md`

## Working on any task here

1. Read the existing feature slice (controller/service/repository/entity/mapper/tests)
   before changing it — don't guess conventions this codebase doesn't use.
2. Confirm what the task actually requires before implementing; keep the change scoped
   to that — no incidental refactoring of code you didn't need to touch.
3. Think through security, validation, transaction boundaries, and concurrency
   implications for any new write path, even if the task description doesn't mention
   them.
4. Add or update tests for what changed, following the existing naming convention
   (`methodUnderTest_condition_expectedOutcome`) and the `Test`/`IT` split. See
   "Automated tests are part of the task" below for the detailed requirements — this
   is not optional polish, it's part of finishing the task.
5. Actually run the relevant tests (`./mvnw test` for a quick loop, `./mvnw verify`
   before calling anything done — the latter needs Docker for Testcontainers) and
   report real output. Never state that tests, a build, or a command succeeded unless
   you actually ran it and saw it pass.
6. Review your own `git diff` before reporting completion; check it against the
   original request.
7. If something couldn't be verified (no Docker available, a command you didn't run,
   an assumption about intent), say so explicitly instead of implying it was checked.

## Automated tests are part of the task

Writing or changing production code without matching tests is an unfinished task, not
a finished one deferred for later. These rules sit above `.claude/rules/testing.md`
(which covers naming, the shared-Testcontainers-context model, and the auth-testing
helpers in detail) — read that file for the mechanics; this section is about when
tests are required and how to decide what to write.

1. **Tests are not optional, and not a separate task.** Every new feature or behavior
   change must include relevant automated tests in the same task/PR, not a follow-up.
   A bug fix must include a regression test that reproduces the bug (failing before the
   fix, passing after) — not just a verification that the fix "seems to work".
2. **Use what's already configured.** This project has JUnit 5, Mockito, Spring Boot
   Test, and Testcontainers (Postgres) on the test classpath (see `pom.xml`). Write
   tests with these, following the existing structure under `src/test/java` (tests
   mirror the `book`/`auth`/`user`/`shared` feature packages, with shared helpers in
   `com.alon.bookstore.support`). Don't introduce a new test framework/library for
   something these already cover.
3. **Pick the right scope for what changed** — don't default to the heaviest option:
   - *Unit (`*Test.java`, no Spring context)* — business logic, validation rules,
     calculations, edge cases in a service/mapper/helper. See `BookServiceTest`,
     `AuthServiceTest`, `BookMapperTest`. Mock collaborators with Mockito; don't boot
     Spring for logic that doesn't need it.
   - *Integration (`*IT.java`, real Spring context + Testcontainers Postgres)* — JPA
     queries, `Specification`s, persistence behavior, DB constraints (unique/FK), and
     multi-component wiring. See `BookRepositoryIT`, `BookSpecificationIT`,
     `UserRepositoryIT`, `FlywayMigrationIT`.
   - *API (`*IT.java` with `@AutoConfigureMockMvc`)* — request validation, response
     body shape, HTTP status codes, pagination/filtering params, error responses. See
     `BookFilterApiIT`.
   - *Security (`*IT.java`, real filter chain — never `@WithMockUser` for these
     journeys)* — authentication, authorization (`/api/admin/**` etc.), session
     handling, CSRF rotation. See `AuthenticationFlowIT` and the
     `CsrfTestSupport`/`AuthTestClient` helpers. Required whenever the feature touches
     `SecurityConfig`, `auth/*`, or route authorization — see
     `.claude/rules/security.md`.
   Not every change needs all four categories — cover the ones the change actually
   touches.
4. **Cover success and failure, including the boundaries that matter for this
   feature** (empty results, pagination edges, invalid/missing input, conflicts,
   unauthorized access, concurrency where relevant) — but don't pad coverage with
   categories the change doesn't exercise just to check a box.
5. **Test observable behavior, not implementation.** A test should fail if the
   feature's actual contract (return value, persisted state, HTTP response, thrown
   exception) breaks — not if an internal method gets renamed. Don't assert trivial
   getters/setters, and don't mock out the exact behavior a test is supposed to be
   verifying (that proves the mock works, not the code).
6. **Keep tests deterministic and isolated.** Use the existing `TestcontainersConfiguration`
   (pinned `postgres:16-alpine`) for anything depending on real Postgres behavior —
   never a hand-rolled connection, H2, or production/dev database credentials. Be aware
   that IT classes with identical Spring context config share one container/schema
   within a run (see `.claude/rules/testing.md` § Shared Testcontainers context); clean
   up rows you insert/delete in `@AfterEach` if another suite asserts exact counts.
7. **Verify how the tests actually get discovered and run before trusting them**:
   - `*Test.java` → Surefire, `test` phase → `./mvnw test`. No Docker needed as long as
     the test doesn't open a Spring context.
   - `*IT.java` → Failsafe, `integration-test`/`verify` phases → `./mvnw verify`.
     Needs Docker running (Testcontainers).
   - Surefire's and Failsafe's default include patterns are what's in effect here —
     `pom.xml` doesn't override them (no explicit `<includes>`/`<excludes>`). A test
     class that doesn't match either plugin's pattern (e.g. misnamed) silently runs in
     neither suite — `./mvnw test` passing is not evidence an `*IT.java` you just added
     ran at all.
   - `./mvnw test` alone never proves integration-level correctness; don't treat it as
     equivalent to `./mvnw verify`.
8. **Run the relevant tests before calling the feature done.** At minimum, run the
   `*Test.java`/`*IT.java` classes for the package(s) you touched; run the full
   `./mvnw verify` when the change affects shared behavior or configuration (security
   filter chain, `GlobalExceptionHandler`, Flyway migrations, `SecurityConfig`,
   anything cross-cutting). If a run is blocked (no Docker, a command you didn't
   execute) or a test fails, say so explicitly and don't claim success you didn't
   observe.

## Final summary for every implementation task

When reporting a feature/change as complete, the summary must cover, in English:

- What behavior was added or changed.
- What tests were added or updated, and what they cover (which scope — unit/
  integration/API/security — and which success/failure/boundary cases).
- The exact commands run (`./mvnw test`, `./mvnw verify`, etc.) and their actual
  results — not an assumption that they would pass.
- Any remaining limitations, skipped scopes, or verification gaps (e.g. "Docker
  wasn't available so `*IT.java` wasn't run", "concurrency behavior wasn't tested").

## Branch and commit workflow

- Start each new feature on a new branch based on `dev`.
- For follow-up changes to a feature already in progress, continue on that feature's
  existing branch rather than branching again.
- Never create a commit without the user's explicit approval, given after they've had
  a chance to review the change.

## Reviewing a branch

For "review this branch against `<base>`" style requests, use the `branch-review`
skill — it runs a stricter, repo-specific checklist (security/authz, JPA/Hibernate,
transactions/concurrency, architecture conventions, missing tests) than a generic diff
review.
