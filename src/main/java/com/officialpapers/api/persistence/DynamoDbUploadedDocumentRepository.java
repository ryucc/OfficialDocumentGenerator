package com.officialpapers.api.persistence;

import com.officialpapers.api.service.UploadedDocumentRepository;
import com.officialpapers.domain.UploadedDocument;
import com.officialpapers.domain.UploadedDocumentStatus;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DynamoDbUploadedDocumentRepository implements UploadedDocumentRepository {

    private static final String DOCUMENT_ID = "documentId";
    private static final String FILENAME = "filename";
    private static final String CONTENT_TYPE = "contentType";
    private static final String SIZE_BYTES = "sizeBytes";
    private static final String STATUS = "status";
    private static final String SOURCE_OBJECT_KEY = "sourceObjectKey";
    private static final String TEXT_OBJECT_KEY = "textObjectKey";
    private static final String CREATED_AT = "createdAt";
    private static final String UPDATED_AT = "updatedAt";

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    @Inject
    public DynamoDbUploadedDocumentRepository(
            DynamoDbClient dynamoDbClient,
            @Named("uploadedDocumentMetadataTable") String tableName
    ) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
    }

    @Override
    public void save(UploadedDocument document) {
        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(toItem(document))
                .build());
    }

    @Override
    public Optional<UploadedDocument> findById(String documentId) {
        Map<String, AttributeValue> item = dynamoDbClient.getItem(GetItemRequest.builder()
                        .tableName(tableName)
                        .key(Map.of(DOCUMENT_ID, AttributeValue.builder().s(documentId).build()))
                        .consistentRead(true)
                        .build())
                .item();

        if (item == null || item.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(fromItem(item));
    }

    @Override
    public List<UploadedDocument> findAll() {
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
    public void deleteById(String documentId) {
        dynamoDbClient.deleteItem(DeleteItemRequest.builder()
                .tableName(tableName)
                .key(Map.of(DOCUMENT_ID, AttributeValue.builder().s(documentId).build()))
                .build());
    }

    private Map<String, AttributeValue> toItem(UploadedDocument document) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put(DOCUMENT_ID, AttributeValue.builder().s(document.id()).build());
        item.put(FILENAME, AttributeValue.builder().s(document.filename()).build());
        item.put(CONTENT_TYPE, AttributeValue.builder().s(document.contentType()).build());
        item.put(STATUS, AttributeValue.builder().s(document.status().name()).build());
        item.put(SOURCE_OBJECT_KEY, AttributeValue.builder().s(document.sourceObjectKey()).build());
        item.put(CREATED_AT, AttributeValue.builder().s(document.createdAt()).build());
        item.put(UPDATED_AT, AttributeValue.builder().s(document.updatedAt()).build());
        if (document.sizeBytes() != null) {
            item.put(SIZE_BYTES, AttributeValue.builder().n(document.sizeBytes().toString()).build());
        }
        if (document.textObjectKey() != null) {
            item.put(TEXT_OBJECT_KEY, AttributeValue.builder().s(document.textObjectKey()).build());
        }
        return item;
    }

    private UploadedDocument fromItem(Map<String, AttributeValue> item) {
        return new UploadedDocument(
                item.get(DOCUMENT_ID).s(),
                item.get(FILENAME).s(),
                item.get(CONTENT_TYPE).s(),
                item.containsKey(SIZE_BYTES) ? Long.valueOf(item.get(SIZE_BYTES).n()) : null,
                UploadedDocumentStatus.valueOf(item.get(STATUS).s()),
                item.get(SOURCE_OBJECT_KEY).s(),
                item.containsKey(TEXT_OBJECT_KEY) ? item.get(TEXT_OBJECT_KEY).s() : null,
                item.get(CREATED_AT).s(),
                item.get(UPDATED_AT).s()
        );
    }
}
