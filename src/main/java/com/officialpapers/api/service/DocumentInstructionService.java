package com.officialpapers.api.service;

import com.officialpapers.api.model.DocumentInstruction;
import com.officialpapers.api.model.DocumentInstructionCreateRequest;
import com.officialpapers.api.model.DocumentInstructionMetadata;
import com.officialpapers.api.model.DocumentInstructionUpdateRequest;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class DocumentInstructionService {

    private static final Comparator<DocumentInstructionMetadata> UPDATED_AT_DESC =
            Comparator.comparing(DocumentInstructionMetadata::updatedAt).reversed();

    private final InstructionMetadataRepository metadataRepository;
    private final InstructionContentStore contentStore;
    private final Clock clock;
    private final Supplier<String> idSupplier;

    public DocumentInstructionService(
            InstructionMetadataRepository metadataRepository,
            InstructionContentStore contentStore,
            Clock clock,
            Supplier<String> idSupplier
    ) {
        this.metadataRepository = metadataRepository;
        this.contentStore = contentStore;
        this.clock = clock;
        this.idSupplier = idSupplier;
    }

    @Inject
    public DocumentInstructionService(
            InstructionMetadataRepository metadataRepository,
            InstructionContentStore contentStore,
            Clock clock
    ) {
        this(metadataRepository, contentStore, clock, () -> UUID.randomUUID().toString());
    }

    public List<DocumentInstruction> listInstructions() {
        return metadataRepository.findAll().stream()
                .sorted(UPDATED_AT_DESC)
                .map(this::toInstruction)
                .toList();
    }

    public DocumentInstruction getInstruction(String instructionId) {
        return toInstruction(findMetadata(instructionId));
    }

    public DocumentInstruction createInstruction(DocumentInstructionCreateRequest request) {
        validateCreateRequest(request);

        String instructionId = idSupplier.get();
        String timestamp = Instant.now(clock).toString();
        String s3Key = buildS3Key(instructionId);
        DocumentInstructionMetadata metadata = new DocumentInstructionMetadata(
                instructionId,
                request.title(),
                s3Key,
                timestamp,
                timestamp
        );

        contentStore.put(s3Key, request.content());
        try {
            metadataRepository.save(metadata);
        } catch (RuntimeException exception) {
            deleteContentQuietly(s3Key);
            throw exception;
        }

        return new DocumentInstruction(
                metadata.id(),
                metadata.title(),
                request.content(),
                metadata.createdAt(),
                metadata.updatedAt()
        );
    }

    public DocumentInstruction updateInstruction(String instructionId, DocumentInstructionUpdateRequest request) {
        if (request == null) {
            throw new BadRequestException("Request body is required");
        }

        DocumentInstructionMetadata existing = findMetadata(instructionId);
        boolean titleProvided = request.title() != null;
        boolean contentProvided = request.content() != null;

        if (!titleProvided && !contentProvided) {
            return toInstruction(existing);
        }

        String newTitle = titleProvided ? request.title() : existing.title();
        String timestamp = Instant.now(clock).toString();
        DocumentInstructionMetadata updated = new DocumentInstructionMetadata(
                existing.id(),
                newTitle,
                existing.s3Key(),
                existing.createdAt(),
                timestamp
        );

        String currentContent = null;
        if (contentProvided) {
            currentContent = contentStore.get(existing.s3Key());
            contentStore.put(existing.s3Key(), request.content());
        }

        try {
            metadataRepository.save(updated);
        } catch (RuntimeException exception) {
            if (contentProvided && currentContent != null) {
                restoreContentQuietly(existing.s3Key(), currentContent);
            }
            throw exception;
        }

        String responseContent = contentProvided ? request.content() : contentStore.get(existing.s3Key());
        return new DocumentInstruction(
                updated.id(),
                updated.title(),
                responseContent,
                updated.createdAt(),
                updated.updatedAt()
        );
    }

    public void deleteInstruction(String instructionId) {
        DocumentInstructionMetadata existing = findMetadata(instructionId);
        metadataRepository.deleteById(instructionId);
        deleteContentQuietly(existing.s3Key());
    }

    private DocumentInstructionMetadata findMetadata(String instructionId) {
        return metadataRepository.findById(instructionId)
                .orElseThrow(() -> new NotFoundException("Instruction not found"));
    }

    private DocumentInstruction toInstruction(DocumentInstructionMetadata metadata) {
        return new DocumentInstruction(
                metadata.id(),
                metadata.title(),
                contentStore.get(metadata.s3Key()),
                metadata.createdAt(),
                metadata.updatedAt()
        );
    }

    private void validateCreateRequest(DocumentInstructionCreateRequest request) {
        if (request == null) {
            throw new BadRequestException("Request body is required");
        }
        if (request.title() == null) {
            throw new BadRequestException("title is required");
        }
        if (request.content() == null) {
            throw new BadRequestException("content is required");
        }
    }

    private String buildS3Key(String instructionId) {
        return "instructions/" + instructionId + ".txt";
    }

    private void deleteContentQuietly(String s3Key) {
        try {
            contentStore.delete(s3Key);
        } catch (RuntimeException ignored) {
            // Best-effort cleanup keeps failed writes from leaving metadata behind.
        }
    }

    private void restoreContentQuietly(String s3Key, String content) {
        try {
            contentStore.put(s3Key, content);
        } catch (RuntimeException ignored) {
            // Best-effort rollback avoids hiding the original failure.
        }
    }
}
