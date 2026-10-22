package com.alon.bookstore.shared.exception;

import com.alon.bookstore.auth.EmailAlreadyExistsException;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsIllegalArgumentToBadRequest() {
        // Invalid input, not a conflict - distinct from EmailAlreadyExistsException below.
        ResponseEntity<ProblemDetail> response =
                handler.handleIllegalArgument(new IllegalArgumentException("Invalid role: ADMIN"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getDetail()).isEqualTo("Invalid role: ADMIN");
    }

    @Test
    void mapsEmailAlreadyExistsToConflict() {
        ResponseEntity<ProblemDetail> response =
                handler.handleEmailAlreadyExists(new EmailAlreadyExistsException("Email already exists"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getDetail()).isEqualTo("Email already exists");
    }

    @Test
    void mapsValidationFailureToBadRequestWithPerFieldErrors() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "email", "must not be blank"));
        bindingResult.addError(new FieldError("request", "password", "size must be between 10 and 100"));

        Method targetMethod = GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyTarget", Object.class);
        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(new MethodParameter(targetMethod, 0), bindingResult);

        ResponseEntity<ProblemDetail> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) response.getBody().getProperties().get("errors");
        assertThat(errors)
                .containsEntry("email", "must not be blank")
                .containsEntry("password", "size must be between 10 and 100");
    }

    @Test
    void mapsMethodArgumentTypeMismatchToBadRequest_insteadOfGenericServerError() throws NoSuchMethodException {
        Method targetMethod = GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyTarget", Object.class);
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "not-a-number", Integer.class, "page", new MethodParameter(targetMethod, 0), null);

        ResponseEntity<ProblemDetail> response = handler.handleTypeMismatch(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void mapsAccessDeniedToForbiddenWithGenericMessage() {
        ResponseEntity<ProblemDetail> response =
                handler.handleAccessDenied(new AccessDeniedException("insufficient permissions for /internal/secret"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // Must never echo back which internal rule or resource denied the request.
        assertThat(response.getBody().getDetail()).isEqualTo("Access denied");
    }

    @Test
    void mapsNoResourceFoundToNotFound_insteadOfGenericServerError() {
        ResponseEntity<ProblemDetail> response = handler.handleNoResourceFound(
                new NoResourceFoundException(
                        org.springframework.http.HttpMethod.GET, "api/admin/anything", "No static resource"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @SuppressWarnings("unused")
    private void dummyTarget(Object arg) {
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
