package com.officialpapers.api.handler;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.officialpapers.api.service.UnauthorizedException;
import com.officialpapers.domain.AuthenticatedUser;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;

@Singleton
public class RequestAuthenticationResolver {

    @Inject
    public RequestAuthenticationResolver() {
    }

    public AuthenticatedUser requireAuthenticatedUser(APIGatewayProxyRequestEvent event) {
        Map<?, ?> claims = claims(event);
        String userId = stringValue(claims.get("sub"));
        if (userId == null) {
            throw new UnauthorizedException("UNAUTHORIZED", "Authenticated user is required");
        }

        return new AuthenticatedUser(
                userId,
                stringValue(claims.get("email")),
                Boolean.parseBoolean(stringValue(claims.get("email_verified")))
        );
    }

    public String requireAccessToken(APIGatewayProxyRequestEvent event) {
        Map<String, String> headers = event == null ? null : event.getHeaders();
        if (headers == null || headers.isEmpty()) {
            throw new UnauthorizedException("UNAUTHORIZED", "Authorization header is required");
        }

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if ("Authorization".equalsIgnoreCase(entry.getKey())) {
                return parseBearerToken(entry.getValue());
            }
        }

        throw new UnauthorizedException("UNAUTHORIZED", "Authorization header is required");
    }

    private Map<?, ?> claims(APIGatewayProxyRequestEvent event) {
        Object authorizer = event != null && event.getRequestContext() != null
                ? event.getRequestContext().getAuthorizer()
                : null;
        if (!(authorizer instanceof Map<?, ?> authorizerMap)) {
            throw new UnauthorizedException("UNAUTHORIZED", "Authenticated user is required");
        }

        Object claims = authorizerMap.get("claims");
        if (claims instanceof Map<?, ?> claimMap) {
            return claimMap;
        }
        return authorizerMap;
    }

    private String parseBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new UnauthorizedException("UNAUTHORIZED", "Authorization header is required");
        }
        String trimmed = authorizationHeader.trim();
        if (!trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new UnauthorizedException("UNAUTHORIZED", "Authorization header must use Bearer authentication");
        }
        String token = trimmed.substring(7).trim();
        if (token.isBlank()) {
            throw new UnauthorizedException("UNAUTHORIZED", "Authorization header must include a bearer token");
        }
        return token;
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String stringValue = value.toString();
        return stringValue.isBlank() ? null : stringValue;
    }
}
