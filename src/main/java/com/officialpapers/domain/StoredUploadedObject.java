package com.officialpapers.domain;

public record StoredUploadedObject(
        String objectKey,
        String contentType,
        long sizeBytes
) {
}
