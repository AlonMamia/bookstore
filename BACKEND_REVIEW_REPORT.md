# Backend Review Report — Tests, Test Helpers, Exception Handling, CI Reporting, Repository Instructions

Scope: backend code, tests/test helpers, relevant Maven/workflow configuration, and
documentation (`CLAUDE.md`, `.claude/rules/*`, `README.md`,
`.claude/skills/branch-review/SKILL.md`). Frontend, deploy triggers, and Terraform
apply/destroy were explicitly out of scope and were not touched.

## 1. Findings confirmed and fixed (this session)

- **Duplicate-email detection in `AuthService`** — `isEmailUniqueConstraintViolation`
  relied on a free-text substring match ("email") against the raw DB error message.
  Hardened it: it now first tries to extract the real constraint name
  (`users_email_key`) from the `org.hibernate.exception.ConstraintViolationException`
  wrapped inside `DataIntegrityViolationException`, and only falls back to the
  message-substring check when no constraint name is available (so the existing unit
  test that constructs a bare `DataIntegrityViolationException` without a cause still
  passes). This avoids ever mapping *every* `DataIntegrityViolationException` to
  "duplicate email" and never exposes the raw DB message to the client (nor did it
  before).
- **`BookController` — non-numeric `page`/`size`** — fixed earlier in this same
  session (before this review request): `GET /api/books?page=not-a-number` returned
  200 instead of 400, because Spring Data's `Pageable` argument resolver silently
  swallows a `NumberFormatException` and falls back to its default page instead of
  failing the request. Confirmed still fixed and covered by tests.
- **`BookSpecificationIT`**:
  - `query_matchesTitleCaseInsensitively` now searches for `"DRAGON"` (uppercase)
    against the stored title "The Dragon's Heir" — an unambiguous proof of
    case-insensitivity (a case-sensitive `LIKE` would not match), rather than a query
    that could coincidentally pass either way.
  - `pagination_splitsResultsAcrossPagesConsistently` issued the same "middle page"
    query twice. Fixed to query it once, capture the result, and assert against that
    single `Page<Book>`.
  - Added `priceRange_atExactBoundaries_isInclusive`, an isolated-fixture test
    (inserted rows cleaned up in `@AfterEach`) proving `minPrice`/`maxPrice` include
    values exactly at the boundary (100.00 / 120.00) and exclude values just outside
    it — the seeded catalog has no book priced at exactly those values, so the
    existing range test never actually exercised the `>=`/`<=` boundary itself.
- **`BookFilterApiIT`** — added `searchBooks_withNonNumericSize_returnsBadRequest`
  (symmetric to the existing non-numeric-`page` test, now that the controller
  validates both), and corrected a misleading comment (see below).
