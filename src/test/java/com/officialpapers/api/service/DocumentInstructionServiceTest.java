package com.officialpapers.api.service;

import com.officialpapers.domain.CreateInstructionCommand;
import com.officialpapers.domain.Instruction;
import com.officialpapers.domain.InstructionMetadata;
import com.officialpapers.domain.UpdateInstructionCommand;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentInstructionServiceTest {

    private static final Clock INITIAL_CLOCK = Clock.fixed(Instant.parse("2026-03-29T10:15:30Z"), ZoneOffset.UTC);
    private static final Clock UPDATED_CLOCK = Clock.fixed(Instant.parse("2026-03-29T10:16:30Z"), ZoneOffset.UTC);

    @Test
    void createInstructionStoresMetadataAndContent() {
        InMemoryMetadataRepository metadataRepository = new InMemoryMetadataRepository();
        InMemoryContentStore contentStore = new InMemoryContentStore();
        DocumentInstructionService service = new DocumentInstructionService(
                metadataRepository,
                contentStore,
                INITIAL_CLOCK,
                () -> "11111111-1111-1111-1111-111111111111"
        );

        Instruction instruction = service.createInstruction(
                new CreateInstructionCommand("Scholarship", "Use formal tone")
        );

        assertEquals("11111111-1111-1111-1111-111111111111", instruction.id());
        assertEquals("Use formal tone", contentStore.items.get("instructions/11111111-1111-1111-1111-111111111111.txt"));
        assertEquals("Scholarship", metadataRepository.items.get("11111111-1111-1111-1111-111111111111").title());
    }

    @Test
    void listInstructionsReturnsMostRecentlyUpdatedFirst() {
        InMemoryMetadataRepository metadataRepository = new InMemoryMetadataRepository();
        InMemoryContentStore contentStore = new InMemoryContentStore();
        metadataRepository.save(new InstructionMetadata(
                "11111111-1111-1111-1111-111111111111",
                "Older",
                "instructions/11111111-1111-1111-1111-111111111111.txt",
                "2026-03-29T10:15:30Z",
                "2026-03-29T10:15:30Z"
        ));
        metadataRepository.save(new InstructionMetadata(
                "22222222-2222-2222-2222-222222222222",
                "Newer",
                "instructions/22222222-2222-2222-2222-222222222222.txt",
                "2026-03-29T10:15:31Z",
                "2026-03-29T10:15:32Z"
        ));
        contentStore.put("instructions/11111111-1111-1111-1111-111111111111.txt", "Older content");
        contentStore.put("instructions/22222222-2222-2222-2222-222222222222.txt", "Newer content");

        DocumentInstructionService service = new DocumentInstructionService(
                metadataRepository,
                contentStore,
                INITIAL_CLOCK
        );

        List<Instruction> items = service.listInstructions();

        assertEquals(List.of("22222222-2222-2222-2222-222222222222", "11111111-1111-1111-1111-111111111111"),
                items.stream().map(Instruction::id).toList());
    }

    @Test
    void updateInstructionSupportsPartialUpdates() {
        InMemoryMetadataRepository metadataRepository = new InMemoryMetadataRepository();
        InMemoryContentStore contentStore = new InMemoryContentStore();
        metadataRepository.save(new InstructionMetadata(
                "11111111-1111-1111-1111-111111111111",
                "Original",
                "instructions/11111111-1111-1111-1111-111111111111.txt",
                "2026-03-29T10:15:30Z",
                "2026-03-29T10:15:30Z"
        ));
        contentStore.put("instructions/11111111-1111-1111-1111-111111111111.txt", "Original content");

        DocumentInstructionService service = new DocumentInstructionService(
                metadataRepository,
                contentStore,
                UPDATED_CLOCK
        );

        Instruction instruction = service.updateInstruction(
                "11111111-1111-1111-1111-111111111111",
                new UpdateInstructionCommand("Updated", null)
        );

        assertEquals("Updated", instruction.title());
        assertEquals("Original content", instruction.content());
        assertEquals("2026-03-29T10:16:30Z", instruction.updatedAt());
    }

    @Test
    void updateInstructionReturnsCurrentResourceForNoopRequest() {
        InMemoryMetadataRepository metadataRepository = new InMemoryMetadataRepository();
        InMemoryContentStore contentStore = new InMemoryContentStore();
        metadataRepository.save(new InstructionMetadata(
                "11111111-1111-1111-1111-111111111111",
                "Original",
                "instructions/11111111-1111-1111-1111-111111111111.txt",
                "2026-03-29T10:15:30Z",
                "2026-03-29T10:15:30Z"
        ));
        contentStore.put("instructions/11111111-1111-1111-1111-111111111111.txt", "Original content");

        DocumentInstructionService service = new DocumentInstructionService(
                metadataRepository,
                contentStore,
                UPDATED_CLOCK
        );

        Instruction instruction = service.updateInstruction(
                "11111111-1111-1111-1111-111111111111",
                new UpdateInstructionCommand(null, null)
        );

        assertEquals("Original", instruction.title());
        assertEquals("2026-03-29T10:15:30Z", instruction.updatedAt());
    }

    @Test
    void deleteInstructionRemovesMetadataAndContent() {
        InMemoryMetadataRepository metadataRepository = new InMemoryMetadataRepository();
        InMemoryContentStore contentStore = new InMemoryContentStore();
        metadataRepository.save(new InstructionMetadata(
                "11111111-1111-1111-1111-111111111111",
                "Original",
                "instructions/11111111-1111-1111-1111-111111111111.txt",
                "2026-03-29T10:15:30Z",
                "2026-03-29T10:15:30Z"
        ));
        contentStore.put("instructions/11111111-1111-1111-1111-111111111111.txt", "Original content");

        DocumentInstructionService service = new DocumentInstructionService(
                metadataRepository,
                contentStore,
                INITIAL_CLOCK
        );

        service.deleteInstruction("11111111-1111-1111-1111-111111111111");

        assertEquals(Optional.empty(), metadataRepository.findById("11111111-1111-1111-1111-111111111111"));
        assertEquals(null, contentStore.items.get("instructions/11111111-1111-1111-1111-111111111111.txt"));
    }

    @Test
    void getInstructionThrowsWhenMissing() {
        DocumentInstructionService service = new DocumentInstructionService(
                new InMemoryMetadataRepository(),
                new InMemoryContentStore(),
                INITIAL_CLOCK
        );

        assertThrows(NotFoundException.class, () -> service.getInstruction("11111111-1111-1111-1111-111111111111"));
    }

    @Test
    void createInstructionRejectsMissingContent() {
        DocumentInstructionService service = new DocumentInstructionService(
                new InMemoryMetadataRepository(),
                new InMemoryContentStore(),
                INITIAL_CLOCK
        );

        assertThrows(BadRequestException.class,
                () -> service.createInstruction(new CreateInstructionCommand("Title", null)));
    }

    private static class InMemoryMetadataRepository implements InstructionMetadataRepository {

        private final Map<String, InstructionMetadata> items = new HashMap<>();

        @Override
        public void save(InstructionMetadata metadata) {
            items.put(metadata.id(), metadata);
        }

        @Override
        public Optional<InstructionMetadata> findById(String instructionId) {
            return Optional.ofNullable(items.get(instructionId));
        }

        @Override
        public List<InstructionMetadata> findAll() {
            return List.copyOf(items.values());
        }

        @Override
        public void deleteById(String instructionId) {
            items.remove(instructionId);
        }
    }

    private static class InMemoryContentStore implements InstructionContentStore {

        private final Map<String, String> items = new HashMap<>();

        @Override
        public void put(String s3Key, String content) {
            items.put(s3Key, content);
        }

        @Override
        public String get(String s3Key) {
            return items.get(s3Key);
        }

        @Override
        public void delete(String s3Key) {
            items.remove(s3Key);
        }
    }
}
