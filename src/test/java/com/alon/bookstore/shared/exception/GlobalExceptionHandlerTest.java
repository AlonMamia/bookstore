package com.alon.bookstore.shared.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsIllegalArgumentToConflictWithoutLeakingInternals() {
        ResponseEntity<ProblemDetail> response =
                handler.handleIllegalArgument(new IllegalArgumentException("Email already exists"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getDetail()).isEqualTo("Email already exists");
    }

    @Test
    void mapsAuthenticationFailureToUnauthorizedWithGenericMessage() {
        ResponseEntity<ProblemDetail> response =
                handler.handleAuthentication(new BadCredentialsException("Bad credentials"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // Must never echo back internal authentication details to the client.
        assertThat(response.getBody().getDetail()).isEqualTo("Invalid credentials");
    }

    @Test
    void mapsUnexpectedExceptionToGenericServerError() {
        ResponseEntity<ProblemDetail> response =
                handler.handleUnexpected(new RuntimeException("db connection string: secret-stuff"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getDetail()).isEqualTo("An unexpected error occurred");
    }
}
