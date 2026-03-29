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
        String appOnlyTemplate = Files.readString(Path.of("infra/app-only.yaml"));

        assertTrue(openApi.contains("url: http://127.0.0.1:3000/api/v1"));
        assertTrue(openApi.contains("/auth/login:"));
        assertTrue(openApi.contains("/auth/respond-to-new-password:"));
        assertTrue(openApi.contains("/auth/me:"));
        assertTrue(!openApi.contains("/auth/signup:"));
        assertTrue(!openApi.contains("/auth/confirm-signup:"));
        assertTrue(!openApi.contains("/auth/resend-confirmation:"));
        assertTrue(openApi.contains("/sample-documents/{documentId}/download-url:"));
        assertTrue(openApi.contains("CognitoBearerAuth:"));
        assertTrue(openApi.contains("security:"));
        assertTrue(openApi.contains("TooManyRequests:"));

        assertTrue(samTemplate.contains("Path: /api/v1/auth/login"));
        assertTrue(samTemplate.contains("Path: /api/v1/auth/respond-to-new-password"));
        assertTrue(samTemplate.contains("Path: /api/v1/auth/me"));
        assertTrue(samTemplate.contains("Path: /api/v1/auth/logout\n            Method: POST\n            Auth:\n              Authorizer: NONE"));
        assertTrue(samTemplate.contains("Path: /api/v1/auth/me\n            Method: GET\n            Auth:\n              Authorizer: NONE"));
        assertTrue(!samTemplate.contains("Path: /api/v1/auth/signup"));
        assertTrue(!samTemplate.contains("Path: /api/v1/auth/confirm-signup"));
        assertTrue(!samTemplate.contains("Path: /api/v1/auth/resend-confirmation"));
        assertTrue(samTemplate.contains("DefaultAuthorizer: CognitoAuthorizer"));
        assertTrue(samTemplate.contains("AllowHeaders: \"'Content-Type,Authorization'\""));
        assertTrue(samTemplate.contains("COGNITO_USER_POOL_CLIENT_ID"));
        assertTrue(samTemplate.contains("cognito-idp:RespondToAuthChallenge"));
        assertTrue(samTemplate.contains("AttributeName: ownerUserId"));
        assertTrue(samTemplate.contains("- s3:ListBucket"));

        assertTrue(appOnlyTemplate.contains("Path: /api/v1/auth/login"));
        assertTrue(appOnlyTemplate.contains("Path: /api/v1/auth/respond-to-new-password"));
        assertTrue(appOnlyTemplate.contains("Path: /api/v1/auth/me"));
        assertTrue(appOnlyTemplate.contains("Path: /api/v1/auth/logout\n            Method: POST\n            Auth:\n              Authorizer: NONE"));
        assertTrue(appOnlyTemplate.contains("Path: /api/v1/auth/me\n            Method: GET\n            Auth:\n              Authorizer: NONE"));
        assertTrue(appOnlyTemplate.contains("DefaultAuthorizer: CognitoAuthorizer"));
        assertTrue(appOnlyTemplate.contains("AllowHeaders: \"'Content-Type,Authorization'\""));
        assertTrue(appOnlyTemplate.contains("COGNITO_USER_POOL_CLIENT_ID"));
        assertTrue(appOnlyTemplate.contains("cognito-idp:RespondToAuthChallenge"));
        assertTrue(appOnlyTemplate.contains("AttributeName: ownerUserId"));
        assertTrue(appOnlyTemplate.contains("- dynamodb:Query"));
        assertTrue(appOnlyTemplate.contains("- s3:ListBucket"));
    }
}
