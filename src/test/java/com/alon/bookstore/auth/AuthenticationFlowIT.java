package com.alon.bookstore.auth;

import com.alon.bookstore.TestcontainersConfiguration;
import com.alon.bookstore.support.AuthTestClient;
import com.alon.bookstore.support.CsrfTestSupport;
import com.alon.bookstore.user.Role;
import com.alon.bookstore.user.RoleRepository;
import com.alon.bookstore.user.User;
import com.alon.bookstore.user.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;

import static com.alon.bookstore.support.AuthTestClient.uniqueEmail;
import static com.alon.bookstore.support.CsrfTestSupport.fetchCsrfCookie;
import static com.alon.bookstore.support.CsrfTestSupport.withCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real Spring Security filter chain end to end: the actual
 * /api/auth endpoints, real password hashing + authentication, real HTTP
 * sessions and the app's cookie-based SPA CSRF protection. No mocked users.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationFlowIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private AuthTestClient authClient;

    private AuthTestClient client() {
        if (authClient == null) {
            authClient = new AuthTestClient(mockMvc, objectMapper);
        }
        return authClient;
    }

    /**
     * AuthTestClient.register always creates a plain USER account, so an ADMIN
     * fixture is created directly via the repositories instead; login and the request
     * itself still go through the real endpoint/filter chain.
     */
    private AuthTestClient.Session createAdminAndLogIn(String email, String password) throws Exception {
        Role adminRole = roleRepository.findByTitle("ADMIN").orElseThrow();

        User admin = new User();
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRole(adminRole);
        admin.setFirstName("Admin");
        admin.setLastName("User");
        admin.setDateOfBirth(LocalDate.of(1990, 1, 1));
        userRepository.save(admin);

        return client().login(email, password);
    }

    @Test
    void csrfEndpoint_isPubliclyAccessible_andIssuesACookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie csrfCookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrfCookie).isNotNull();
        assertThat(csrfCookie.getValue()).isNotBlank();
    }

    @Test
    void register_withoutCsrfToken_isRejected() throws Exception {
        RegisterRequest request = registerRequest(uniqueEmail(), "password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void register_withInvalidCsrfToken_isRejected() throws Exception {
        // A real XSRF-TOKEN cookie, but a header that doesn't match it: the double-submit
        // check must compare the two, not just require "some" header to be present.
        Cookie realCsrfCookie = fetchCsrfCookie(mockMvc);
        RegisterRequest request = registerRequest(uniqueEmail(), "password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "not-the-real-token")
                        .cookie(realCsrfCookie)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void register_withValidCsrf_createsUser() throws Exception {
        Cookie csrf = fetchCsrfCookie(mockMvc);
        RegisterRequest request = registerRequest(uniqueEmail(), "password123");

        mockMvc.perform(withCsrf(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)), csrf))
                .andExpect(status().isCreated());
    }

    @Test
    void register_duplicateEmail_returnsConflictWithoutLeakingInternals() throws Exception {
        String email = uniqueEmail();
        Cookie csrf = fetchCsrfCookie(mockMvc);
        RegisterRequest request = registerRequest(email, "password123");

        mockMvc.perform(withCsrf(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)), csrf))
                .andExpect(status().isCreated());

        mockMvc.perform(withCsrf(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)), csrf))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Email already exists"));
    }

    @Test
    void register_invalidPayload_returnsFieldValidationErrors() throws Exception {
        Cookie csrf = fetchCsrfCookie(mockMvc);
        RegisterRequest request = registerRequest("not-an-email", "short");
        request.setDateOfBirth(LocalDate.now().plusDays(1));

        mockMvc.perform(withCsrf(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)), csrf))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.dateOfBirth").exists());
    }

    @Test
    void login_withValidCredentials_returnsUser_andOpensSession() throws Exception {
        String email = uniqueEmail();
        String password = "password123";
        client().register(email, password);

        Cookie csrfBeforeLogin = fetchCsrfCookie(mockMvc);
        MvcResult loginResult = mockMvc.perform(withCsrf(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))), csrfBeforeLogin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        assertThat(loginResult.getRequest().getSession(false)).isNotNull();

        // CsrfAuthenticationStrategy clears the pre-login cookie on login (Max-Age=0 /
        // blank value); it does not synchronously reissue a usable replacement in this
        // same response - a real browser would delete the cleared cookie, not keep
        // sending it. See login_withValidCredentials_obtainsUsableCsrfToken_... below
        // for how a real client recovers a token afterward, and AuthTestClient.login
        // for the helper that does it the same way.
        Cookie csrfAfterLogin = CsrfTestSupport.latestCookie(loginResult, "XSRF-TOKEN");
        assertThat(CsrfTestSupport.isUsable(csrfAfterLogin)).isFalse();
    }

    @Test
    void login_withValidCredentials_obtainsUsableCsrfToken_andProtectedPostThenSucceeds() throws Exception {
        String email = uniqueEmail();
        String password = "password123";

        // 1. Real login succeeds.
        AuthTestClient.Session session = client().registerAndLogin(email, password);
        assertThat(session.httpSession()).isNotNull();

        // 2. The client obtains a valid post-login CSRF token.
        assertThat(CsrfTestSupport.isUsable(session.csrfCookie())).isTrue();

        // 3. A protected POST succeeds with the authenticated session and current
        // cookie/header (logout is the only protected write endpoint this API exposes).
        mockMvc.perform(withCsrf(post("/api/auth/logout"), session.csrfCookie())
                        .session(session.httpSession()))
                .andExpect(status().isNoContent());
    }

    @Test
    void login_withWrongPassword_returnsUnauthorizedWithGenericMessage() throws Exception {
        String email = uniqueEmail();
        client().register(email, "password123");

        Cookie csrf = fetchCsrfCookie(mockMvc);
        mockMvc.perform(withCsrf(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "wrong-password"))), csrf))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid credentials"));
    }

    @Test
    void login_withUnknownEmail_returnsUnauthorizedWithGenericMessage() throws Exception {
        Cookie csrf = fetchCsrfCookie(mockMvc);
        mockMvc.perform(withCsrf(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(uniqueEmail(), "whatever123"))), csrf))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid credentials"));
    }

    @Test
    void login_withoutCsrfToken_isRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(uniqueEmail(), "whatever123"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void login_withMismatchedCsrfToken_isRejected() throws Exception {
        Cookie realCsrfCookie = fetchCsrfCookie(mockMvc);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "not-the-real-token")
                        .cookie(realCsrfCookie)
                        .content(objectMapper.writeValueAsString(new LoginRequest(uniqueEmail(), "whatever123"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void me_withAuthenticatedSession_returnsCurrentUserWithoutSensitiveFields() throws Exception {
        String email = uniqueEmail();
        AuthTestClient.Session session = client().registerAndLogin(email, "password123");

        mockMvc.perform(get("/api/auth/me").session(session.httpSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role.title").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void me_withoutAuthentication_isRejected() throws Exception {
        // formLogin()/httpBasic() aren't configured, so no mechanism registers its own
        // AuthenticationEntryPoint. Spring Security still installs a default one in
        // that case (ExceptionHandlingConfigurer falls back to Http403ForbiddenEntryPoint)
        // - that's what denies anonymous access to an authenticated-only route as 403,
        // not 401, not the absence of any entry point.
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    void protectedEndpoint_withoutAuthentication_isRejected() throws Exception {
        mockMvc.perform(get("/api/books"))
                .andExpect(status().isForbidden());
    }

    @Test
    void protectedEndpoint_withAuthenticatedSession_isAllowed() throws Exception {
        AuthTestClient.Session session = client().registerAndLogin(uniqueEmail(), "password123");

        mockMvc.perform(get("/api/books").session(session.httpSession()))
                .andExpect(status().isOk());
    }

    @Test
    void adminOnlyEndpoint_withRegularUser_isForbidden() throws Exception {
        AuthTestClient.Session session = client().registerAndLogin(uniqueEmail(), "password123");

        mockMvc.perform(get("/api/admin/anything").session(session.httpSession()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminOnlyEndpoint_withAdminUser_passesAuthorization() throws Exception {
        // Admin fixture created through a trusted repository write, never through the
        // public register endpoint - see createAdminAndLogIn. Login and the request
        // itself still go through the real endpoint and the real security filter chain.
        AuthTestClient.Session session = createAdminAndLogIn(uniqueEmail(), "password123");

        // No controller is mapped under /api/admin/**; reaching dispatch (404) rather
        // than being blocked (403) proves the ADMIN authorization rule let the request through.
        mockMvc.perform(get("/api/admin/anything").session(session.httpSession()))
                .andExpect(status().isNotFound());
    }

    @Test
    void logout_invalidatesSession_soSubsequentMeIsDeniedAsAnonymous() throws Exception {
        AuthTestClient.Session session = client().registerAndLogin(uniqueEmail(), "password123");

        mockMvc.perform(withCsrf(post("/api/auth/logout"), session.csrfCookie())
                        .session(session.httpSession()))
                .andExpect(status().isNoContent());

        assertThat(session.httpSession().isInvalid()).isTrue();

        // A real client wouldn't keep resending an invalidated session; this issues a
        // fresh, unauthenticated request rather than reattaching the dead one, which is
        // what actually proves access is denied afterward.
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    void logout_withoutCsrfToken_isRejected() throws Exception {
        AuthTestClient.Session session = client().registerAndLogin(uniqueEmail(), "password123");

        mockMvc.perform(post("/api/auth/logout").session(session.httpSession()))
                .andExpect(status().isForbidden());
    }

    private RegisterRequest registerRequest(String email, String password) {
        RegisterRequest request = new RegisterRequest();
        request.setEmail(email);
        request.setPassword(password);
        request.setRole("USER");
        request.setFirstName("Test");
        request.setLastName("User");
        request.setDateOfBirth(LocalDate.of(1990, 1, 1));
        return request;
    }
}
