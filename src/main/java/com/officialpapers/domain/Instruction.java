package com.officialpapers.domain;

public record Instruction(
        String id,
        String title,
        String content,
        String createdAt,
        String updatedAt
) {
}
