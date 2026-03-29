package com.officialpapers.api.dto;

import com.officialpapers.api.enums.DocumentFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExportDocumentRequest {

    private DocumentFormat format;
    private String title;
    private Boolean includeChatHistory = true;
}
