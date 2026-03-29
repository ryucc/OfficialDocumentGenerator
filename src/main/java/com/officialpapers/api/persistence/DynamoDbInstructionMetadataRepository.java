package com.officialpapers.api.persistence;

import com.officialpapers.api.model.DocumentInstructionMetadata;
import com.officialpapers.api.service.InstructionMetadataRepository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DynamoDbInstructionMetadataRepository implements InstructionMetadataRepository {

    private static final String INSTRUCTION_ID = "instructionId";
    private static final String TITLE = "title";
    private static final String S3_KEY = "s3Key";
    private static final String CREATED_AT = "createdAt";
    private static final String UPDATED_AT = "updatedAt";

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    public DynamoDbInstructionMetadataRepository(DynamoDbClient dynamoDbClient, String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
    }

    @Override
    public void save(DocumentInstructionMetadata metadata) {
        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(toItem(metadata))
                .build());
    }

    @Override
    public Optional<DocumentInstructionMetadata> findById(String instructionId) {
        Map<String, AttributeValue> item = dynamoDbClient.getItem(GetItemRequest.builder()
                        .tableName(tableName)
                        .key(Map.of(INSTRUCTION_ID, AttributeValue.builder().s(instructionId).build()))
                        .consistentRead(true)
                        .build())
                .item();

        if (item == null || item.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(fromItem(item));
    }

    @Override
    public List<DocumentInstructionMetadata> findAll() {
        ScanResponse response = dynamoDbClient.scan(ScanRequest.builder()
                .tableName(tableName)
                .build());

        if (response.items() == null || response.items().isEmpty()) {
            return List.of();
        }

        return response.items().stream()
                .map(this::fromItem)
                .toList();
    }

    @Override
    public void deleteById(String instructionId) {
        dynamoDbClient.deleteItem(DeleteItemRequest.builder()
                .tableName(tableName)
                .key(Map.of(INSTRUCTION_ID, AttributeValue.builder().s(instructionId).build()))
                .build());
    }

    private Map<String, AttributeValue> toItem(DocumentInstructionMetadata metadata) {
        return Map.of(
                INSTRUCTION_ID, AttributeValue.builder().s(metadata.id()).build(),
                TITLE, AttributeValue.builder().s(metadata.title()).build(),
                S3_KEY, AttributeValue.builder().s(metadata.s3Key()).build(),
                CREATED_AT, AttributeValue.builder().s(metadata.createdAt()).build(),
                UPDATED_AT, AttributeValue.builder().s(metadata.updatedAt()).build()
        );
    }

    private DocumentInstructionMetadata fromItem(Map<String, AttributeValue> item) {
        return new DocumentInstructionMetadata(
                item.get(INSTRUCTION_ID).s(),
                item.get(TITLE).s(),
                item.get(S3_KEY).s(),
                item.get(CREATED_AT).s(),
                item.get(UPDATED_AT).s()
        );
    }
}
