package com.officialpapers.api.dto;

import com.officialpapers.api.enums.DocumentFormat;
import com.officialpapers.api.enums.DocumentStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentDto {

    private UUID id;
    private UUID emailEventId;
    private String title;
    private DocumentFormat format;
    private DocumentStatus status;
    private Integer version;
    private String downloadUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
