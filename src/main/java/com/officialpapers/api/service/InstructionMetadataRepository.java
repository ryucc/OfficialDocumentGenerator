package com.officialpapers.api.service;

import com.officialpapers.domain.InstructionMetadata;

import java.util.List;
import java.util.Optional;

public interface InstructionMetadataRepository {

    void save(InstructionMetadata metadata);

    Optional<InstructionMetadata> findById(String instructionId);

    List<InstructionMetadata> findAll();

    void deleteById(String instructionId);
}
