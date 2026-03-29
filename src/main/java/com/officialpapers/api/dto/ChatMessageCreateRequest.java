package com.officialpapers.api.dto;

import com.officialpapers.api.enums.ChatIntent;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageCreateRequest {

    @NotBlank
    private String message;

    private ChatIntent intent;
    private UUID relatedDocumentId;
}
