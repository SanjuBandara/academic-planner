package com.academicplanner.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Centralized exception handling. Ensures the API never leaks stack traces
 * or internal database details to the frontend, and returns a consistent
 * JSON error shape (see ErrorResponse) with appropriate HTTP status codes.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 400 - Bean Validation failures (e.g. @Valid on RegisterRequest/LoginRequest)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(err ->
                fieldErrors.put(err.getField(), err.getDefaultMessage())
        );

        ErrorResponse body = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "Validation failed",
                fieldErrors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // 409 - Duplicate email on registration
    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailExists(EmailAlreadyExistsException ex) {
        ErrorResponse body = ErrorResponse.of(HttpStatus.CONFLICT.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    // 403 - Ownership violation: student trying to access another student's data
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        ErrorResponse body = ErrorResponse.of(HttpStatus.FORBIDDEN.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    // 404 - Resource not found
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        ErrorResponse body = ErrorResponse.of(HttpStatus.NOT_FOUND.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    // 401 - Invalid login credentials
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        ErrorResponse body = ErrorResponse.of(HttpStatus.UNAUTHORIZED.value(), "Invalid email or password");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    // 401 - Any other Spring Security authentication failure
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex) {
        ErrorResponse body = ErrorResponse.of(HttpStatus.UNAUTHORIZED.value(), "Authentication required");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    // 400 - Illegal argument / bad request thrown intentionally by services
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        ErrorResponse body = ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // 500 - Fallback for anything unexpected. Never leak internal details.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "An unexpected error occurred. Please try again later."
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
