package com.alon.bookstore.auth;

/**
 * A genuine conflict - the email is already taken - as opposed to invalid input.
 * Kept distinct from {@link IllegalArgumentException} so GlobalExceptionHandler can
 * map the two to different HTTP statuses (409 vs 400).
 */
public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException(String message) {
        super(message);
    }
}
