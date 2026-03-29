package com.officialpapers.api.controller;

import com.officialpapers.api.dto.DocumentDto;
import com.officialpapers.api.dto.DocumentExportResponse;
import com.officialpapers.api.dto.ExportDocumentRequest;
import com.officialpapers.api.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Documents")
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping("/email-events/{emailEventId}/export")
    @Operation(summary = "Export a document for an email event")
    public ResponseEntity<DocumentExportResponse> exportDocument(
            @PathVariable UUID emailEventId,
            @RequestBody(required = false) ExportDocumentRequest request) {
        if (request == null) {
            request = new ExportDocumentRequest();
        }
        DocumentExportResponse response = documentService.exportDocument(emailEventId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/documents/{documentId}")
    @Operation(summary = "Get document metadata")
    public ResponseEntity<DocumentDto> getDocument(@PathVariable UUID documentId) {
        return documentService.getDocument(documentId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/documents/{documentId}/download")
    @Operation(summary = "Download an exported document")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable UUID documentId) {
        return documentService.downloadDocument(documentId)
                .map(content -> {
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_PDF);
                    headers.setContentDispositionFormData("attachment", "document.pdf");
                    return ResponseEntity.ok().headers(headers).body(content);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
