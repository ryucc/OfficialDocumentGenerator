package com.officialpapers.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/document-instructions")
@Tag(name = "Document Instructions")
public class DocumentInstructionController {

    @GetMapping
    @Operation(summary = "List document-writing instructions")
    public ResponseEntity<String> listInstructions() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body("Not Implemented");
    }

    @PostMapping
    @Operation(summary = "Create a document-writing instruction")
    public ResponseEntity<String> createInstruction(@RequestBody String request) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body("Not Implemented");
    }

    @GetMapping("/{instructionId}")
    @Operation(summary = "Get a document-writing instruction")
    public ResponseEntity<String> getInstruction(@PathVariable UUID instructionId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body("Not Implemented");
    }

    @PutMapping("/{instructionId}")
    @Operation(summary = "Update a document-writing instruction")
    public ResponseEntity<String> updateInstruction(
            @PathVariable UUID instructionId,
            @RequestBody String request) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body("Not Implemented");
    }

    @DeleteMapping("/{instructionId}")
    @Operation(summary = "Delete a document-writing instruction")
    public ResponseEntity<String> deleteInstruction(@PathVariable UUID instructionId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body("Not Implemented");
    }
}
