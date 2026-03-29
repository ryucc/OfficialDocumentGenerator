package com.officialpapers.api.dto;

import com.officialpapers.api.enums.EmailEventStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailEventSummaryDto {

    private UUID id;
    private String title;
    private EmailEventStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private UUID latestDocumentId;
}
