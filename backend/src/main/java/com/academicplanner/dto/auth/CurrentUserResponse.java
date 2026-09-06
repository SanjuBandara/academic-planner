package com.academicplanner.dto.auth;

/**
 * Response for GET /api/auth/me
 */
public record CurrentUserResponse(
        boolean authenticated,
        Long id,
        String email
) {
    public static CurrentUserResponse unauthenticated() {
        return new CurrentUserResponse(false, null, null);
    }
}
