package com.alon.bookstore.support;

import com.alon.bookstore.auth.LoginRequest;
import com.alon.bookstore.auth.RegisterRequest;
import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.UUID;

import static com.alon.bookstore.support.CsrfTestSupport.fetchCsrfCookie;
import static com.alon.bookstore.support.CsrfTestSupport.isUsable;
import static com.alon.bookstore.support.CsrfTestSupport.latestCookie;
import static com.alon.bookstore.support.CsrfTestSupport.withCsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Drives the real registration/login/logout endpoints through MockMvc so integration
 * tests can obtain an authenticated session the same way a real client would: a valid
 * CSRF token, real password hashing/authentication, and a real HTTP session.
 * <p>
 * {@link #register} always registers a plain USER account. Tests that need an ADMIN
 * session must create that user through a trusted fixture (direct repository access)
 * and log in with this client, not through register().
 * <p>
 * MockMvc never writes a JSESSIONID Set-Cookie header (that's normally the servlet
 * container's job, and there is no real container here), so session continuity across
 * separate {@code mockMvc.perform()} calls is carried via the underlying
 * {@link MockHttpSession} instance rather than a cookie.
 */
public class AuthTestClient {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    public AuthTestClient(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    public static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.test";
    }

    public MvcResult register(String email, String password) throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail(email);
        request.setPassword(password);
        request.setRole("USER");
        request.setFirstName("Test");
        request.setLastName("User");
        request.setDateOfBirth(LocalDate.of(1990, 1, 1));

        Cookie csrf = fetchCsrfCookie(mockMvc);

        return mockMvc.perform(withCsrf(
                post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                csrf
        )).andReturn();
    }

    public Session registerAndLogin(String email, String password) throws Exception {
        MvcResult registration = register(email, password);
        if (registration.getResponse().getStatus() != 201) {
            throw new IllegalStateException(
                    "Registration failed with status " + registration.getResponse().getStatus());
        }
        return login(email, password);
    }

    public Session login(String email, String password) throws Exception {
        Cookie csrfBeforeLogin = fetchCsrfCookie(mockMvc);

        MvcResult result = mockMvc.perform(withCsrf(
                post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))),
                csrfBeforeLogin
        )).andReturn();

        if (result.getResponse().getStatus() != 200) {
            throw new IllegalStateException("Login failed with status " + result.getResponse().getStatus());
        }

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        if (session == null) {
            throw new IllegalStateException("Login did not open an HTTP session");
        }

        // CsrfAuthenticationStrategy rotates the token on login: it eagerly clears the
        // old cookie (Max-Age=0 / blank value), then schedules a new one via a
        // *deferred* token that nothing in this response resolves, so no fresh,
        // usable Set-Cookie actually appears here in practice. A real browser deletes
        // a cookie sent with Max-Age=0 - it would never resend the pre-login value - so
        // the only faithful fallback is to ask the real CSRF endpoint again, now that
        // the session is authenticated, and let it mint a fresh token for that session.
        Cookie rotatedCsrf = latestCookie(result, CsrfTestSupport.CSRF_COOKIE);
        Cookie csrfForNextRequest = isUsable(rotatedCsrf)
                ? rotatedCsrf
                : fetchCsrfCookie(mockMvc, session);

        return new Session(session, csrfForNextRequest);
    }

    public record Session(MockHttpSession httpSession, Cookie csrfCookie) {
    }
}
