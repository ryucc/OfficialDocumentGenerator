package com.officialpapers.api.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.officialpapers.api.di.DaggerLambdaComponent;
import com.officialpapers.api.di.LambdaComponent;
import com.officialpapers.api.generated.model.ApiError;
import com.officialpapers.api.service.ApiException;
import com.officialpapers.api.service.AuthService;
import com.officialpapers.api.service.BadRequestException;
import com.officialpapers.domain.AuthenticatedUser;

import javax.inject.Inject;
import java.util.Map;
import java.util.Set;

public class AuthHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final LambdaComponent COMPONENT = DaggerLambdaComponent.create();

    private final AuthService authService;
    private final SampleDocumentApiMapper apiMapper;
    private final RequestAuthenticationResolver authenticationResolver;
    private final ObjectMapper objectMapper;

    public AuthHandler() {
        this(COMPONENT.authHandler());
    }

    private AuthHandler(AuthHandler delegate) {
        this(delegate.authService, delegate.apiMapper, delegate.authenticationResolver, delegate.objectMapper);
    }

    public AuthHandler(AuthService authService) {
        this(authService, new SampleDocumentApiMapper(), new RequestAuthenticationResolver(), defaultObjectMapper());
    }

    @Inject
    public AuthHandler(
            AuthService authService,
            SampleDocumentApiMapper apiMapper,
            RequestAuthenticationResolver authenticationResolver,
            ObjectMapper objectMapper
    ) {
        this.authService = authService;
        this.apiMapper = apiMapper;
        this.authenticationResolver = authenticationResolver;
        this.objectMapper = objectMapper;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
            String method = event.getHttpMethod();
            String path = normalizePath(event.getPath());

            return switch (method) {
                case "OPTIONS" -> apiMapper.toOptionsResponse();
                case "GET" -> handleGet(path, event);
                case "POST" -> handlePost(path, event);
                default -> errorResponse(405, "METHOD_NOT_ALLOWED", "Method not allowed");
            };
        } catch (ApiException exception) {
            return errorResponse(exception.statusCode(), exception.code(), exception.getMessage());
        } catch (Exception exception) {
            return errorResponse(500, "INTERNAL_SERVER_ERROR", "Internal server error");
        }
    }

    private APIGatewayProxyResponseEvent handleGet(String path, APIGatewayProxyRequestEvent event) {
        if (path.endsWith("/auth/me")) {
            AuthenticatedUser currentUser = authService.getCurrentUser(authenticationResolver.requireAccessToken(event));
            return jsonResponse(200, new CurrentUserResponse(
                    currentUser.userId(),
                    currentUser.email(),
                    currentUser.emailVerified()
            ));
        }
        return errorResponse(404, "NOT_FOUND", "Route not found");
    }

    private APIGatewayProxyResponseEvent handlePost(String path, APIGatewayProxyRequestEvent event) {
        if (path.endsWith("/auth/login")) {
            CredentialsRequest request = credentialsRequest(event);
            return jsonResponse(200, authService.login(request.email(), request.password()));
        }
        if (path.endsWith("/auth/refresh")) {
            RefreshRequest request = refreshRequest(event);
            return jsonResponse(200, authService.refresh(request.refreshToken()));
        }
        if (path.endsWith("/auth/respond-to-new-password")) {
            NewPasswordChallengeRequest request = newPasswordChallengeRequest(event);
            return jsonResponse(200, authService.respondToNewPassword(
                    request.email(),
                    request.newPassword(),
                    request.session()
            ));
        }
        if (path.endsWith("/auth/forgot-password")) {
            EmailOnlyRequest request = emailOnlyRequest(event);
            authService.forgotPassword(request.email());
            return apiMapper.toNoContentResponse();
        }
        if (path.endsWith("/auth/confirm-forgot-password")) {
            ResetPasswordRequest request = resetPasswordRequest(event);
            authService.confirmForgotPassword(request.email(), request.confirmationCode(), request.newPassword());
            return apiMapper.toNoContentResponse();
        }
        if (path.endsWith("/auth/logout")) {
            RefreshRequest request = refreshRequest(event);
            authService.logout(authenticationResolver.requireAccessToken(event), request.refreshToken());
            return apiMapper.toNoContentResponse();
        }
        return errorResponse(404, "NOT_FOUND", "Route not found");
    }

    private CredentialsRequest credentialsRequest(APIGatewayProxyRequestEvent event) {
        JsonNode body = readJsonObject(event, Set.of("email", "password"));
        return new CredentialsRequest(
                requiredText(body, "email"),
                requiredText(body, "password")
        );
    }

    private EmailOnlyRequest emailOnlyRequest(APIGatewayProxyRequestEvent event) {
        JsonNode body = readJsonObject(event, Set.of("email"));
        return new EmailOnlyRequest(requiredText(body, "email"));
    }

    private RefreshRequest refreshRequest(APIGatewayProxyRequestEvent event) {
        JsonNode body = readJsonObject(event, Set.of("refreshToken"));
        return new RefreshRequest(requiredText(body, "refreshToken"));
    }

    private ResetPasswordRequest resetPasswordRequest(APIGatewayProxyRequestEvent event) {
        JsonNode body = readJsonObject(event, Set.of("email", "confirmationCode", "newPassword"));
        return new ResetPasswordRequest(
                requiredText(body, "email"),
                requiredText(body, "confirmationCode"),
                requiredText(body, "newPassword")
        );
    }

    private NewPasswordChallengeRequest newPasswordChallengeRequest(APIGatewayProxyRequestEvent event) {
        JsonNode body = readJsonObject(event, Set.of("email", "newPassword", "session"));
        return new NewPasswordChallengeRequest(
                requiredText(body, "email"),
                requiredText(body, "newPassword"),
                requiredText(body, "session")
        );
    }

    private JsonNode readJsonObject(APIGatewayProxyRequestEvent event, Set<String> allowedFields) {
        JsonNode body = readJson(event);
        if (!body.isObject()) {
            throw new BadRequestException("Request body must be a JSON object");
        }
        body.fieldNames().forEachRemaining(fieldName -> {
            if (!allowedFields.contains(fieldName)) {
                throw new BadRequestException("Unknown field: " + fieldName);
            }
        });
        return body;
    }

    private JsonNode readJson(APIGatewayProxyRequestEvent event) {
        String body = event.getBody();
        if (body == null || body.isBlank()) {
            throw new BadRequestException("Request body is required");
        }

        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException exception) {
            throw new BadRequestException("Malformed JSON request body");
        }
    }

    private String requiredText(JsonNode body, String fieldName) {
        JsonNode field = body.get(fieldName);
        if (field == null || field.isNull()) {
            throw new BadRequestException(fieldName + " is required");
        }
        if (!field.isTextual()) {
            throw new BadRequestException(fieldName + " must be a string");
        }
        String value = field.asText();
        if (value == null || value.isBlank()) {
            throw new BadRequestException(fieldName + " is required");
        }
        return value;
    }

    private APIGatewayProxyResponseEvent jsonResponse(int statusCode, Object body) {
        try {
            return response(statusCode, objectMapper.writeValueAsString(body));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize response", exception);
        }
    }

    private APIGatewayProxyResponseEvent errorResponse(int statusCode, String code, String message) {
        return jsonResponse(statusCode, new ApiError()
                .code(code)
                .message(message));
    }

    private APIGatewayProxyResponseEvent response(int statusCode, String body) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        response.setStatusCode(statusCode);
        response.setHeaders(apiMapper.corsHeaders());
        response.setBody(body);
        return response;
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        return path.replaceAll("/+$", "");
    }

    private static ObjectMapper defaultObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    private record CredentialsRequest(String email, String password) {
    }

    private record EmailOnlyRequest(String email) {
    }

    private record RefreshRequest(String refreshToken) {
    }

    private record ResetPasswordRequest(String email, String confirmationCode, String newPassword) {
    }

    private record NewPasswordChallengeRequest(String email, String newPassword, String session) {
    }

    private record CurrentUserResponse(String userId, String email, boolean emailVerified) {
    }
}
