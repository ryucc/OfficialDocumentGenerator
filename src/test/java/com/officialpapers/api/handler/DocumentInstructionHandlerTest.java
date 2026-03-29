package com.officialpapers.api.handler;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.officialpapers.api.generated.model.DocumentInstruction;
import com.officialpapers.api.service.DocumentInstructionService;
import com.officialpapers.api.service.InstructionContentStore;
import com.officialpapers.api.service.InstructionMetadataRepository;
import com.officialpapers.domain.CreateInstructionCommand;
import com.officialpapers.domain.InstructionMetadata;
import com.officialpapers.domain.UpdateInstructionCommand;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DocumentInstructionHandlerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-03-29T10:15:30Z"), ZoneOffset.UTC);

    private final InMemoryMetadataRepository metadataRepository = new InMemoryMetadataRepository();
    private final InMemoryContentStore contentStore = new InMemoryContentStore();
    private final DocumentInstructionService service = new DocumentInstructionService(
            metadataRepository,
            contentStore,
            FIXED_CLOCK,
            () -> "11111111-1111-1111-1111-111111111111"
    );
    private final DocumentInstructionHandler handler = new DocumentInstructionHandler(service);
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void createInstructionReturnsCreatedResource() throws Exception {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withBody("""
                        {"title":"Scholarship","content":"Write formally"}
                        """);

        var response = handler.handleRequest(event, null);

        assertEquals(201, response.getStatusCode());
        DocumentInstruction instruction = objectMapper.readValue(response.getBody(), DocumentInstruction.class);
        assertEquals("11111111-1111-1111-1111-111111111111", instruction.getId().toString());
        assertEquals("Scholarship", instruction.getTitle());
        assertEquals("Write formally", instruction.getContent());
        assertEquals(OffsetDateTime.parse("2026-03-29T10:15:30Z"), instruction.getCreatedAt());
        assertEquals(OffsetDateTime.parse("2026-03-29T10:15:30Z"), instruction.getUpdatedAt());
    }

    @Test
    void createInstructionSerializesDateTimesAsStrings() throws Exception {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withBody("""
                        {"title":"Scholarship","content":"Write formally"}
                        """);

        var response = handler.handleRequest(event, null);

        JsonNode payload = objectMapper.readTree(response.getBody());
        assertEquals(true, payload.get("createdAt").isTextual());
        assertEquals(true, payload.get("updatedAt").isTextual());
    }

    @Test
    void listInstructionsReturnsItemsEnvelope() throws Exception {
        metadataRepository.save(new InstructionMetadata(
                "22222222-2222-2222-2222-222222222222",
                "Letter",
                "instructions/22222222-2222-2222-2222-222222222222.txt",
                "2026-03-29T10:15:30Z",
                "2026-03-29T10:15:31Z"
        ));
        contentStore.put("instructions/22222222-2222-2222-2222-222222222222.txt", "Body");

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent().withHttpMethod("GET");

        var response = handler.handleRequest(event, null);

        assertEquals(200, response.getStatusCode());
        Map<?, ?> payload = objectMapper.readValue(response.getBody(), Map.class);
        assertEquals(1, ((List<?>) payload.get("items")).size());
    }

    @Test
    void getInstructionRejectsInvalidUuid() throws Exception {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHttpMethod("GET")
                .withPathParameters(Map.of("instructionId", "not-a-uuid"));

        var response = handler.handleRequest(event, null);

        assertEquals(400, response.getStatusCode());
        Map<?, ?> payload = objectMapper.readValue(response.getBody(), Map.class);
        assertEquals("BAD_REQUEST", payload.get("code"));
    }

    @Test
    void createInstructionRejectsMalformedJson() throws Exception {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withBody("{\"title\":");

        var response = handler.handleRequest(event, null);

        assertEquals(400, response.getStatusCode());
        Map<?, ?> payload = objectMapper.readValue(response.getBody(), Map.class);
        assertEquals("BAD_REQUEST", payload.get("code"));
        assertEquals("Malformed JSON request body", payload.get("message"));
    }

    @Test
    void createInstructionRejectsMissingRequiredFields() throws Exception {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withBody("""
                        {"title":"Only title"}
                        """);

        var response = handler.handleRequest(event, null);

        assertEquals(400, response.getStatusCode());
        Map<?, ?> payload = objectMapper.readValue(response.getBody(), Map.class);
        assertEquals("content is required", payload.get("message"));
    }

    @Test
    void createInstructionRejectsNonStringFields() throws Exception {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withBody("""
                        {"title":123,"content":"Valid"}
                        """);

        var response = handler.handleRequest(event, null);

        assertEquals(400, response.getStatusCode());
        Map<?, ?> payload = objectMapper.readValue(response.getBody(), Map.class);
        assertEquals("title must be a string", payload.get("message"));
    }

    @Test
    void updateInstructionRejectsUnknownFields() throws Exception {
        metadataRepository.save(new InstructionMetadata(
                "44444444-4444-4444-4444-444444444444",
                "Essay",
                "instructions/44444444-4444-4444-4444-444444444444.txt",
                "2026-03-29T10:15:30Z",
                "2026-03-29T10:15:30Z"
        ));
        contentStore.put("instructions/44444444-4444-4444-4444-444444444444.txt", "Original");

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHttpMethod("PUT")
                .withPathParameters(Map.of("instructionId", "44444444-4444-4444-4444-444444444444"))
                .withBody("""
                        {"title":"Updated","unexpected":"value"}
                        """);

        var response = handler.handleRequest(event, null);

        assertEquals(400, response.getStatusCode());
        Map<?, ?> payload = objectMapper.readValue(response.getBody(), Map.class);
        assertEquals("Unknown field: unexpected", payload.get("message"));
    }

    @Test
    void updateInstructionRejectsNonStringFields() throws Exception {
        metadataRepository.save(new InstructionMetadata(
                "55555555-5555-5555-5555-555555555555",
                "Essay",
                "instructions/55555555-5555-5555-5555-555555555555.txt",
                "2026-03-29T10:15:30Z",
                "2026-03-29T10:15:30Z"
        ));
        contentStore.put("instructions/55555555-5555-5555-5555-555555555555.txt", "Original");

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHttpMethod("PUT")
                .withPathParameters(Map.of("instructionId", "55555555-5555-5555-5555-555555555555"))
                .withBody("""
                        {"content":123}
                        """);

        var response = handler.handleRequest(event, null);

        assertEquals(400, response.getStatusCode());
        Map<?, ?> payload = objectMapper.readValue(response.getBody(), Map.class);
        assertEquals("content must be a string", payload.get("message"));
    }

    @Test
    void updateInstructionAppliesProvidedTitle() throws Exception {
        metadataRepository.save(new InstructionMetadata(
                "66666666-6666-6666-6666-666666666666",
                "Essay",
                "instructions/66666666-6666-6666-6666-666666666666.txt",
                "2026-03-29T10:15:30Z",
                "2026-03-29T10:15:30Z"
        ));
        contentStore.put("instructions/66666666-6666-6666-6666-666666666666.txt", "Original");

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHttpMethod("PUT")
                .withPathParameters(Map.of("instructionId", "66666666-6666-6666-6666-666666666666"))
                .withBody("""
                        {"title":"Updated"}
                        """);

        var response = handler.handleRequest(event, null);

        assertEquals(200, response.getStatusCode());
        DocumentInstruction instruction = objectMapper.readValue(response.getBody(), DocumentInstruction.class);
        assertEquals("Updated", instruction.getTitle());
        assertEquals("Original", contentStore.get("instructions/66666666-6666-6666-6666-666666666666.txt"));
    }

    @Test
    void deleteInstructionReturnsNoContent() {
        metadataRepository.save(new InstructionMetadata(
                "33333333-3333-3333-3333-333333333333",
                "Essay",
                "instructions/33333333-3333-3333-3333-333333333333.txt",
                "2026-03-29T10:15:30Z",
                "2026-03-29T10:15:30Z"
        ));
        contentStore.put("instructions/33333333-3333-3333-3333-333333333333.txt", "Original");

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHttpMethod("DELETE")
                .withPathParameters(Map.of("instructionId", "33333333-3333-3333-3333-333333333333"));

        var response = handler.handleRequest(event, null);

        assertEquals(204, response.getStatusCode());
        assertNull(response.getBody());
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
