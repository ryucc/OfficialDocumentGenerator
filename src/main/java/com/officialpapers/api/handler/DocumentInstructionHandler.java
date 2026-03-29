package com.officialpapers.api.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.officialpapers.api.di.DaggerLambdaComponent;
import com.officialpapers.api.di.LambdaComponent;
import com.officialpapers.api.model.ApiError;
import com.officialpapers.api.model.DocumentInstructionCreateRequest;
import com.officialpapers.api.model.DocumentInstructionListResponse;
import com.officialpapers.api.model.DocumentInstructionUpdateRequest;
import com.officialpapers.api.service.BadRequestException;
import com.officialpapers.api.service.DocumentInstructionService;
import com.officialpapers.api.service.NotFoundException;

import javax.inject.Inject;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class DocumentInstructionHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final LambdaComponent COMPONENT = DaggerLambdaComponent.create();

    private final DocumentInstructionService instructionService;
    private final ObjectMapper objectMapper;

    public DocumentInstructionHandler() {
        this(COMPONENT.handler());
    }

    private DocumentInstructionHandler(DocumentInstructionHandler delegate) {
        this(delegate.instructionService, delegate.objectMapper);
    }

    public DocumentInstructionHandler(DocumentInstructionService instructionService) {
        this(instructionService, new ObjectMapper());
    }

    @Inject
    public DocumentInstructionHandler(DocumentInstructionService instructionService, ObjectMapper objectMapper) {
        this.instructionService = instructionService;
        this.objectMapper = objectMapper;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
            String method = event.getHttpMethod();
            Map<String, String> pathParams = event.getPathParameters();

            return switch (method) {
                case "GET" -> pathParams != null && pathParams.containsKey("instructionId")
                        ? jsonResponse(200, instructionService.getInstruction(requireInstructionId(pathParams)))
                        : jsonResponse(200, new DocumentInstructionListResponse(instructionService.listInstructions()));
                case "POST" -> jsonResponse(
                        201,
                        instructionService.createInstruction(readBody(event, DocumentInstructionCreateRequest.class))
                );
                case "PUT" -> jsonResponse(
                        200,
                        instructionService.updateInstruction(
                                requireInstructionId(pathParams),
                                readUpdateBody(event)
                        )
                );
                case "DELETE" -> deleteInstruction(pathParams);
                default -> errorResponse(405, "METHOD_NOT_ALLOWED", "Method not allowed");
            };
        } catch (BadRequestException exception) {
            return errorResponse(400, "BAD_REQUEST", exception.getMessage());
        } catch (NotFoundException exception) {
            return errorResponse(404, "NOT_FOUND", exception.getMessage());
        } catch (Exception exception) {
            return errorResponse(500, "INTERNAL_SERVER_ERROR", "Internal server error");
        }
    }

    private APIGatewayProxyResponseEvent deleteInstruction(Map<String, String> pathParams) {
        instructionService.deleteInstruction(requireInstructionId(pathParams));
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        response.setStatusCode(204);
        response.setHeaders(Map.of("Content-Type", "application/json"));
        return response;
    }

    private String requireInstructionId(Map<String, String> pathParams) {
        if (pathParams == null || !pathParams.containsKey("instructionId")) {
            throw new BadRequestException("instructionId path parameter is required");
        }

        String instructionId = pathParams.get("instructionId");
        try {
            UUID.fromString(instructionId);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("instructionId must be a valid UUID");
        }
        return instructionId;
    }

    private <T> T readBody(APIGatewayProxyRequestEvent event, Class<T> bodyType) {
        return readBody(readJson(event), bodyType);
    }

    private DocumentInstructionUpdateRequest readUpdateBody(APIGatewayProxyRequestEvent event) {
        JsonNode body = readJson(event);
        if (!body.isObject()) {
            throw new BadRequestException("Request body must be a JSON object");
        }

        Set<String> allowedFields = Set.of("title", "content");
        body.fieldNames().forEachRemaining(fieldName -> {
            if (!allowedFields.contains(fieldName)) {
                throw new BadRequestException("Unknown field: " + fieldName);
            }
        });

        return readBody(body, DocumentInstructionUpdateRequest.class);
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

    private <T> T readBody(JsonNode body, Class<T> bodyType) {
        try {
            return objectMapper.treeToValue(body, bodyType);
        } catch (JsonProcessingException exception) {
            throw new BadRequestException("Malformed JSON request body");
        }
    }

    private APIGatewayProxyResponseEvent jsonResponse(int statusCode, Object body) {
        try {
            return response(statusCode, objectMapper.writeValueAsString(body));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize response", exception);
        }
    }

    private APIGatewayProxyResponseEvent errorResponse(int statusCode, String code, String message) {
        return jsonResponse(statusCode, new ApiError(code, message, null));
    }

    private APIGatewayProxyResponseEvent response(int statusCode, String body) {
        APIGatewayProxyResponseEvent resp = new APIGatewayProxyResponseEvent();
        resp.setStatusCode(statusCode);
        resp.setHeaders(Map.of("Content-Type", "application/json"));
        resp.setBody(body);
        return resp;
    }

}
