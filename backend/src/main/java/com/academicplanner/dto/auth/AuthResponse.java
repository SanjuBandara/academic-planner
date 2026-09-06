package com.academicplanner.dto.auth;

/**
 * Safe response returned after register/login.
 * Never contains password or passwordHash.
 */
public record AuthResponse(
        Long id,
        String email,
        boolean authenticated
) {
}
