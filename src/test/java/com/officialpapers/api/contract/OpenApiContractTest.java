package com.officialpapers.api.contract;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiContractTest {

    @Test
    void openApiServerBasePathMatchesSampleDocumentRoutes() throws IOException {
        String openApi = Files.readString(Path.of("src/main/resources/openapi/openapi.yaml"));
        String appTemplate = Files.readString(Path.of("infra/app-only.yaml"));
        String buildSpec = Files.readString(Path.of("infra/buildspec.yaml"));

        assertTrue(openApi.contains("url: http://127.0.0.1:3000/api/v1"));
        assertTrue(openApi.contains("url: https://api.example.com/api/v1"));
        assertTrue(openApi.contains("url: https://staging-api.example.com/api/v1"));
        assertTrue(openApi.contains("/sample-documents:"));
        assertTrue(openApi.contains("/sample-documents/{documentId}/download-url:"));
        assertTrue(appTemplate.contains("Path: /api/v1/sample-documents"));
        assertTrue(appTemplate.contains("UPLOADED_DOCUMENT_METADATA_TABLE"));
        assertTrue(appTemplate.contains("UPLOADED_DOCUMENT_BUCKET"));
        assertTrue(appTemplate.contains("- s3:ListBucket"));
        assertTrue(appTemplate.contains("CorsConfiguration:"));
        assertTrue(buildSpec.contains("infra/app-only.yaml"));
    }
}
