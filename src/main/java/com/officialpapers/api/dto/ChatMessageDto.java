package com.officialpapers.api.dto;

import com.officialpapers.api.enums.ChatChannel;
import com.officialpapers.api.enums.ChatRole;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDto {

    private UUID id;
    private UUID emailEventId;
    private ChatRole role;
    private ChatChannel channel;
    private String content;
    private LocalDateTime createdAt;
}
