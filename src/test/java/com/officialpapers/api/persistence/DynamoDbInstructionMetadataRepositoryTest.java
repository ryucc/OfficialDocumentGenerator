package com.officialpapers.api.persistence;

import com.officialpapers.domain.InstructionMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamoDbInstructionMetadataRepositoryTest {

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Test
    void saveWritesInstructionMetadataItem() {
        DynamoDbInstructionMetadataRepository repository =
                new DynamoDbInstructionMetadataRepository(dynamoDbClient, "instruction-metadata");
        InstructionMetadata metadata = new InstructionMetadata(
                "11111111-1111-1111-1111-111111111111",
                "Scholarship",
                "instructions/11111111-1111-1111-1111-111111111111.txt",
                "2026-03-29T10:15:30Z",
                "2026-03-29T10:15:30Z"
        );

        repository.save(metadata);

        ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDbClient).putItem(captor.capture());
        assertEquals("instruction-metadata", captor.getValue().tableName());
        assertEquals("Scholarship", captor.getValue().item().get("title").s());
    }

    @Test
    void findByIdReturnsMappedMetadata() {
        when(dynamoDbClient.getItem(org.mockito.ArgumentMatchers.any(GetItemRequest.class)))
                .thenReturn(GetItemResponse.builder()
                        .item(Map.of(
                                "instructionId", AttributeValue.builder().s("11111111-1111-1111-1111-111111111111").build(),
                                "title", AttributeValue.builder().s("Scholarship").build(),
                                "s3Key", AttributeValue.builder().s("instructions/11111111-1111-1111-1111-111111111111.txt").build(),
                                "createdAt", AttributeValue.builder().s("2026-03-29T10:15:30Z").build(),
                                "updatedAt", AttributeValue.builder().s("2026-03-29T10:15:30Z").build()
                        ))
                        .build());

        DynamoDbInstructionMetadataRepository repository =
                new DynamoDbInstructionMetadataRepository(dynamoDbClient, "instruction-metadata");

        Optional<InstructionMetadata> result =
                repository.findById("11111111-1111-1111-1111-111111111111");

        assertTrue(result.isPresent());
        assertEquals("Scholarship", result.orElseThrow().title());
    }

    @Test
    void findByIdReturnsEmptyWhenItemMissing() {
        when(dynamoDbClient.getItem(org.mockito.ArgumentMatchers.any(GetItemRequest.class)))
                .thenReturn(GetItemResponse.builder().build());

        DynamoDbInstructionMetadataRepository repository =
                new DynamoDbInstructionMetadataRepository(dynamoDbClient, "instruction-metadata");

        Optional<InstructionMetadata> result =
                repository.findById("11111111-1111-1111-1111-111111111111");

        assertEquals(Optional.empty(), result);
    }

    @Test
    void findAllMapsScanResults() {
        when(dynamoDbClient.scan(org.mockito.ArgumentMatchers.any(ScanRequest.class)))
                .thenReturn(ScanResponse.builder()
                        .items(Map.of(
                                        "instructionId", AttributeValue.builder().s("11111111-1111-1111-1111-111111111111").build(),
                                        "title", AttributeValue.builder().s("Scholarship").build(),
                                        "s3Key", AttributeValue.builder().s("instructions/11111111-1111-1111-1111-111111111111.txt").build(),
                                        "createdAt", AttributeValue.builder().s("2026-03-29T10:15:30Z").build(),
                                        "updatedAt", AttributeValue.builder().s("2026-03-29T10:15:30Z").build()
                                ),
                                Map.of(
                                        "instructionId", AttributeValue.builder().s("22222222-2222-2222-2222-222222222222").build(),
                                        "title", AttributeValue.builder().s("Essay").build(),
                                        "s3Key", AttributeValue.builder().s("instructions/22222222-2222-2222-2222-222222222222.txt").build(),
                                        "createdAt", AttributeValue.builder().s("2026-03-29T10:16:30Z").build(),
                                        "updatedAt", AttributeValue.builder().s("2026-03-29T10:16:30Z").build()
                                ))
                        .build());

        DynamoDbInstructionMetadataRepository repository =
                new DynamoDbInstructionMetadataRepository(dynamoDbClient, "instruction-metadata");

        List<InstructionMetadata> result = repository.findAll();

        assertEquals(2, result.size());
        assertEquals(List.of("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222"),
                result.stream().map(InstructionMetadata::id).toList());
    }

    @Test
    void deleteByIdIssuesDeleteRequest() {
        DynamoDbInstructionMetadataRepository repository =
                new DynamoDbInstructionMetadataRepository(dynamoDbClient, "instruction-metadata");

        repository.deleteById("11111111-1111-1111-1111-111111111111");

        ArgumentCaptor<DeleteItemRequest> captor = ArgumentCaptor.forClass(DeleteItemRequest.class);
        verify(dynamoDbClient).deleteItem(captor.capture());
        assertEquals("11111111-1111-1111-1111-111111111111", captor.getValue().key().get("instructionId").s());
    }
}
