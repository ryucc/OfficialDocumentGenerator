package com.officialpapers.domain;

public record UploadedDocument(
        String id,
        String filename,
        String contentType,
        Long sizeBytes,
        UploadedDocumentStatus status,
        String sourceObjectKey,
        String createdAt,
        String updatedAt
) {
}
