package com.officialpapers.api.service;

import com.officialpapers.api.dto.*;
import com.officialpapers.api.entity.Email;
import com.officialpapers.api.entity.EmailEvent;
import com.officialpapers.api.enums.EmailEventStatus;
import com.officialpapers.api.mapper.EmailEventMapper;
import com.officialpapers.api.repository.EmailEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailEventService {

    private final EmailEventRepository emailEventRepository;
    private final EmailEventMapper emailEventMapper;

    @Transactional
    public EmailEventDto ingestEmail(InboundEmailCreateRequest request) {
        // Try to find existing event by correlation key (title-based)
        String correlationKey = generateCorrelationKey(request.getTitle());
        Optional<EmailEvent> existingEvent = emailEventRepository.findByCorrelationKey(correlationKey);

        EmailEvent emailEvent;
        if (existingEvent.isPresent()) {
            // Update existing event
            emailEvent = existingEvent.get();
            emailEvent.setStatus(EmailEventStatus.IN_PROGRESS);
        } else {
            // Create new event
            Email email = emailEventMapper.toEmailEntity(request);
            emailEvent = new EmailEvent();
            emailEvent.setTitle(request.getTitle());
            emailEvent.setStatus(EmailEventStatus.RECEIVED);
            emailEvent.setSourceEmail(email);
            emailEvent.setCorrelationKey(correlationKey);
        }

        emailEvent = emailEventRepository.save(emailEvent);
        return emailEventMapper.toDto(emailEvent);
    }

    @Transactional(readOnly = true)
    public EmailEventListResponse listEmailEvents(String title, EmailEventStatus status, Integer limit, String cursor) {
        int pageSize = limit != null ? limit : 20;
        Pageable pageable = PageRequest.of(0, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<EmailEvent> page;
        if (title != null && status != null) {
            page = emailEventRepository.findByTitleContainingAndStatus(title, status, pageable);
        } else if (title != null) {
            page = emailEventRepository.findByTitleContaining(title, pageable);
        } else if (status != null) {
            page = emailEventRepository.findByStatus(status, pageable);
        } else {
            page = emailEventRepository.findAll(pageable);
        }

        EmailEventListResponse response = new EmailEventListResponse();
        response.setItems(page.getContent().stream()
                .map(emailEventMapper::toSummaryDto)
                .toList());

        if (page.hasNext()) {
            response.setNextCursor(String.valueOf(page.getNumber() + 1));
        }

        return response;
    }

    @Transactional(readOnly = true)
    public Optional<EmailEventDto> getEmailEvent(UUID id) {
        return emailEventRepository.findById(id)
                .map(emailEventMapper::toDto);
    }

    private String generateCorrelationKey(String title) {
        return title.toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}
