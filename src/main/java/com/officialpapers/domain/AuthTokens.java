package com.officialpapers.domain;

public record AuthTokens(
        String accessToken,
        String idToken,
        String refreshToken,
        String tokenType,
        Integer expiresIn
) {
}
