# Security rules

Read this before touching `SecurityConfig`, anything under `auth/`, CSRF handling, or
authorization for a new/changed endpoint.

## Auth model

- Session-based, not JWT/stateless. `SecurityContextRepository` is
  `HttpSessionSecurityContextRepository`; `AuthService.login` authenticates via
  `AuthenticationManager`, then manually builds and persists the `SecurityContext`
  (it does not rely on a filter to do this). Don't introduce bearer tokens, a stateless
  filter, or `SessionCreationPolicy.STATELESS` unless explicitly asked — it would
  conflict with this model and with the CSRF setup below.
- `CustomUserDetailsService` loads a `User` by email and wraps it in
  `CustomUserDetails`, which exposes exactly one `ROLE_<role.title>` authority. `Role`
  is a DB-backed entity (table `roles`, seeded by migration), not a Java enum — don't
  assume a fixed, compile-time set of roles.
- **Public registration can never grant a privileged role.** `RegisterRequest` has no
  `role` field at all — a client-supplied `"role":"ADMIN"` in the JSON body is an
  unmapped property and is silently ignored by Jackson, never deserialized into
  anything. `AuthService.register` always resolves the role via the hardcoded
  `DEFAULT_REGISTRATION_ROLE = "USER"` constant, never from client input. If a
  privileged-signup path is ever needed, it must be a separate, explicitly
  authorized/admin-only endpoint — never a parameter on the public endpoint. See
  `AuthenticationFlowIT.register_withForgedAdminRoleInPayload_isIgnored_...` for the
  regression test, and create ADMIN fixtures for tests via a direct repository write
  (see `AuthenticationFlowIT.createAdminAndLogIn`), never through `register()`.

## CSRF

- Cookie-based double-submit: `CookieCsrfTokenRepository.withHttpOnlyFalse()` +
  `http.csrf().spa()`, cookie `XSRF-TOKEN`, header `X-XSRF-TOKEN`.
- `CsrfAuthenticationStrategy` rotates the token on login by clearing the pre-login
  cookie (`Max-Age=0`/blank value) in that same response - it does not synchronously
  reissue a usable replacement there. A real client must treat that cleared cookie as
  deleted (never resend it) and, if the response carried no fresh usable cookie, obtain
  one afterward by calling `GET /api/auth/csrf` again with the now-authenticated
  session. See `AuthTestClient.login` / `CsrfTestSupport.isUsable` for the test helpers
  that follow this lifecycle, and
  `AuthenticationFlowIT.login_withValidCredentials_obtainsUsableCsrfToken_andProtectedPostThenSucceeds`
  for the full real flow (login → obtain token → authenticated POST succeeds).
- Every state-changing endpoint (`POST`/`PUT`/`PATCH`/`DELETE`) is CSRF-protected by
  default. New integration tests for such endpoints must go through the real flow via
  `CsrfTestSupport`/`AuthTestClient`, not bypass CSRF — a test that doesn't exercise it
  isn't testing what a real client experiences.

## Authorization

- Route rules are centralized in `SecurityConfig.securityFilterChain`'s
  `authorizeHttpRequests` block: specific matchers first, `anyRequest().authenticated()`
  last (default-deny). A new endpoint that needs a non-default rule (public, admin-only,
  etc.) must get an explicit matcher added there. Don't introduce method-level
  `@PreAuthorize`/`@Secured` unless that pattern already exists elsewhere — it would be
  a second, inconsistent place authorization lives.
- `anyRequest().authenticated()` is not "deny every unmatched route" — it only gates on
  authentication *status*, evaluated before dispatch even knows whether a controller
  exists for the path. An authenticated (or `ROLE_ADMIN`, for `/api/admin/**`) request
  to a path with no matching controller passes this rule and reaches `DispatcherServlet`,
  which then separately 404s via `NoResourceFoundException`. See
  `AuthenticationFlowIT.adminOnlyEndpoint_withAdminUser_passesAuthorization` - an
  authenticated ADMIN request to the unmapped `/api/admin/anything` gets 404, not 403,
  which is exactly how this test proves authorization let it through: an anonymous or
  non-admin request to the same path still gets 403, before reaching dispatch at all.
- `/api/admin/**` requires `ROLE_ADMIN`; everything else not explicitly listed as
  public requires authentication via `anyRequest().authenticated()` — but that rule
  alone only governs *this* filter chain's matchers; it doesn't by itself guarantee
  isolation from other authorization boundaries (e.g. a shared DB schema reachable by
  a different app/role — see `.claude/rules/infra-cicd.md`). Anonymous access to an
  authenticated-only route is a 403, not 401 — not because no entry point exists, but
  because neither `formLogin()` nor `httpBasic()` is configured to register one, so
  Spring Security's `ExceptionHandlingConfigurer` falls back to its own default
  (`Http403ForbiddenEntryPoint`).

## Error messages & secrets

- Authentication/authorization failures return deliberately generic `ProblemDetail`
  messages ("Invalid credentials", "Access denied") regardless of the underlying cause
  (unknown email vs. wrong password; which specific rule denied access). Any new
  exception handling must preserve this — never let a message reveal whether an
  account/resource exists or which internal check failed.
- Passwords are hashed with `BCryptPasswordEncoder`; never log or persist a raw
  password. `RegisterRequest.password` enforces `@Size(min = 10, max = 100)` — don't
  relax this without being asked to.
