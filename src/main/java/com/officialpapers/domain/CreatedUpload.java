package com.officialpapers.domain;

public record CreatedUpload(
        UploadedDocument document,
        UploadTarget upload
) {
}
