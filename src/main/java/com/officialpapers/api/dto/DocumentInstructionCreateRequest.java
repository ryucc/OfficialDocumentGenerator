package com.officialpapers.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentInstructionCreateRequest {

    @NotBlank
    private String title;

    @NotBlank
    private String content;
}
