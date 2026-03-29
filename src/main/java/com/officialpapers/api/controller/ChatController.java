package com.officialpapers.api.controller;

import com.officialpapers.api.dto.ChatHistoryDto;
import com.officialpapers.api.dto.ChatMessageCreateRequest;
import com.officialpapers.api.dto.ChatReplyDto;
import com.officialpapers.api.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/email-events")
@RequiredArgsConstructor
@Tag(name = "Chat")
public class ChatController {

    private final ChatService chatService;

    @GetMapping("/{emailEventId}/chat-history")
    @Operation(summary = "Get chat history for an email event")
    public ResponseEntity<ChatHistoryDto> getChatHistory(@PathVariable UUID emailEventId) {
        ChatHistoryDto history = chatService.getChatHistory(emailEventId);
        return ResponseEntity.ok(history);
    }

    @PostMapping("/{emailEventId}/messages")
    @Operation(summary = "Send a chat message within an email event")
    public ResponseEntity<ChatReplyDto> createChatMessage(
            @PathVariable UUID emailEventId,
            @Valid @RequestBody ChatMessageCreateRequest request) {
        ChatReplyDto reply = chatService.createChatMessage(emailEventId, request);
        return ResponseEntity.ok(reply);
    }
}
