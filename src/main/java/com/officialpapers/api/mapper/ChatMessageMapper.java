package com.officialpapers.api.mapper;

import com.officialpapers.api.dto.ChatMessageDto;
import com.officialpapers.api.entity.ChatMessage;
import org.springframework.stereotype.Component;

@Component
public class ChatMessageMapper {

    public ChatMessageDto toDto(ChatMessage message) {
        if (message == null) return null;
        return new ChatMessageDto(
                message.getId(),
                message.getEmailEventId(),
                message.getRole(),
                message.getChannel(),
                message.getContent(),
                message.getCreatedAt()
        );
    }
}
