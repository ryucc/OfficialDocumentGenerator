package com.officialpapers.domain;

import java.util.Map;

public record UploadTarget(
        String uploadUrl,
        String uploadMethod,
        Map<String, String> uploadHeaders,
        String expiresAt
) {
}
