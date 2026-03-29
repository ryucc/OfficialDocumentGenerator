package com.officialpapers.api.model;

public record DocumentInstructionMetadata(
        String id,
        String title,
        String s3Key,
        String createdAt,
        String updatedAt
) {
}
