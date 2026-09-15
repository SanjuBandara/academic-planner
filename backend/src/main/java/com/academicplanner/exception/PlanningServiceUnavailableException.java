package com.academicplanner.exception;

/**
 * Thrown when the Python CP-SAT planning microservice cannot be reached or
 * returns an unexpected HTTP error code.
 *
 * <p>Maps to HTTP 503 Service Unavailable so the frontend can display a
 * user-friendly "scheduling service is down" message instead of a generic 500.
 */
public class PlanningServiceUnavailableException extends RuntimeException {

    public PlanningServiceUnavailableException(String message) {
        super(message);
    }

    public PlanningServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
