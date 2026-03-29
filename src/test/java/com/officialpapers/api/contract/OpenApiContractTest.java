package com.officialpapers.api.contract;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiContractTest {

    @Test
    void openApiAndSamTemplateExposeSecuredDocumentRoutesAndAuthEndpoints() throws IOException {
        String openApi = Files.readString(Path.of("src/main/resources/openapi/openapi.yaml"));
        String samTemplate = Files.readString(Path.of("infra/template.yaml"));

        assertTrue(openApi.contains("url: http://127.0.0.1:3000/api/v1"));
        assertTrue(openApi.contains("/auth/login:"));
        assertTrue(openApi.contains("/auth/me:"));
        assertTrue(openApi.contains("/sample-documents/{documentId}/download-url:"));
        assertTrue(openApi.contains("CognitoBearerAuth:"));
        assertTrue(openApi.contains("security:"));
        assertTrue(openApi.contains("TooManyRequests:"));

        assertTrue(samTemplate.contains("Path: /api/v1/auth/login"));
        assertTrue(samTemplate.contains("Path: /api/v1/auth/me"));
        assertTrue(samTemplate.contains("DefaultAuthorizer: CognitoAuthorizer"));
        assertTrue(samTemplate.contains("AllowHeaders: \"'Content-Type,Authorization'\""));
        assertTrue(samTemplate.contains("COGNITO_USER_POOL_CLIENT_ID"));
        assertTrue(samTemplate.contains("AttributeName: ownerUserId"));
    }
}
