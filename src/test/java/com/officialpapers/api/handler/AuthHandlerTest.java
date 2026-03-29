package com.officialpapers.api.handler;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.officialpapers.api.service.AuthService;
import com.officialpapers.domain.AuthTokens;
import com.officialpapers.domain.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AuthService authService;

    private AuthHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AuthHandler(authService);
    }

    @Test
    void loginReturnsTokenPayload() throws Exception {
        when(authService.login("user@example.com", "secret-password")).thenReturn(
                new AuthTokens("access-token", "id-token", "refresh-token", "Bearer", 3600)
        );

        APIGatewayProxyResponseEvent response = handler.handleRequest(
                request("POST", "/api/v1/auth/login", "{\"email\":\"user@example.com\",\"password\":\"secret-password\"}", null),
                null
        );

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals(200, response.getStatusCode());
        assertEquals("access-token", body.get("accessToken").asText());
        assertEquals("Bearer", body.get("tokenType").asText());
    }

    @Test
    void signupRejectsUnknownField() throws Exception {
        APIGatewayProxyResponseEvent response = handler.handleRequest(
                request("POST", "/api/v1/auth/signup", "{\"email\":\"user@example.com\",\"password\":\"secret-password\",\"extra\":true}", null),
                null
        );

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals(400, response.getStatusCode());
        assertEquals("BAD_REQUEST", body.get("code").asText());
    }

    @Test
    void logoutUsesBearerTokenAndRefreshToken() {
        APIGatewayProxyResponseEvent response = handler.handleRequest(
                request(
                        "POST",
                        "/api/v1/auth/logout",
                        "{\"refreshToken\":\"refresh-token\"}",
                        Map.of("Authorization", "Bearer access-token")
                ),
                null
        );

        verify(authService).logout("access-token", "refresh-token");
        assertEquals(204, response.getStatusCode());
    }

    @Test
    void meReturnsCurrentUser() throws Exception {
        when(authService.getCurrentUser("access-token")).thenReturn(
                new AuthenticatedUser("user-123", "user@example.com", true)
        );

        APIGatewayProxyResponseEvent response = handler.handleRequest(
                request("GET", "/api/v1/auth/me", null, Map.of("Authorization", "Bearer access-token")),
                null
        );

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals(200, response.getStatusCode());
        assertEquals("user-123", body.get("userId").asText());
        assertTrue(body.get("emailVerified").asBoolean());
    }

    @Test
    void protectedRoutesRequireBearerToken() throws Exception {
        APIGatewayProxyResponseEvent response = handler.handleRequest(
                request("GET", "/api/v1/auth/me", null, null),
                null
        );

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals(401, response.getStatusCode());
        assertEquals("UNAUTHORIZED", body.get("code").asText());
    }

    private static APIGatewayProxyRequestEvent request(
            String method,
            String path,
            String body,
            Map<String, String> headers
    ) {
        return new APIGatewayProxyRequestEvent()
                .withHttpMethod(method)
                .withPath(path)
                .withBody(body)
                .withHeaders(headers);
    }
}
