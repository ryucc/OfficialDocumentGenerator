package com.officialpapers.domain;

public record AuthenticatedUser(
        String userId,
        String email,
        boolean emailVerified
) {
}
