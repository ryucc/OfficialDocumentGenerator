package com.officialpapers.api.service;

import com.officialpapers.api.model.DocumentInstructionMetadata;

import java.util.List;
import java.util.Optional;

public interface InstructionMetadataRepository {

    void save(DocumentInstructionMetadata metadata);

    Optional<DocumentInstructionMetadata> findById(String instructionId);

    List<DocumentInstructionMetadata> findAll();

    void deleteById(String instructionId);
}
