package com.academicplanner.exception;

/**
 * Thrown when an authenticated student attempts to access or modify a resource
 * that belongs to a different student. Results in HTTP 403 Forbidden.
 */
public class AccessDeniedException extends RuntimeException {

    public AccessDeniedException() {
        super("You do not have permission to access this resource.");
    }

    public AccessDeniedException(String message) {
        super(message);
    }
}
