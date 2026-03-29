package com.officialpapers.api.contract;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiContractTest {

    @Test
    void openApiServerBasePathMatchesSamRoutes() throws IOException {
        String openApi = Files.readString(Path.of("src/main/resources/openapi/openapi.yaml"));
        String samTemplate = Files.readString(Path.of("template.yaml"));

        assertTrue(openApi.contains("url: https://api.example.com/api/v1"));
        assertTrue(openApi.contains("url: https://staging-api.example.com/api/v1"));
        assertTrue(samTemplate.contains("Path: /api/v1/document-instructions"));
    }
}
