package com.officialpapers.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatReplyDto {

    private ChatMessageDto replyMessage;
    private List<SuggestedActionDto> suggestedActions;
}
