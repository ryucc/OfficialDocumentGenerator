package com.officialpapers.api.handler;

import com.officialpapers.api.generated.model.ApiError;
import com.officialpapers.api.generated.model.DocumentInstruction;
import com.officialpapers.api.generated.model.DocumentInstructionCreateRequest;
import com.officialpapers.api.generated.model.DocumentInstructionListResponse;
import com.officialpapers.api.generated.model.DocumentInstructionUpdateRequest;
import com.officialpapers.domain.CreateInstructionCommand;
import com.officialpapers.domain.Instruction;
import com.officialpapers.domain.UpdateInstructionCommand;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Singleton
public class DocumentInstructionApiMapper {

    @Inject
    public DocumentInstructionApiMapper() {
    }

    public CreateInstructionCommand toDomain(DocumentInstructionCreateRequest request) {
        return new CreateInstructionCommand(request.getTitle(), request.getContent());
    }

    public UpdateInstructionCommand toDomain(DocumentInstructionUpdateRequest request) {
        return new UpdateInstructionCommand(request.getTitle(), request.getContent());
    }

    public DocumentInstruction toApi(Instruction instruction) {
        return new DocumentInstruction()
                .id(UUID.fromString(instruction.id()))
                .title(instruction.title())
                .content(instruction.content())
                .createdAt(OffsetDateTime.parse(instruction.createdAt()))
                .updatedAt(OffsetDateTime.parse(instruction.updatedAt()));
    }

    public DocumentInstructionListResponse toApiList(List<Instruction> instructions) {
        return new DocumentInstructionListResponse().items(
                instructions.stream()
                        .map(this::toApi)
                        .toList()
        );
    }

    public ApiError toApiError(String code, String message) {
        return new ApiError()
                .code(code)
                .message(message);
    }
}
