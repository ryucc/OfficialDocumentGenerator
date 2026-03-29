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
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
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
        assertEquals("11111111-1111-1111-1111-111111111111", request.item().get("documentId").s());
        assertEquals("memo.pdf", request.item().get("filename").s());
        assertEquals("application/pdf", request.item().get("contentType").s());
        assertEquals("512", request.item().get("sizeBytes").n());
        assertEquals("AVAILABLE", request.item().get("status").s());
        assertEquals("sample-documents/11111111-1111-1111-1111-111111111111/memo.pdf", request.item().get("sourceObjectKey").s());
    }

    @Test
    void findByIdReturnsMappedDocument() {
        when(dynamoDbClient.getItem(org.mockito.ArgumentMatchers.any(GetItemRequest.class))).thenReturn(GetItemResponse.builder()
                .item(item())
                .build());

        UploadedDocument document = repository.findById("11111111-1111-1111-1111-111111111111").orElseThrow();

        assertEquals("memo.pdf", document.filename());
        assertEquals(UploadedDocumentStatus.AVAILABLE, document.status());
        assertEquals(512L, document.sizeBytes());
    }

    @Test
    void findByIdReturnsEmptyWhenItemMissing() {
        when(dynamoDbClient.getItem(org.mockito.ArgumentMatchers.any(GetItemRequest.class))).thenReturn(GetItemResponse.builder().build());

        assertTrue(repository.findById("11111111-1111-1111-1111-111111111111").isEmpty());
    }

    @Test
    void findAllMapsScanResults() {
        when(dynamoDbClient.scan(org.mockito.ArgumentMatchers.any(ScanRequest.class))).thenReturn(ScanResponse.builder()
                .items(item(), item().entrySet().stream().collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
                )))
                .build());

        List<UploadedDocument> documents = repository.findAll();

        assertEquals(2, documents.size());
        assertEquals("11111111-1111-1111-1111-111111111111", documents.get(0).id());
    }

    @Test
    void deleteByIdDeletesPrimaryKey() {
        repository.deleteById("11111111-1111-1111-1111-111111111111");

        ArgumentCaptor<DeleteItemRequest> captor = ArgumentCaptor.forClass(DeleteItemRequest.class);
        verify(dynamoDbClient).deleteItem(captor.capture());

        DeleteItemRequest request = captor.getValue();
        assertEquals("sample-documents-test", request.tableName());
        assertEquals("11111111-1111-1111-1111-111111111111", request.key().get("documentId").s());
    }

    @Test
    void findAllScansConfiguredTable() {
        when(dynamoDbClient.scan(org.mockito.ArgumentMatchers.any(ScanRequest.class))).thenReturn(ScanResponse.builder().build());

        repository.findAll();

        ArgumentCaptor<ScanRequest> captor = ArgumentCaptor.forClass(ScanRequest.class);
        verify(dynamoDbClient).scan(captor.capture());
        assertEquals("sample-documents-test", captor.getValue().tableName());
    }

    private static UploadedDocument document() {
        return new UploadedDocument(
                "11111111-1111-1111-1111-111111111111",
                "memo.pdf",
                "application/pdf",
                512L,
                UploadedDocumentStatus.AVAILABLE,
                "sample-documents/11111111-1111-1111-1111-111111111111/memo.pdf",
                "2026-03-29T06:00:00Z",
                "2026-03-29T06:10:00Z"
        );
    }

    private static Map<String, AttributeValue> item() {
        return Map.of(
                "documentId", AttributeValue.builder().s("11111111-1111-1111-1111-111111111111").build(),
                "filename", AttributeValue.builder().s("memo.pdf").build(),
                "contentType", AttributeValue.builder().s("application/pdf").build(),
                "sizeBytes", AttributeValue.builder().n("512").build(),
                "status", AttributeValue.builder().s("AVAILABLE").build(),
                "sourceObjectKey", AttributeValue.builder().s("sample-documents/11111111-1111-1111-1111-111111111111/memo.pdf").build(),
                "createdAt", AttributeValue.builder().s("2026-03-29T06:00:00Z").build(),
                "updatedAt", AttributeValue.builder().s("2026-03-29T06:10:00Z").build()
        );
    }
}
