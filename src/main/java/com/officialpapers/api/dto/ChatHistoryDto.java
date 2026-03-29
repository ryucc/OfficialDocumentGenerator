package com.officialpapers.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatHistoryDto {

    private UUID emailEventId;
    private List<ChatMessageDto> messages;
    private List<DocumentSummaryDto> historicDocuments;
}
