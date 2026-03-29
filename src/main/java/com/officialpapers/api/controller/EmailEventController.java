package com.officialpapers.api.controller;

import com.officialpapers.api.dto.EmailEventDto;
import com.officialpapers.api.dto.EmailEventListResponse;
import com.officialpapers.api.enums.EmailEventStatus;
import com.officialpapers.api.service.EmailEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/email-events")
@RequiredArgsConstructor
@Tag(name = "Email Events")
public class EmailEventController {

    private final EmailEventService emailEventService;

    @GetMapping
    @Operation(summary = "List email events")
    public ResponseEntity<EmailEventListResponse> listEmailEvents(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) EmailEventStatus status,
            @RequestParam(required = false, defaultValue = "20") Integer limit,
            @RequestParam(required = false) String cursor) {
        EmailEventListResponse response = emailEventService.listEmailEvents(title, status, limit, cursor);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{emailEventId}")
    @Operation(summary = "Get an email event")
    public ResponseEntity<EmailEventDto> getEmailEvent(@PathVariable UUID emailEventId) {
        return emailEventService.getEmailEvent(emailEventId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
