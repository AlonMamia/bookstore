package com.alon.bookstore.shared.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Generic, cross-cutting HTTP request logging.
 * <p>
 * Assigns a correlation id (accepting a caller-supplied {@value #REQUEST_ID_HEADER} header
 * when it looks safe, otherwise generating one), exposes it via MDC and on the response,
 * and logs one summary line per request. Runs before Spring Security so the id is present
 * for authentication/authorization failures too.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    static final String REQUEST_ID_HEADER = "X-Request-Id";
    static final String REQUEST_ID_MDC_KEY = "requestId";

    // Deliberately restrictive: prevents log/header injection via an attacker-supplied id.
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[a-zA-Z0-9-]{1,64}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String requestId = resolveRequestId(request.getHeader(REQUEST_ID_HEADER));
        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        long startTime = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            logRequest(request, response, System.currentTimeMillis() - startTime);
            // Prevents MDC leaking into whatever the next request on this pooled thread does.
            MDC.remove(REQUEST_ID_MDC_KEY);
        }
    }

    private void logRequest(HttpServletRequest request, HttpServletResponse response, long durationMs) {
        int status = response.getStatus();
        String method = request.getMethod();
        String path = request.getRequestURI();

        if (status >= 500) {
            log.warn("{} {} -> {} ({} ms)", method, path, status, durationMs);
        } else {
            log.info("{} {} -> {} ({} ms)", method, path, status, durationMs);
        }
    }

    private String resolveRequestId(String incoming) {
        if (incoming != null && SAFE_REQUEST_ID.matcher(incoming).matches()) {
            return incoming;
        }
        return UUID.randomUUID().toString();
    }
}
