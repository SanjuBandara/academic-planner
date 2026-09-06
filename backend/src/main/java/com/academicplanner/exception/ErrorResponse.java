package com.academicplanner.exception;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Standardized error response body returned by the API for all error conditions.
 *
 * Example JSON response:
 * {
 *   "timestamp": "2026-09-01T10:00:00",
 *   "status": 400,
 *   "message": "Validation failed",
 *   "errors": {
 *     "email": "Invalid email address"
 *   }
 * }
 *
 * The "errors" field is only present for validation failures.
 * Stack traces and internal DB details are NEVER exposed.
 */
public class ErrorResponse {

    private LocalDateTime timestamp;
    private int status;
    private String message;
    private Map<String, String> errors;

    // ─── Constructors ─────────────────────────────────────────────────────────

    public ErrorResponse() {
        this.timestamp = LocalDateTime.now();
    }

    public ErrorResponse(int status, String message) {
        this.timestamp = LocalDateTime.now();
        this.status = status;
        this.message = message;
    }

    public ErrorResponse(int status, String message, Map<String, String> errors) {
        this.timestamp = LocalDateTime.now();
        this.status = status;
        this.message = message;
        this.errors = errors;
    }

    public static ErrorResponse of(int status, String message) {
        return new ErrorResponse(status, message);
    }

    public static ErrorResponse of(int status, String message, Map<String, String> errors) {
        return new ErrorResponse(status, message, errors);
    }

    // ─── Getters & Setters ────────────────────────────────────────────────────

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Map<String, String> getErrors() {
        return errors;
    }

    public void setErrors(Map<String, String> errors) {
        this.errors = errors;
    }
}
