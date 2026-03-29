package com.officialpapers.api.handler;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.officialpapers.api.service.UnauthorizedException;
import com.officialpapers.domain.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequestAuthenticationResolverTest {

    private RequestAuthenticationResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new RequestAuthenticationResolver();
    }

    @Test
    void requireAuthenticatedUserExtractsUserFromClaims() {
        APIGatewayProxyRequestEvent.ProxyRequestContext requestContext = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        requestContext.setAuthorizer(Map.of(
                "claims", Map.of(
                        "sub", "user-123",
                        "email", "user@example.com",
                        "email_verified", "true"
                )
        ));

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withRequestContext(requestContext);

        AuthenticatedUser user = resolver.requireAuthenticatedUser(event);

        assertEquals("user-123", user.userId());
        assertEquals("user@example.com", user.email());
        assertEquals(true, user.emailVerified());
    }

    @Test
    void requireAuthenticatedUserExtractsUserFromAuthorizerMap() {
        APIGatewayProxyRequestEvent.ProxyRequestContext requestContext = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        requestContext.setAuthorizer(Map.of(
                "sub", "user-456",
                "email", "another@example.com",
                "email_verified", "false"
        ));

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withRequestContext(requestContext);

        AuthenticatedUser user = resolver.requireAuthenticatedUser(event);

        assertEquals("user-456", user.userId());
        assertEquals("another@example.com", user.email());
        assertEquals(false, user.emailVerified());
    }

    @Test
    void requireAuthenticatedUserThrowsWhenSubIsMissing() {
        APIGatewayProxyRequestEvent.ProxyRequestContext requestContext = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        requestContext.setAuthorizer(Map.of(
                "email", "user@example.com"
        ));

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withRequestContext(requestContext);

        UnauthorizedException exception = assertThrows(
                UnauthorizedException.class,
                () -> resolver.requireAuthenticatedUser(event)
        );

        assertEquals("UNAUTHORIZED", exception.code());
        assertEquals("Authenticated user is required", exception.getMessage());
    }

    @Test
    void requireAuthenticatedUserThrowsWhenAuthorizerIsMissing() {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withRequestContext(new APIGatewayProxyRequestEvent.ProxyRequestContext());

        UnauthorizedException exception = assertThrows(
                UnauthorizedException.class,
                () -> resolver.requireAuthenticatedUser(event)
        );

        assertEquals("UNAUTHORIZED", exception.code());
    }

    @Test
    void requireAuthenticatedUserThrowsWhenRequestContextIsNull() {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();

        UnauthorizedException exception = assertThrows(
                UnauthorizedException.class,
                () -> resolver.requireAuthenticatedUser(event)
        );

        assertEquals("UNAUTHORIZED", exception.code());
    }

    @Test
    void requireAccessTokenExtractsBearerToken() {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHeaders(Map.of("Authorization", "Bearer my-access-token"));

        String token = resolver.requireAccessToken(event);

        assertEquals("my-access-token", token);
    }

    @Test
    void requireAccessTokenHandlesCaseInsensitiveHeader() {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHeaders(Map.of("authorization", "Bearer my-token"));

        String token = resolver.requireAccessToken(event);

        assertEquals("my-token", token);
    }

    @Test
    void requireAccessTokenTrimsWhitespace() {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHeaders(Map.of("Authorization", "  Bearer   my-token   "));

        String token = resolver.requireAccessToken(event);

        assertEquals("my-token", token);
    }

    @Test
    void requireAccessTokenThrowsWhenHeaderIsMissing() {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHeaders(Map.of());

        UnauthorizedException exception = assertThrows(
                UnauthorizedException.class,
                () -> resolver.requireAccessToken(event)
        );

        assertEquals("UNAUTHORIZED", exception.code());
        assertEquals("Authorization header is required", exception.getMessage());
    }

    @Test
    void requireAccessTokenThrowsWhenHeadersAreNull() {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();

        UnauthorizedException exception = assertThrows(
                UnauthorizedException.class,
                () -> resolver.requireAccessToken(event)
        );

        assertEquals("UNAUTHORIZED", exception.code());
    }

    @Test
    void requireAccessTokenThrowsWhenNotBearerScheme() {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHeaders(Map.of("Authorization", "Basic username:password"));

        UnauthorizedException exception = assertThrows(
                UnauthorizedException.class,
                () -> resolver.requireAccessToken(event)
        );

        assertEquals("UNAUTHORIZED", exception.code());
        assertEquals("Authorization header must use Bearer authentication", exception.getMessage());
    }

    @Test
    void requireAccessTokenThrowsWhenAuthorizationHeaderIsBlank() {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHeaders(Map.of("Authorization", "   "));

        UnauthorizedException exception = assertThrows(
                UnauthorizedException.class,
                () -> resolver.requireAccessToken(event)
        );

        assertEquals("UNAUTHORIZED", exception.code());
        assertEquals("Authorization header is required", exception.getMessage());
    }
}
