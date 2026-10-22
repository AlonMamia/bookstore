---
name: branch-review
description: Strict engineering review of this branch's changes against a base branch (e.g. "review this branch against dev", "review my changes vs main"). Checks correctness bugs, missing requirements, security/authorization, validation gaps, JPA/Hibernate issues, transaction/concurrency problems, architecture-convention violations, unnecessary complexity, missing tests, and regressions, specific to this Spring Boot bookstore codebase. Prefer this over a generic diff review for this repo.
---

# Branch review

A strict review of the current branch's diff against a base branch, for this
Spring Boot bookstore codebase specifically. Default base branch is `dev` unless the
user names another one.

## 1. Gather the actual change

- `git fetch origin <base>` if needed, then `git diff origin/<base>...HEAD --stat` and
  the full `git diff origin/<base>...HEAD` for content.
- `git log origin/<base>..HEAD --oneline` for commit messages — they're the closest
  thing to a stated requirement; note what they claim to do.
- For every changed file, **read the whole file**, not just the diff hunk — a
  correctness or architecture problem is often visible only in context (e.g. a new
  `if` branch next to an existing one it should be consistent with).
- For every changed service/controller, also open the files it directly calls into
  (repository, mapper, entity, migration) even if they weren't touched — a change can
  be correct in isolation and still break an invariant those files rely on.

## 2. Establish intent before judging correctness

State in your own words what the change is supposed to do, based on commit
messages/PR description/code. If that's genuinely ambiguous, say so explicitly rather
than reviewing against an assumption you invented — "missing requirement" findings
especially depend on actually knowing the requirement.

## 3. Checklist

Load `.claude/rules/persistence.md`, `.claude/rules/security.md`,
`.claude/rules/testing.md`, and `.claude/rules/infra-cicd.md` (whichever are relevant
to the changed files) before finishing this pass — they encode this repo's actual
conventions and known sharp edges, not generic advice.

- **Bugs / correctness** — does the code do what it appears intended to do, including
  edge cases (empty/null inputs, boundary values, concurrent requests)?
- **Missing requirements** — does the diff fully cover what the commit
  messages/description claim, or is part of it stubbed/incomplete?
- **Security & authorization** — new/changed endpoint: is it covered by an explicit
  `SecurityConfig` rule (not silently falling through to default-authenticated when it
  should be public, or vice versa)? Does anything leak internal detail in an error
  message? Is CSRF still exercised in its tests?
- **Validation gaps** — do new request fields have jakarta.validation constraints
  consistent with neighboring fields? Does a new exception type reach some
  `@ExceptionHandler` in `GlobalExceptionHandler` — an existing one it's a subtype of
  counts, it doesn't need a handler of its own — or does it fall through to the
  generic 500 unhandled?
- **JPA/Hibernate issues** — N+1 risk from a new lazy-association access pattern,
  missing `@EntityGraph`/fetch join, a schema change without a matching Flyway
  migration, anything that would fail `ddl-auto=validate`.
- **Transactions & concurrency** — a new multi-step write without `@Transactional`
  where partial failure would leave inconsistent state; a new check-then-act pattern
  racing against a DB constraint; a mutable concurrently-updated entity missing
  `@Version`.
- **Architecture conventions** — package-by-feature placement, DTO/entity separation
  (no entity returned/accepted directly by a controller), mapper usage instead of
  manual field copying, `PageResponse` instead of raw `Page`, `Specification` instead
  of ad hoc JPQL for multi-field filters.
- **Unnecessary complexity** — new abstraction, config flag, or indirection the task
  didn't need; speculative generality; dead code.
- **Test coverage** — does new/changed behavior have a test, in the right suite
  (`*Test.java` vs `*IT.java` per `.claude/rules/testing.md`), following the
  `methodUnderTest_condition_expectedOutcome` naming convention? Does a new IT test
  that inserts rows clean up after itself given the shared-container model?
- **Regressions** — could this break an existing test's assumptions (shared Flyway
  seed counts, CSRF flow, an existing security rule's precedence)?

## 4. Verify, don't assume

If practical, actually run `./mvnw verify` (needs Docker) against the branch and
report the real result. If you can't run it (no Docker, time constraints), say so
explicitly — don't imply tests were confirmed passing when they weren't run.

## 5. Report

Group findings by severity: **blocking** (bug, security hole, missing requirement),
**should-fix** (validation/transaction/architecture issue, missing test), **nit**
(style, minor complexity). For each finding give the file:line, the concrete failure
scenario (not just "this could be an issue"), and a specific suggested fix. If a
category above has nothing wrong, it's fine to say so briefly — don't pad the report
with praise, and don't claim "no issues found" anywhere you didn't actually read the
code carefully enough to back that up.
