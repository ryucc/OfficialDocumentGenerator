package com.officialpapers.api.persistence;

import com.officialpapers.domain.UploadedDocument;
import com.officialpapers.domain.UploadedDocumentStatus;
import org.junit.jupiter.api.BeforeEach;
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
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamoDbUploadedDocumentRepositoryTest {

    @Mock
    private DynamoDbClient dynamoDbClient;

    private DynamoDbUploadedDocumentRepository repository;

    @BeforeEach
    void setUp() {
        repository = new DynamoDbUploadedDocumentRepository(dynamoDbClient, "sample-documents-test");
    }

    @Test
    void saveWritesAllDocumentFields() {
        repository.save(document());

        ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDbClient).putItem(captor.capture());

        PutItemRequest request = captor.getValue();
        assertEquals("sample-documents-test", request.tableName());
        assertEquals("user-123", request.item().get("ownerUserId").s());
        assertEquals("11111111-1111-1111-1111-111111111111", request.item().get("documentId").s());
        assertEquals("memo.pdf", request.item().get("filename").s());
        assertEquals("sample-documents/user-123/11111111-1111-1111-1111-111111111111/memo.pdf", request.item().get("sourceObjectKey").s());
    }

    @Test
    void findByIdReturnsMappedDocument() {
        when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(GetItemResponse.builder()
                .item(item())
                .build());

        UploadedDocument document = repository.findById("user-123", "11111111-1111-1111-1111-111111111111").orElseThrow();

        assertEquals("user-123", document.ownerUserId());
        assertEquals(UploadedDocumentStatus.AVAILABLE, document.status());
    }

    @Test
    void findByIdReturnsEmptyWhenItemMissing() {
        when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(GetItemResponse.builder().build());

        assertTrue(repository.findById("user-123", "11111111-1111-1111-1111-111111111111").isEmpty());
    }

    @Test
    void findAllByOwnerUserIdQueriesConfiguredTable() {
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(QueryResponse.builder()
                .items(item(), item())
                .build());

        List<UploadedDocument> documents = repository.findAllByOwnerUserId("user-123");

        assertEquals(2, documents.size());

        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());
        assertEquals("sample-documents-test", captor.getValue().tableName());
        assertEquals("ownerUserId = :ownerUserId", captor.getValue().keyConditionExpression());
        assertEquals("user-123", captor.getValue().expressionAttributeValues().get(":ownerUserId").s());
        assertEquals(true, captor.getValue().consistentRead());
    }

    @Test
    void findAllByOwnerUserIdReadsAllPages() {
        Map<String, AttributeValue> pageToken = Map.of(
                "ownerUserId", AttributeValue.builder().s("user-123").build(),
                "documentId", AttributeValue.builder().s("11111111-1111-1111-1111-111111111111").build()
        );
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(
                QueryResponse.builder()
                        .items(item())
                        .lastEvaluatedKey(pageToken)
                        .build(),
                QueryResponse.builder()
                        .items(secondItem())
                        .build()
        );

        List<UploadedDocument> documents = repository.findAllByOwnerUserId("user-123");

        assertEquals(List.of(
                "11111111-1111-1111-1111-111111111111",
                "22222222-2222-2222-2222-222222222222"
        ), documents.stream().map(UploadedDocument::id).toList());

        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient, times(2)).query(captor.capture());
        assertEquals(pageToken, captor.getAllValues().get(1).exclusiveStartKey());
    }

    @Test
    void deleteByIdDeletesCompositePrimaryKey() {
        repository.deleteById("user-123", "11111111-1111-1111-1111-111111111111");

        ArgumentCaptor<DeleteItemRequest> captor = ArgumentCaptor.forClass(DeleteItemRequest.class);
        verify(dynamoDbClient).deleteItem(captor.capture());

        DeleteItemRequest request = captor.getValue();
        assertEquals("sample-documents-test", request.tableName());
        assertEquals("user-123", request.key().get("ownerUserId").s());
        assertEquals("11111111-1111-1111-1111-111111111111", request.key().get("documentId").s());
    }

    private static UploadedDocument document() {
        return new UploadedDocument(
                "11111111-1111-1111-1111-111111111111",
                "user-123",
                "memo.pdf",
                "application/pdf",
                512L,
                UploadedDocumentStatus.AVAILABLE,
                "sample-documents/user-123/11111111-1111-1111-1111-111111111111/memo.pdf",
                "2026-03-29T06:00:00Z",
                "2026-03-29T06:10:00Z"
        );
    }

    private static Map<String, AttributeValue> item() {
        return Map.of(
                "ownerUserId", AttributeValue.builder().s("user-123").build(),
                "documentId", AttributeValue.builder().s("11111111-1111-1111-1111-111111111111").build(),
                "filename", AttributeValue.builder().s("memo.pdf").build(),
                "contentType", AttributeValue.builder().s("application/pdf").build(),
                "sizeBytes", AttributeValue.builder().n("512").build(),
                "status", AttributeValue.builder().s("AVAILABLE").build(),
                "sourceObjectKey", AttributeValue.builder().s("sample-documents/user-123/11111111-1111-1111-1111-111111111111/memo.pdf").build(),
                "createdAt", AttributeValue.builder().s("2026-03-29T06:00:00Z").build(),
                "updatedAt", AttributeValue.builder().s("2026-03-29T06:10:00Z").build()
        );
    }

    private static Map<String, AttributeValue> secondItem() {
        return Map.of(
                "ownerUserId", AttributeValue.builder().s("user-123").build(),
                "documentId", AttributeValue.builder().s("22222222-2222-2222-2222-222222222222").build(),
                "filename", AttributeValue.builder().s("memo-2.pdf").build(),
                "contentType", AttributeValue.builder().s("application/pdf").build(),
                "sizeBytes", AttributeValue.builder().n("256").build(),
                "status", AttributeValue.builder().s("PENDING_UPLOAD").build(),
                "sourceObjectKey", AttributeValue.builder().s("sample-documents/user-123/22222222-2222-2222-2222-222222222222/memo-2.pdf").build(),
                "createdAt", AttributeValue.builder().s("2026-03-29T06:15:00Z").build(),
                "updatedAt", AttributeValue.builder().s("2026-03-29T06:20:00Z").build()
        );
    }
}
