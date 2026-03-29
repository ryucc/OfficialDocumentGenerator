package com.officialpapers.domain;

public record DownloadTarget(
        String downloadUrl,
        String downloadMethod,
        String expiresAt
) {
}
