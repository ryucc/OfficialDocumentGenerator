package com.officialpapers.api.handler;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.officialpapers.api.service.NotFoundException;
import com.officialpapers.api.service.UploadedDocumentService;
import com.officialpapers.domain.CreatedUpload;
import com.officialpapers.domain.DownloadTarget;
import com.officialpapers.domain.UploadTarget;
import com.officialpapers.domain.UploadedDocument;
import com.officialpapers.domain.UploadedDocumentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SampleDocumentHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private UploadedDocumentService documentService;

    private SampleDocumentHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SampleDocumentHandler(documentService);
    }

    @Test
    void listDocumentsReturnsEnvelope() throws Exception {
        when(documentService.listDocuments()).thenReturn(List.of(document(
                "11111111-1111-1111-1111-111111111111",
                UploadedDocumentStatus.AVAILABLE
        )));

        APIGatewayProxyResponseEvent response = handler.handleRequest(
                request("GET", "/api/v1/sample-documents", null, null),
                null
        );

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals(200, response.getStatusCode());
        assertEquals("*", response.getHeaders().get("Access-Control-Allow-Origin"));
        assertEquals(1, body.get("items").size());
        assertEquals("11111111-1111-1111-1111-111111111111", body.get("items").get(0).get("id").asText());
    }

    @Test
    void createDocumentReturnsUploadSession() throws Exception {
        when(documentService.createDocument(any())).thenReturn(new CreatedUpload(
                document("11111111-1111-1111-1111-111111111111", UploadedDocumentStatus.PENDING_UPLOAD),
                new UploadTarget(
                        "https://upload.example.com/object",
                        "PUT",
                        Map.of("Content-Type", "application/pdf"),
                        "2026-03-29T06:15:00Z"
                )
        ));

        APIGatewayProxyResponseEvent response = handler.handleRequest(
                request(
                        "POST",
                        "/api/v1/sample-documents",
                        "{\"filename\":\"memo.pdf\",\"contentType\":\"application/pdf\",\"sizeBytes\":512}",
                        null
                ),
                null
        );

        JsonNode body = objectMapper.readTree(response.getBody());
        ArgumentCaptor<com.officialpapers.domain.CreateUploadedDocumentCommand> commandCaptor =
                ArgumentCaptor.forClass(com.officialpapers.domain.CreateUploadedDocumentCommand.class);

        verify(documentService).createDocument(commandCaptor.capture());
        assertEquals("memo.pdf", commandCaptor.getValue().filename());
        assertEquals("application/pdf", commandCaptor.getValue().contentType());
        assertEquals(512L, commandCaptor.getValue().sizeBytes());
        assertEquals(201, response.getStatusCode());
        assertEquals("https://upload.example.com/object", body.get("upload").get("uploadUrl").asText());
        assertEquals("PENDING_UPLOAD", body.get("document").get("status").asText());
    }

    @Test
    void createDocumentRejectsUnknownField() throws Exception {
        APIGatewayProxyResponseEvent response = handler.handleRequest(
                request(
                        "POST",
                        "/api/v1/sample-documents",
                        "{\"filename\":\"memo.pdf\",\"contentType\":\"application/pdf\",\"extra\":true}",
                        null
                ),
                null
        );

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals(400, response.getStatusCode());
        assertEquals("BAD_REQUEST", body.get("code").asText());
        assertTrue(body.get("message").asText().contains("Unknown field"));
    }

    @Test
    void getDocumentRejectsInvalidUuidPathParameter() throws Exception {
        APIGatewayProxyResponseEvent response = handler.handleRequest(
                request("GET", "/api/v1/sample-documents/not-a-uuid", null, Map.of("documentId", "not-a-uuid")),
                null
        );

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals(400, response.getStatusCode());
        assertEquals("BAD_REQUEST", body.get("code").asText());
    }

    @Test
    void completeUploadReturnsUpdatedDocument() throws Exception {
        when(documentService.completeUpload("11111111-1111-1111-1111-111111111111")).thenReturn(
                document("11111111-1111-1111-1111-111111111111", UploadedDocumentStatus.AVAILABLE)
        );

        APIGatewayProxyResponseEvent response = handler.handleRequest(
                request(
                        "POST",
                        "/api/v1/sample-documents/11111111-1111-1111-1111-111111111111/complete",
                        "",
                        Map.of("documentId", "11111111-1111-1111-1111-111111111111")
                ),
                null
        );

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals(200, response.getStatusCode());
        assertEquals("AVAILABLE", body.get("status").asText());
    }

    @Test
    void downloadUrlReturnsPresignedTarget() throws Exception {
        when(documentService.createDownloadTarget("11111111-1111-1111-1111-111111111111")).thenReturn(
                new DownloadTarget("https://download.example.com/object", "GET", "2026-03-29T06:15:00Z")
        );

        APIGatewayProxyResponseEvent response = handler.handleRequest(
                request(
                        "GET",
                        "/api/v1/sample-documents/11111111-1111-1111-1111-111111111111/download-url",
                        null,
                        Map.of("documentId", "11111111-1111-1111-1111-111111111111")
                ),
                null
        );

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals(200, response.getStatusCode());
        assertEquals("https://download.example.com/object", body.get("downloadUrl").asText());
        assertEquals("GET", body.get("downloadMethod").asText());
    }

    @Test
    void deleteReturnsNoContent() {
        APIGatewayProxyResponseEvent response = handler.handleRequest(
                request(
                        "DELETE",
                        "/api/v1/sample-documents/11111111-1111-1111-1111-111111111111",
                        null,
                        Map.of("documentId", "11111111-1111-1111-1111-111111111111")
                ),
                null
        );

        verify(documentService).deleteDocument("11111111-1111-1111-1111-111111111111");
        assertEquals(204, response.getStatusCode());
    }

    @Test
    void notFoundExceptionsBecome404Responses() throws Exception {
        when(documentService.getDocument("11111111-1111-1111-1111-111111111111"))
                .thenThrow(new NotFoundException("Document not found"));

        APIGatewayProxyResponseEvent response = handler.handleRequest(
                request(
                        "GET",
                        "/api/v1/sample-documents/11111111-1111-1111-1111-111111111111",
                        null,
                        Map.of("documentId", "11111111-1111-1111-1111-111111111111")
                ),
                null
        );

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals(404, response.getStatusCode());
        assertEquals("NOT_FOUND", body.get("code").asText());
    }

    @Test
    void optionsRequestsReturnCorsHeaders() {
        APIGatewayProxyResponseEvent response = handler.handleRequest(
                request("OPTIONS", "/api/v1/sample-documents", null, null),
                null
        );

        assertEquals(204, response.getStatusCode());
        assertEquals("*", response.getHeaders().get("Access-Control-Allow-Origin"));
        assertTrue(response.getHeaders().get("Access-Control-Allow-Methods").contains("OPTIONS"));
    }

    private static UploadedDocument document(String id, UploadedDocumentStatus status) {
        return new UploadedDocument(
                id,
                "memo.pdf",
                "application/pdf",
                512L,
                status,
                "sample-documents/" + id + "/memo.pdf",
                null,
                "2026-03-29T06:00:00Z",
                "2026-03-29T06:05:00Z"
        );
    }

    private static APIGatewayProxyRequestEvent request(
            String method,
            String path,
            String body,
            Map<String, String> pathParameters
    ) {
        return new APIGatewayProxyRequestEvent()
                .withHttpMethod(method)
                .withPath(path)
                .withBody(body)
                .withPathParameters(pathParameters);
    }
}