- **Documentation that was actually wrong** (`CLAUDE.md`, `.claude/rules/*`,
  `README.md`, `branch-review` skill):
  - Removed the claim that `IllegalArgumentException → 409` is an architectural
    invariant — that's no longer true in the code: `IllegalArgumentException` maps to
    400, and a dedicated `EmailAlreadyExistsException` maps to 409 for the genuine
    conflict case.
  - Corrected the inaccurate claim ("there's no configured `AuthenticationEntryPoint`")
    appearing in `SecurityConfig`-adjacent docs and in two test comments
    (`AuthenticationFlowIT.me_withoutAuthentication_isRejected`,
    `BookFilterApiIT.searchBooks_withoutAuthentication_isRejected`). Verified via
    bytecode inspection of `ExceptionHandlingConfigurer` (Spring Security 7.1.1) that
    Spring Security *does* install a default entry point
    (`Http403ForbiddenEntryPoint`) when neither `formLogin()` nor `httpBasic()`
    registers one — that default is what actually produces the 403 (not 401).
  - Corrected a wrong claim in `infra-cicd.md` that a container-name mismatch between
    Terraform and the workflow's `container-name` input would make the ECS render
    step "silently target the wrong container." Fetched and read the actual source of
    `aws-actions/amazon-ecs-render-task-definition` (master branch, GitHub) — it
    throws an explicit error ("Could not find container definition with matching
    name") and the action fails outright; it does not silently misbehave.
  - Added explicit documentation of the prohibition on privileged public registration,
    a corrected description of the post-login CSRF flow, a clarification that
    `anyRequest().authenticated()` is not equivalent to "deny every unmatched route"
    (it only gates on authentication status before dispatch even knows whether a
    controller exists for the path), and a clarification that pp/prod schema
    separation is not an authorization boundary (the RDS master user is shared
    between both — already honestly documented in `infra/rds.tf`/`infra/README.md`,
    just not previously reflected in `.claude/rules`).
  - Fixed three stale `postgres:latest` references in `README.md` (the actual
    `TestcontainersConfiguration` pins `postgres:16-alpine`), and corrected the
    description of Surefire's/Failsafe's default include patterns (they also match
    `Test*.java`/`*Tests.java`/`*TestCase.java` and `IT*.java`/`*ITCase.java`, not only
    `*Test.java`/`*IT.java`).
  - Softened the blanket "never use `@WithMockUser`" rule into a nuance: it's
    forbidden specifically for the critical authentication/authorization journeys
    themselves, not for every focused test in the codebase.

## 2. Findings disproved or intentionally left unchanged

Most of findings 2–6 from the review turned out to **already be fixed** in the
working tree, from earlier work this conversation did not have transcript access to.
Each was verified by reading the actual code and running the tests, not taken on
faith:

- `RegisterRequest` already has no `role` field at all; `AuthService.register` always
  resolves `"USER"`. A regression test,
  `register_withForgedAdminRoleInPayload_isIgnored_andCreatesOnlyARegularUser`, already
  proves a forged `"role":"ADMIN"` in the raw JSON body is ignored, both via `/me` and
  via a real request to `/api/admin/anything`. The ADMIN test fixture is already
  created through a direct repository write (`createAdminAndLogIn`), never through the
  public endpoint.
- `AuthTestClient.login`/`CsrfTestSupport` already do **not** fall back to reusing the
  pre-login cookie — they check `isUsable()` (rejecting `Max-Age=0`/blank values) and
  fetch a fresh token via `fetchCsrfCookie(mockMvc, session)` when needed. A full
  end-to-end test,
  `login_withValidCredentials_obtainsUsableCsrfToken_andProtectedPostThenSucceeds`,
  already proves login → obtaining a valid token → a protected POST succeeding, plus
  existing tests for missing/mismatched CSRF.
- `AuthServiceTest` already captures the `Authentication` submitted to
  `AuthenticationManager`, captures the saved `SecurityContext`, cleans up
  `SecurityContextHolder`, and has a failed-authentication test proving no context is
  persisted.
- `BookServiceTest` already uses two entities, a non-first page, and an empty page,
  without pretending the mocked `Specification` exercises real filtering.
- `CustomUserDetailsTest` already asserts `ROLE_USER`/`ROLE_ADMIN` from real `Role`
  fixtures, in the class actually responsible for producing authorities.
- `BookRepositoryIT`/`UserRepositoryIT` already use `saveAndFlush` and explicit
  `@AfterEach` cleanup.
- `FlywayMigrationIT` already avoids asserting a hardcoded "1.5" version and already
  separates migration-mechanics verification from the sample-catalog assertion.
- `TestcontainersConfiguration` already pins `postgres:16-alpine` (matching
  `infra/variables.tf`'s `rds_engine_version = "16.4"`).
- The `NoResourceFoundException → 404` handler is already verified both by a unit test
  and by a real, authorization-passing IT request
  (`adminOnlyEndpoint_withAdminUser_passesAuthorization`).
- The BCrypt/password-length fix (`@MaxUtf8Bytes(72)`) already has boundary tests with
  multibyte characters.
- **`maxPrice=0` meaning "unbounded"** — left **unchanged, deliberately**. This is a
  surprising product decision in `BookSpecification` (ignoring `maxPrice <= 0`), not a
  binding-related workaround (unlike `availableOnly`, which was a primitive `boolean`;
  `maxPrice` is already a nullable `BigDecimal`). No documented requirement clarifies
  whether this is intentional. No business rule was changed — this is flagged here as
  an ambiguity that needs a product decision, not a technical bug to silently fix.
- The claims in `infra-cicd.md` that "`mvn clean verify` runs exactly once per
  push/PR," that OIDC/`container-name: backend`/the `ignore_changes` lifecycle blocks
  are correctly wired, were checked against the actual workflow files and
  `infra/ecs.tf` and found **accurate** — left unchanged.
- No frontend exists in this repository (only `postman/`/`.postman/`, neither
  referencing `role`), so there is no frontend-compatibility risk from the earlier
  removal of the `role` field from `RegisterRequest`.
- `./mvnw` failed inside this session's Bash tool (tried to download the Maven
  distribution and had no network access from that shell) — but `mvnw.cmd` via
  PowerShell worked fine (used the already-cached 3.9.16 distribution). This is a
  quirk of this agent session's shell environment, not a repository defect, so no
  `./mvnw` references in the docs were changed.

## 3. Production behavior / API contract changes

- One real behavioral change: `AuthService.register` no longer maps *every*
  `DataIntegrityViolationException` containing "email" in its free-text message to a
  conflict — it now prefers the actual DB constraint name. This is **not** a
  client-visible API contract change (still 409 + "Email already exists" on a genuine
  duplicate), only an internal hardening of the detection condition.
- `GET /api/books` with a non-numeric `size` now returns 400 instead of 200 — fixed in
  the preceding turn of this same session, confirmed still in effect here.
- No DTO/role contract (`/me`, login, register) was changed in this session — those
  were already correct from prior work.

## 4. Commands actually executed and results

- **Baseline** (before this task's changes, after the earlier `page`/`size` fix):
  `mvn --batch-mode clean verify` → **BUILD SUCCESS**, 34 unit + 48 integration tests,
  0 failures.
- During implementation: `mvn -q test-compile`, `mvn -q test -Dtest=AuthServiceTest`,
  and `mvn verify -Dit.test=BookSpecificationIT,BookFilterApiIT,AuthenticationFlowIT
  -Dsurefire.skip=true` — all passed (44 tests, 0 failures).
- **Final verify after all changes**: `mvn --batch-mode clean verify` → **BUILD
  SUCCESS**, **34 unit + 50 integration = 84 tests total, 0 failures, 0 errors**.
- `./mvnw` (POSIX wrapper) failed under the session's Bash tool: `wget: Failed to
  fetch https://repo.maven.apache.org/.../apache-maven-3.9.16-bin.zip` — no network
  from that shell. Used the system-installed Maven (3.9.11) / `mvnw.cmd` via
  PowerShell instead (both resolve to the same effective configuration as
  `<distributionUrl>`).
- Versions verified (not from memory — via `dependency:tree` / `help:effective-pom`):
  Spring Boot **4.1.1**, Spring Framework **7.0.9**, Spring Security **7.1.1**,
  Jackson **3.1.5** (`tools.jackson`, i.e. Jackson 3, not `com.fasterxml.jackson`),
  PostgreSQL JDBC driver **42.7.13**, Flyway **12.4.0**, Surefire/Failsafe **3.5.6**
  (both, with no explicit `<includes>` override in `pom.xml`).
- Docker was not running at the start of the session — started Docker Desktop
  (`Start-Service`/`Start-Process`) to run the IT suite; a local, reversible action,
  not a deploy.

## 5. Remaining blockers or meaningful gaps

- **Unresolved product ambiguity**: `maxPrice=0` meaning "unbounded" needs a product
  decision — if intentional, it deserves explicit documentation in the code/API docs;
  if not, it's a latent bug.
- **A documented but unaddressed infrastructure gap** (out of this task's scope): pp
  and prod connect to RDS as the same master user; schema separation is namespacing
  only, not a DB-level authorization boundary. `infra/README.md` already tracks a
  planned least-privilege-per-schema follow-up that has not been implemented.
- The `Dockerfile`, Terraform (`plan`/`apply`), and any secrets/state were not touched,
  as required.
- The "fails outright" behavior of `amazon-ecs-render-task-definition` was verified
  against the action's current source on GitHub, not by triggering an actual CI
  mismatch in this repository's workflows.

## 6. Files changed (this session, beyond prior work already present)

**Production code:**
- `src/main/java/com/alon/bookstore/auth/AuthService.java`

**Tests:**
- `src/test/java/com/alon/bookstore/book/BookSpecificationIT.java`
- `src/test/java/com/alon/bookstore/book/BookFilterApiIT.java`
- `src/test/java/com/alon/bookstore/auth/AuthenticationFlowIT.java` (comment fix only)

**Documentation:**
- `CLAUDE.md`
- `.claude/rules/security.md`
- `.claude/rules/testing.md`
- `.claude/rules/infra-cicd.md`
- `.claude/skills/branch-review/SKILL.md`
- `README.md`

Other files showing as modified/untracked in `git status` (e.g. `BookController.java`,
`GlobalExceptionHandler.java`, `RegisterRequest.java`, `AuthController.java`, the new
`support`/`auth`/`book`/`user` test files, the GitHub workflows, `pom.xml`) were
inspected and verified in this session but **not edited** here — some were fixed in
the immediately preceding turn of this conversation, and the rest were already in
that state from earlier work this session had no transcript access to.
