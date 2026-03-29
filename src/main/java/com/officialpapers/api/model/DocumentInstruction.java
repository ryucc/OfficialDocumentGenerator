package com.officialpapers.api.model;

public record DocumentInstruction(
        String id,
        String title,
        String content,
        String createdAt,
        String updatedAt
) {
}
