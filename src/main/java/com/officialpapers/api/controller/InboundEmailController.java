package com.officialpapers.api.controller;

import com.officialpapers.api.dto.EmailEventDto;
import com.officialpapers.api.dto.InboundEmailCreateRequest;
import com.officialpapers.api.service.EmailEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inbound/emails")
@RequiredArgsConstructor
@Tag(name = "Inbound Email")
public class InboundEmailController {

    private final EmailEventService emailEventService;

    @PostMapping
    @Operation(summary = "Ingest a forwarded email")
    public ResponseEntity<EmailEventDto> ingestEmail(@Valid @RequestBody InboundEmailCreateRequest request) {
        EmailEventDto event = emailEventService.ingestEmail(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(event);
    }
}
