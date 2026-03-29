package com.officialpapers.api.service;

import com.officialpapers.api.dto.DocumentInstructionCreateRequest;
import com.officialpapers.api.dto.DocumentInstructionDto;
import com.officialpapers.api.dto.DocumentInstructionUpdateRequest;
import com.officialpapers.api.entity.DocumentInstruction;
import com.officialpapers.api.mapper.DocumentInstructionMapper;
import com.officialpapers.api.repository.DocumentInstructionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentInstructionService {

    private final DocumentInstructionRepository instructionRepository;
    private final DocumentInstructionMapper instructionMapper;

    @Transactional(readOnly = true)
    public List<DocumentInstructionDto> listInstructions() {
        return instructionRepository.findAll().stream()
                .map(instructionMapper::toDto)
                .toList();
    }

    @Transactional
    public DocumentInstructionDto createInstruction(DocumentInstructionCreateRequest request) {
        DocumentInstruction instruction = instructionMapper.toEntity(request);
        instruction = instructionRepository.save(instruction);
        return instructionMapper.toDto(instruction);
    }

    @Transactional(readOnly = true)
    public Optional<DocumentInstructionDto> getInstruction(UUID id) {
        return instructionRepository.findById(id)
                .map(instructionMapper::toDto);
    }

    @Transactional
    public Optional<DocumentInstructionDto> updateInstruction(UUID id, DocumentInstructionUpdateRequest request) {
        return instructionRepository.findById(id)
                .map(instruction -> {
                    if (request.getTitle() != null) {
                        instruction.setTitle(request.getTitle());
                    }
                    if (request.getContent() != null) {
                        instruction.setContent(request.getContent());
                    }
                    instruction = instructionRepository.save(instruction);
                    return instructionMapper.toDto(instruction);
                });
    }

    @Transactional
    public boolean deleteInstruction(UUID id) {
        if (instructionRepository.existsById(id)) {
            instructionRepository.deleteById(id);
            return true;
        }
        return false;
    }
}
