package com.officialpapers.api.controller;

import com.officialpapers.api.dto.DocumentInstructionCreateRequest;
import com.officialpapers.api.dto.DocumentInstructionDto;
import com.officialpapers.api.dto.DocumentInstructionUpdateRequest;
import com.officialpapers.api.service.DocumentInstructionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/document-instructions")
@RequiredArgsConstructor
@Tag(name = "Document Instructions")
public class DocumentInstructionController {

    private final DocumentInstructionService instructionService;

    @GetMapping
    @Operation(summary = "List document-writing instructions")
    public ResponseEntity<Map<String, List<DocumentInstructionDto>>> listInstructions() {
        List<DocumentInstructionDto> instructions = instructionService.listInstructions();
        return ResponseEntity.ok(Map.of("items", instructions));
    }

    @PostMapping
    @Operation(summary = "Create a document-writing instruction")
    public ResponseEntity<DocumentInstructionDto> createInstruction(
            @Valid @RequestBody DocumentInstructionCreateRequest request) {
        DocumentInstructionDto instruction = instructionService.createInstruction(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(instruction);
    }

    @GetMapping("/{instructionId}")
    @Operation(summary = "Get a document-writing instruction")
    public ResponseEntity<DocumentInstructionDto> getInstruction(@PathVariable UUID instructionId) {
        return instructionService.getInstruction(instructionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{instructionId}")
    @Operation(summary = "Update a document-writing instruction")
    public ResponseEntity<DocumentInstructionDto> updateInstruction(
            @PathVariable UUID instructionId,
            @Valid @RequestBody DocumentInstructionUpdateRequest request) {
        return instructionService.updateInstruction(instructionId, request)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{instructionId}")
    @Operation(summary = "Delete a document-writing instruction")
    public ResponseEntity<Void> deleteInstruction(@PathVariable UUID instructionId) {
        boolean deleted = instructionService.deleteInstruction(instructionId);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
