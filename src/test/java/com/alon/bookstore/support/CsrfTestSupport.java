package com.alon.bookstore.support;

import jakarta.servlet.http.Cookie;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Drives the app's cookie-based SPA CSRF protection ({@code http.csrf().spa()} with
 * {@link org.springframework.security.web.csrf.CookieCsrfTokenRepository}) the way a
 * browser-based SPA client does: read the raw value from the {@code XSRF-TOKEN} cookie
 * and echo it back verbatim as the {@code X-XSRF-TOKEN} header. Each MockMvc call is an
 * independent exchange, so callers must carry the cookie across requests themselves.
 */
public final class CsrfTestSupport {

    public static final String CSRF_COOKIE = "XSRF-TOKEN";
    public static final String CSRF_HEADER = "X-XSRF-TOKEN";

    private CsrfTestSupport() {
    }

    /**
     * Fetches a token as an anonymous client would, before any session exists.
     */
    public static Cookie fetchCsrfCookie(MockMvc mockMvc) throws Exception {
        return fetchCsrfCookie(mockMvc, get("/api/auth/csrf"));
    }

    /**
     * Fetches a token the same way, but within an already-authenticated session - the
     * real browser-facing fallback when a post-login response didn't carry a freshly
     * issued cookie (see AuthTestClient.login): ask the actual CSRF endpoint again,
     * now that the session exists, and let CookieCsrfTokenRepository mint and persist a
     * new token tied to it.
     */
    public static Cookie fetchCsrfCookie(MockMvc mockMvc, MockHttpSession session) throws Exception {
        return fetchCsrfCookie(mockMvc, get("/api/auth/csrf").session(session));
    }

    private static Cookie fetchCsrfCookie(MockMvc mockMvc, MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request).andReturn();

        if (result.getResponse().getStatus() != 200) {
            throw new IllegalStateException(
                    "GET /api/auth/csrf returned status " + result.getResponse().getStatus());
        }

        Cookie cookie = latestCookie(result, CSRF_COOKIE);
        if (!isUsable(cookie)) {
            throw new IllegalStateException("No usable XSRF-TOKEN cookie was set by GET /api/auth/csrf");
        }
        return cookie;
    }

    public static MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder builder, Cookie csrfCookie) {
        return builder.cookie(csrfCookie).header(CSRF_HEADER, csrfCookie.getValue());
    }

    /**
     * A cookie name can appear more than once in a single response (e.g. the login
     * endpoint rotates the CSRF token by first clearing it, then issuing a new one).
     * The last occurrence reflects the value a real cookie jar would retain.
     */
    public static Cookie latestCookie(MvcResult result, String name) {
        Cookie[] cookies = result.getResponse().getCookies();
        Cookie latest = null;
        for (Cookie cookie : cookies) {
            if (cookie.getName().equals(name)) {
                latest = cookie;
            }
        }
        return latest;
    }

    /**
     * A real browser deletes a cookie sent with Max-Age=0 (or a blank value) rather
     * than keeping the old value around - so neither is a token a client could still
     * send on its next request.
     */
    public static boolean isUsable(Cookie cookie) {
        return cookie != null
                && cookie.getMaxAge() != 0
                && cookie.getValue() != null
                && !cookie.getValue().isBlank();
    }
}
