package com.officialpapers.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResult(
        AuthTokens tokens,
        AuthChallenge challenge
) {

    public static LoginResult authenticated(AuthTokens tokens) {
        return new LoginResult(tokens, null);
    }

    public static LoginResult challenged(AuthChallenge challenge) {
        return new LoginResult(null, challenge);
    }
}
