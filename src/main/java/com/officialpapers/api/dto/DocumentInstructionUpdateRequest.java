package com.officialpapers.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentInstructionUpdateRequest {

    private String title;
    private String content;
}
