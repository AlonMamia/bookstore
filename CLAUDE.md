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
   (`methodUnderTest_condition_expectedOutcome`) and the `Test`/`IT` split.
5. Actually run the relevant tests (`./mvnw test` for a quick loop, `./mvnw verify`
   before calling anything done — the latter needs Docker for Testcontainers) and
   report real output. Never state that tests, a build, or a command succeeded unless
   you actually ran it and saw it pass.
6. Review your own `git diff` before reporting completion; check it against the
   original request.
7. If something couldn't be verified (no Docker available, a command you didn't run,
   an assumption about intent), say so explicitly instead of implying it was checked.

## Reviewing a branch

For "review this branch against `<base>`" style requests, use the `branch-review`
skill — it runs a stricter, repo-specific checklist (security/authz, JPA/Hibernate,
transactions/concurrency, architecture conventions, missing tests) than a generic diff
review.
