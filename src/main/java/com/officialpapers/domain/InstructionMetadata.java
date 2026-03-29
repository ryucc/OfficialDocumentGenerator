package com.officialpapers.domain;

public record InstructionMetadata(
        String id,
        String title,
        String s3Key,
        String createdAt,
        String updatedAt
) {
}
