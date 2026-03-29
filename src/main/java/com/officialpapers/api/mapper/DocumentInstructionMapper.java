package com.officialpapers.api.mapper;

import com.officialpapers.api.dto.DocumentInstructionCreateRequest;
import com.officialpapers.api.dto.DocumentInstructionDto;
import com.officialpapers.api.entity.DocumentInstruction;
import org.springframework.stereotype.Component;

@Component
public class DocumentInstructionMapper {

    public DocumentInstructionDto toDto(DocumentInstruction instruction) {
        if (instruction == null) return null;
        return new DocumentInstructionDto(
                instruction.getId(),
                instruction.getTitle(),
                instruction.getContent(),
                instruction.getCreatedAt(),
                instruction.getUpdatedAt()
        );
    }

    public DocumentInstruction toEntity(DocumentInstructionCreateRequest request) {
        if (request == null) return null;
        DocumentInstruction instruction = new DocumentInstruction();
        instruction.setTitle(request.getTitle());
        instruction.setContent(request.getContent());
        return instruction;
    }
}
