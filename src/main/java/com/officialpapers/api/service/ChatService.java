package com.officialpapers.api.service;

import com.officialpapers.api.dto.*;
import com.officialpapers.api.entity.ChatMessage;
import com.officialpapers.api.enums.ChatChannel;
import com.officialpapers.api.enums.ChatRole;
import com.officialpapers.api.mapper.ChatMessageMapper;
import com.officialpapers.api.mapper.DocumentMapper;
import com.officialpapers.api.repository.ChatMessageRepository;
import com.officialpapers.api.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final DocumentRepository documentRepository;
    private final ChatMessageMapper chatMessageMapper;
    private final DocumentMapper documentMapper;

    @Transactional(readOnly = true)
    public ChatHistoryDto getChatHistory(UUID emailEventId) {
        List<ChatMessage> messages = chatMessageRepository.findByEmailEventIdOrderByCreatedAtAsc(emailEventId);
        List<ChatMessageDto> messageDtos = messages.stream()
                .map(chatMessageMapper::toDto)
                .toList();

        List<DocumentSummaryDto> documents = documentRepository.findByEmailEventIdOrderByCreatedAtDesc(emailEventId)
                .stream()
                .map(documentMapper::toSummaryDto)
                .toList();

        return new ChatHistoryDto(emailEventId, messageDtos, documents);
    }

    @Transactional
    public ChatReplyDto createChatMessage(UUID emailEventId, ChatMessageCreateRequest request) {
        // Save user message
        ChatMessage userMessage = new ChatMessage();
        userMessage.setEmailEventId(emailEventId);
        userMessage.setRole(ChatRole.USER);
        userMessage.setChannel(ChatChannel.WEB);
        userMessage.setContent(request.getMessage());
        chatMessageRepository.save(userMessage);

        // Generate assistant reply (placeholder - integrate with AI service later)
        ChatMessage assistantMessage = new ChatMessage();
        assistantMessage.setEmailEventId(emailEventId);
        assistantMessage.setRole(ChatRole.ASSISTANT);
        assistantMessage.setChannel(ChatChannel.WEB);
        assistantMessage.setContent("This is a placeholder response. Integrate with AI service for actual responses.");
        assistantMessage = chatMessageRepository.save(assistantMessage);

        ChatReplyDto reply = new ChatReplyDto();
        reply.setReplyMessage(chatMessageMapper.toDto(assistantMessage));
        reply.setSuggestedActions(List.of());

        return reply;
    }
}
