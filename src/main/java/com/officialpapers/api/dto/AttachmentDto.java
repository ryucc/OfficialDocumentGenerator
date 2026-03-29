package com.officialpapers.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentDto {

    @NotBlank
    private String fileName;

    @NotBlank
    private String mediaType;

    @NotBlank
    private String url;
}
