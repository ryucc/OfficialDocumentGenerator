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
import com.officialpapers.api.generated.model.CreateSampleDocumentRequest;
import com.officialpapers.api.service.BadRequestException;
import com.officialpapers.api.service.ConflictException;
import com.officialpapers.api.service.NotFoundException;
import com.officialpapers.api.service.UploadedDocumentService;

import javax.inject.Inject;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SampleDocumentHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final LambdaComponent COMPONENT = DaggerLambdaComponent.create();

    private final UploadedDocumentService documentService;
    private final SampleDocumentApiMapper apiMapper;
    private final ObjectMapper objectMapper;

    public SampleDocumentHandler() {
        this(COMPONENT.handler());
    }

    private SampleDocumentHandler(SampleDocumentHandler delegate) {
        this(delegate.documentService, delegate.apiMapper, delegate.objectMapper);
    }

    public SampleDocumentHandler(UploadedDocumentService documentService) {
        this(documentService, new SampleDocumentApiMapper(), defaultObjectMapper());
    }

    @Inject
    public SampleDocumentHandler(
            UploadedDocumentService documentService,
            SampleDocumentApiMapper apiMapper,
            ObjectMapper objectMapper
    ) {
        this.documentService = documentService;
        this.apiMapper = apiMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
            String method = event.getHttpMethod();
            Map<String, String> pathParams = event.getPathParameters();
            String path = event.getPath() == null ? "" : event.getPath();

            return switch (method) {
                case "OPTIONS" -> apiMapper.toOptionsResponse();
                case "GET" -> handleGet(path, pathParams);
                case "POST" -> handlePost(event, path, pathParams);
                case "DELETE" -> deleteDocument(pathParams);
                default -> errorResponse(405, "METHOD_NOT_ALLOWED", "Method not allowed");
            };
        } catch (BadRequestException exception) {
            return errorResponse(400, "BAD_REQUEST", exception.getMessage());
        } catch (ConflictException exception) {
            return errorResponse(409, exception.code(), exception.getMessage());
        } catch (NotFoundException exception) {
            return errorResponse(404, "NOT_FOUND", exception.getMessage());
        } catch (Exception exception) {
            return errorResponse(500, "INTERNAL_SERVER_ERROR", "Internal server error");
        }
    }

    private APIGatewayProxyResponseEvent handleGet(String path, Map<String, String> pathParams) {
        if (hasDocumentId(pathParams) && path.endsWith("/download-url")) {
            return jsonResponse(200, apiMapper.toApi(
                    documentService.createDownloadTarget(requireDocumentId(pathParams))
            ));
        }
        if (hasDocumentId(pathParams)) {
            return jsonResponse(200, apiMapper.toApi(documentService.getDocument(requireDocumentId(pathParams))));
        }
        return jsonResponse(200, apiMapper.toApiList(documentService.listDocuments()));
    }

    private APIGatewayProxyResponseEvent handlePost(
            APIGatewayProxyRequestEvent event,
            String path,
            Map<String, String> pathParams
    ) {
        if (hasDocumentId(pathParams) && path.endsWith("/complete")) {
            return jsonResponse(200, apiMapper.toApi(documentService.completeUpload(requireDocumentId(pathParams))));
        }
        return jsonResponse(
                201,
                apiMapper.toApi(
                        documentService.createDocument(
                                apiMapper.toDomain(readCreateBody(event))
                        )
                )
        );
    }

    private APIGatewayProxyResponseEvent deleteDocument(Map<String, String> pathParams) {
        documentService.deleteDocument(requireDocumentId(pathParams));
        return apiMapper.toNoContentResponse();
    }

    private boolean hasDocumentId(Map<String, String> pathParams) {
        return pathParams != null && pathParams.containsKey("documentId");
    }

    private String requireDocumentId(Map<String, String> pathParams) {
        if (!hasDocumentId(pathParams)) {
            throw new BadRequestException("documentId path parameter is required");
        }

        String documentId = pathParams.get("documentId");
        try {
            UUID.fromString(documentId);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("documentId must be a valid UUID");
        }
        return documentId;
    }

    private CreateSampleDocumentRequest readCreateBody(APIGatewayProxyRequestEvent event) {
        JsonNode body = readJson(event);
        if (!body.isObject()) {
            throw new BadRequestException("Request body must be a JSON object");
        }

        Set<String> allowedFields = Set.of("filename", "contentType", "sizeBytes");
        body.fieldNames().forEachRemaining(fieldName -> {
            if (!allowedFields.contains(fieldName)) {
                throw new BadRequestException("Unknown field: " + fieldName);
            }
        });

        validateStringField(body, "filename", true);
        validateStringField(body, "contentType", true);
        validateSizeField(body);

        return new CreateSampleDocumentRequest()
                .filename(textValue(body, "filename"))
                .contentType(textValue(body, "contentType"))
                .sizeBytes(longValue(body, "sizeBytes"));
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

    private void validateStringField(JsonNode body, String fieldName, boolean required) {
        JsonNode field = body.get(fieldName);
        if (field == null || field.isNull()) {
            if (required) {
                throw new BadRequestException(fieldName + " is required");
            }
            return;
        }
        if (!field.isTextual()) {
            throw new BadRequestException(fieldName + " must be a string");
        }
    }

    private void validateSizeField(JsonNode body) {
        JsonNode field = body.get("sizeBytes");
        if (field == null || field.isNull()) {
            return;
        }
        if (!field.canConvertToLong()) {
            throw new BadRequestException("sizeBytes must be an integer");
        }
        if (field.longValue() < 0) {
            throw new BadRequestException("sizeBytes must be greater than or equal to 0");
        }
    }

    private String textValue(JsonNode body, String fieldName) {
        JsonNode field = body.get(fieldName);
        return field == null || field.isNull() ? null : field.asText();
    }

    private Long longValue(JsonNode body, String fieldName) {
        JsonNode field = body.get(fieldName);
        return field == null || field.isNull() ? null : field.longValue();
    }

    private APIGatewayProxyResponseEvent jsonResponse(int statusCode, Object body) {
        try {
            return response(statusCode, objectMapper.writeValueAsString(body));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize response", exception);
        }
    }

    private APIGatewayProxyResponseEvent errorResponse(int statusCode, String code, String message) {
        return jsonResponse(statusCode, apiMapper.toApiError(code, message));
    }

    private APIGatewayProxyResponseEvent response(int statusCode, String body) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        response.setStatusCode(statusCode);
        response.setHeaders(apiMapper.corsHeaders());
        response.setBody(body);
        return response;
    }

    private static ObjectMapper defaultObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
