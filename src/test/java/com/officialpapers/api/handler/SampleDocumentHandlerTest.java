package com.officialpapers.api.handler;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.officialpapers.api.service.NotFoundException;
import com.officialpapers.api.service.UploadedDocumentService;
import com.officialpapers.domain.AuthenticatedUser;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SampleDocumentHandlerTest {

    private static final AuthenticatedUser USER =
            new AuthenticatedUser("user-123", "user@example.com", true);

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
        when(documentService.listDocuments(USER)).thenReturn(List.of(document(
                "11111111-1111-1111-1111-111111111111",
                UploadedDocumentStatus.AVAILABLE
        )));

        APIGatewayProxyResponseEvent response = handler.handleRequest(
                authenticatedRequest("GET", "/api/v1/sample-documents", null, null),
                null
        );

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals(200, response.getStatusCode());
        assertEquals("*", response.getHeaders().get("Access-Control-Allow-Origin"));
        assertEquals(1, body.get("items").size());
    }

    @Test
    void createDocumentReturnsUploadSession() throws Exception {
        when(documentService.createDocument(eq(USER), any())).thenReturn(new CreatedUpload(
                document("11111111-1111-1111-1111-111111111111", UploadedDocumentStatus.PENDING_UPLOAD),
                new UploadTarget(
                        "https://upload.example.com/object",
                        "PUT",
                        Map.of("Content-Type", "application/pdf"),
                        "2026-03-29T06:15:00Z"
                )
        ));

        APIGatewayProxyResponseEvent response = handler.handleRequest(
                authenticatedRequest(
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

        verify(documentService).createDocument(eq(USER), commandCaptor.capture());
        assertEquals("memo.pdf", commandCaptor.getValue().filename());
        assertEquals(201, response.getStatusCode());
        assertEquals("https://upload.example.com/object", body.get("upload").get("uploadUrl").asText());
    }

    @Test
    void createDocumentRejectsUnknownField() throws Exception {
        APIGatewayProxyResponseEvent response = handler.handleRequest(
                authenticatedRequest(
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
    }

    @Test
    void getDocumentRejectsInvalidUuidPathParameter() throws Exception {
        APIGatewayProxyResponseEvent response = handler.handleRequest(
                authenticatedRequest("GET", "/api/v1/sample-documents/not-a-uuid", null, Map.of("documentId", "not-a-uuid")),
                null
        );

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals(400, response.getStatusCode());
        assertEquals("BAD_REQUEST", body.get("code").asText());
    }

    @Test
    void completeUploadReturnsUpdatedDocument() throws Exception {
        when(documentService.completeUpload(USER, "11111111-1111-1111-1111-111111111111")).thenReturn(
                document("11111111-1111-1111-1111-111111111111", UploadedDocumentStatus.AVAILABLE)
        );

        APIGatewayProxyResponseEvent response = handler.handleRequest(
                authenticatedRequest(
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
        when(documentService.createDownloadTarget(USER, "11111111-1111-1111-1111-111111111111")).thenReturn(
                new DownloadTarget("https://download.example.com/object", "GET", "2026-03-29T06:15:00Z")
        );

        APIGatewayProxyResponseEvent response = handler.handleRequest(
                authenticatedRequest(
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
    }

    @Test
    void deleteReturnsNoContent() {
        APIGatewayProxyResponseEvent response = handler.handleRequest(
                authenticatedRequest(
                        "DELETE",
                        "/api/v1/sample-documents/11111111-1111-1111-1111-111111111111",
                        null,
                        Map.of("documentId", "11111111-1111-1111-1111-111111111111")
                ),
                null
        );

        verify(documentService).deleteDocument(USER, "11111111-1111-1111-1111-111111111111");
        assertEquals(204, response.getStatusCode());
    }

    @Test
    void notFoundExceptionsBecome404Responses() throws Exception {
        when(documentService.getDocument(USER, "11111111-1111-1111-1111-111111111111"))
                .thenThrow(new NotFoundException("Document not found"));

        APIGatewayProxyResponseEvent response = handler.handleRequest(
                authenticatedRequest(
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
    void missingClaimsBecomeUnauthorizedResponses() throws Exception {
        APIGatewayProxyResponseEvent response = handler.handleRequest(
                request("GET", "/api/v1/sample-documents", null, null),
                null
        );

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals(401, response.getStatusCode());
        assertEquals("UNAUTHORIZED", body.get("code").asText());
    }

    @Test
    void optionsRequestsReturnCorsHeaders() {
        APIGatewayProxyResponseEvent response = handler.handleRequest(
                request("OPTIONS", "/api/v1/sample-documents", null, null),
                null
        );

        assertEquals(204, response.getStatusCode());
        assertEquals("*", response.getHeaders().get("Access-Control-Allow-Origin"));
        assertTrue(response.getHeaders().get("Access-Control-Allow-Headers").contains("Authorization"));
    }

    private static UploadedDocument document(String id, UploadedDocumentStatus status) {
        return new UploadedDocument(
                id,
                USER.userId(),
                "memo.pdf",
                "application/pdf",
                512L,
                status,
                "sample-documents/" + USER.userId() + "/" + id + "/memo.pdf",
                "2026-03-29T06:00:00Z",
                "2026-03-29T06:05:00Z"
        );
    }

    private static APIGatewayProxyRequestEvent authenticatedRequest(
            String method,
            String path,
            String body,
            Map<String, String> pathParameters
    ) {
        APIGatewayProxyRequestEvent.ProxyRequestContext requestContext =
                new APIGatewayProxyRequestEvent.ProxyRequestContext();
        requestContext.setAuthorizer(Map.of(
                "claims", Map.of(
                        "sub", USER.userId(),
                        "email", USER.email(),
                        "email_verified", "true"
                )
        ));
        return request(method, path, body, pathParameters)
                .withRequestContext(requestContext);
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
