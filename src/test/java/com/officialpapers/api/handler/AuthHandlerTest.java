package com.officialpapers.api.handler;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.officialpapers.api.service.AuthService;
import com.officialpapers.domain.AuthChallenge;
import com.officialpapers.domain.AuthTokens;
import com.officialpapers.domain.AuthenticatedUser;
import com.officialpapers.domain.LoginResult;
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
                LoginResult.authenticated(new AuthTokens("access-token", "id-token", "refresh-token", "Bearer", 3600))
        );

        APIGatewayProxyResponseEvent response = handler.handleRequest(
                request("POST", "/api/v1/auth/login", "{\"email\":\"user@example.com\",\"password\":\"secret-password\"}", null),
                null
        );

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals(200, response.getStatusCode());
        assertEquals("access-token", body.get("tokens").get("accessToken").asText());
        assertEquals("Bearer", body.get("tokens").get("tokenType").asText());
    }

    @Test
    void loginReturnsChallengePayload() throws Exception {
        when(authService.login("user@example.com", "temp-password")).thenReturn(
                LoginResult.challenged(new AuthChallenge("NEW_PASSWORD_REQUIRED", "session-123", "user@example.com"))
        );

        APIGatewayProxyResponseEvent response = handler.handleRequest(
                request("POST", "/api/v1/auth/login", "{\"email\":\"user@example.com\",\"password\":\"temp-password\"}", null),
                null
        );

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals(200, response.getStatusCode());
        assertEquals("NEW_PASSWORD_REQUIRED", body.get("challenge").get("challengeName").asText());
        assertEquals("session-123", body.get("challenge").get("session").asText());
    }

    @Test
    void loginRejectsUnknownField() throws Exception {
        APIGatewayProxyResponseEvent response = handler.handleRequest(
                request("POST", "/api/v1/auth/login", "{\"email\":\"user@example.com\",\"password\":\"secret-password\",\"extra\":true}", null),
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

    @Test
    void refreshCallsAuthService() throws Exception {
        when(authService.refresh("refresh-token")).thenReturn(
                new AuthTokens("new-access", "new-id", "refresh-token", "Bearer", 3600)
        );

        APIGatewayProxyResponseEvent response = handler.handleRequest(
                request("POST", "/api/v1/auth/refresh", "{\"refreshToken\":\"refresh-token\"}", null),
                null
        );

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals(200, response.getStatusCode());
        assertEquals("new-access", body.get("accessToken").asText());
    }

    @Test
    void respondToNewPasswordCallsAuthService() throws Exception {
        when(authService.respondToNewPassword("user@example.com", "new-secret", "session-123")).thenReturn(
                new AuthTokens("access-token", "id-token", "refresh-token", "Bearer", 3600)
        );

        APIGatewayProxyResponseEvent response = handler.handleRequest(
                request(
                        "POST",
                        "/api/v1/auth/respond-to-new-password",
                        "{\"email\":\"user@example.com\",\"newPassword\":\"new-secret\",\"session\":\"session-123\"}",
                        null
                ),
                null
        );

        JsonNode body = objectMapper.readTree(response.getBody());
        verify(authService).respondToNewPassword("user@example.com", "new-secret", "session-123");
        assertEquals(200, response.getStatusCode());
        assertEquals("access-token", body.get("accessToken").asText());
    }

    @Test
    void forgotPasswordCallsAuthService() {
        APIGatewayProxyResponseEvent response = handler.handleRequest(
                request("POST", "/api/v1/auth/forgot-password", "{\"email\":\"user@example.com\"}", null),
                null
        );

        verify(authService).forgotPassword("user@example.com");
        assertEquals(204, response.getStatusCode());
    }

    @Test
    void confirmForgotPasswordCallsAuthService() {
        APIGatewayProxyResponseEvent response = handler.handleRequest(
                request("POST", "/api/v1/auth/confirm-forgot-password",
                        "{\"email\":\"user@example.com\",\"confirmationCode\":\"123456\",\"newPassword\":\"new-secret\"}", null),
                null
        );

        verify(authService).confirmForgotPassword("user@example.com", "123456", "new-secret");
        assertEquals(204, response.getStatusCode());
    }

    @Test
    void missingRequiredFieldReturns400() throws Exception {
        APIGatewayProxyResponseEvent response = handler.handleRequest(
                request("POST", "/api/v1/auth/login", "{\"email\":\"user@example.com\"}", null),
                null
        );

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals(400, response.getStatusCode());
        assertEquals("BAD_REQUEST", body.get("code").asText());
        assertTrue(body.get("message").asText().contains("password"));
    }

    @Test
    void emptyBodyReturns400() throws Exception {
        APIGatewayProxyResponseEvent response = handler.handleRequest(
                request("POST", "/api/v1/auth/login", "", null),
                null
        );

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals(400, response.getStatusCode());
        assertEquals("BAD_REQUEST", body.get("code").asText());
    }

    @Test
    void invalidJsonReturns400() throws Exception {
        APIGatewayProxyResponseEvent response = handler.handleRequest(
                request("POST", "/api/v1/auth/login", "{invalid json", null),
                null
        );

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals(400, response.getStatusCode());
        assertEquals("BAD_REQUEST", body.get("code").asText());
    }

    @Test
    void nonObjectBodyReturns400() throws Exception {
        APIGatewayProxyResponseEvent response = handler.handleRequest(
                request("POST", "/api/v1/auth/login", "[]", null),
                null
        );

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals(400, response.getStatusCode());
        assertEquals("BAD_REQUEST", body.get("code").asText());
    }

    @Test
    void optionsRequestReturns204WithCorsHeaders() {
        APIGatewayProxyResponseEvent response = handler.handleRequest(
                request("OPTIONS", "/api/v1/auth/login", null, null),
                null
        );

        assertEquals(204, response.getStatusCode());
        assertTrue(response.getHeaders().containsKey("Access-Control-Allow-Origin"));
    }

    @Test
    void unknownRouteReturns404() throws Exception {
        APIGatewayProxyResponseEvent response = handler.handleRequest(
                request("GET", "/api/v1/auth/unknown", null, null),
                null
        );

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals(404, response.getStatusCode());
        assertEquals("NOT_FOUND", body.get("code").asText());
    }

    @Test
    void unsupportedMethodReturns405() throws Exception {
        APIGatewayProxyResponseEvent response = handler.handleRequest(
                request("PUT", "/api/v1/auth/login", "{}", null),
                null
        );

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals(405, response.getStatusCode());
        assertEquals("METHOD_NOT_ALLOWED", body.get("code").asText());
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
