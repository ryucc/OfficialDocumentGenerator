package com.officialpapers.domain;

public record AuthChallenge(
        String challengeName,
        String session,
        String email
) {
}
