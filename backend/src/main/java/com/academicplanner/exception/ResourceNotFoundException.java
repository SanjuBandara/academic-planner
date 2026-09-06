package com.academicplanner.exception;

/**
 * Thrown when a requested resource cannot be found.
 * Mapped to 404 Not Found by GlobalExceptionHandler.
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
