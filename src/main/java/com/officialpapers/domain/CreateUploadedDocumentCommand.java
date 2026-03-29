package com.officialpapers.domain;

public record CreateUploadedDocumentCommand(
        String filename,
        String contentType,
        Long sizeBytes
) {
}
