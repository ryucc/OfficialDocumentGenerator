package com.officialpapers.api.mapper;

import com.officialpapers.api.dto.*;
import com.officialpapers.api.entity.Email;
import com.officialpapers.api.entity.EmailEvent;
import org.springframework.stereotype.Component;

@Component
public class EmailEventMapper {

    public EmailDto toEmailDto(Email email) {
        if (email == null) return null;
        return new EmailDto(
                email.getId(),
                email.getSender(),
                email.getTitle(),
                email.getContent(),
                email.getDate()
        );
    }

    public EmailEventSummaryDto toSummaryDto(EmailEvent event) {
        if (event == null) return null;
        return new EmailEventSummaryDto(
                event.getId(),
                event.getTitle(),
                event.getStatus(),
                event.getCreatedAt(),
                event.getUpdatedAt(),
                event.getLatestDocumentId()
        );
    }

    public EmailEventDto toDto(EmailEvent event) {
        if (event == null) return null;
        return new EmailEventDto(
                event.getId(),
                event.getTitle(),
                event.getStatus(),
                event.getCreatedAt(),
                event.getUpdatedAt(),
                event.getLatestDocumentId(),
                toEmailDto(event.getSourceEmail()),
                event.getCorrelationKey()
        );
    }

    public Email toEmailEntity(InboundEmailCreateRequest request) {
        if (request == null) return null;
        Email email = new Email();
        email.setSender(request.getSender());
        email.setTitle(request.getTitle());
        email.setContent(request.getContent());
        email.setDate(request.getDate());
        email.setExternalMessageId(request.getExternalMessageId());
        return email;
    }
}
