package com.officialpapers.api.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.*;

public class DocumentInstructionHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbClient dynamoDb = DynamoDbClient.create();
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final String tableName = System.getenv("TABLE_NAME");

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        String method = event.getHttpMethod();
        String path = event.getPath();
        Map<String, String> pathParams = event.getPathParameters();

        try {
            return switch (method) {
                case "GET" -> pathParams != null && pathParams.containsKey("instructionId")
                        ? getInstruction(pathParams.get("instructionId"))
                        : listInstructions();
                case "POST" -> createInstruction(event.getBody());
                case "PUT" -> updateInstruction(pathParams.get("instructionId"), event.getBody());
                case "DELETE" -> deleteInstruction(pathParams.get("instructionId"));
                default -> response(405, Map.of("code", "METHOD_NOT_ALLOWED", "message", "Method not allowed"));
            };
        } catch (IllegalArgumentException e) {
            return response(400, Map.of("code", "BAD_REQUEST", "message", e.getMessage()));
        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return response(500, Map.of("code", "INTERNAL_ERROR", "message", "An internal error occurred"));
        }
    }

    private APIGatewayProxyResponseEvent listInstructions() throws Exception {
        ScanResponse result = dynamoDb.scan(ScanRequest.builder().tableName(tableName).build());
        List<Map<String, String>> items = result.items().stream()
                .map(this::itemToMap)
                .toList();
        return response(200, Map.of("items", items));
    }

    private APIGatewayProxyResponseEvent createInstruction(String body) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, String> request = mapper.readValue(body, Map.class);
        String title = request.get("title");
        String content = request.get("content");
        if (title == null || content == null) {
            throw new IllegalArgumentException("title and content are required");
        }

        String id = UUID.randomUUID().toString();
        String now = Instant.now().toString();

        Map<String, AttributeValue> item = Map.of(
                "id", AttributeValue.fromS(id),
                "title", AttributeValue.fromS(title),
                "content", AttributeValue.fromS(content),
                "createdAt", AttributeValue.fromS(now),
                "updatedAt", AttributeValue.fromS(now)
        );

        dynamoDb.putItem(PutItemRequest.builder().tableName(tableName).item(item).build());
        return response(201, itemToMap(item));
    }

    private APIGatewayProxyResponseEvent getInstruction(String id) throws Exception {
        GetItemResponse result = dynamoDb.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("id", AttributeValue.fromS(id)))
                .build());

        if (!result.hasItem() || result.item().isEmpty()) {
            return response(404, Map.of("code", "NOT_FOUND", "message", "Instruction not found"));
        }
        return response(200, itemToMap(result.item()));
    }

    private APIGatewayProxyResponseEvent updateInstruction(String id, String body) throws Exception {
        GetItemResponse existing = dynamoDb.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("id", AttributeValue.fromS(id)))
                .build());

        if (!existing.hasItem() || existing.item().isEmpty()) {
            return response(404, Map.of("code", "NOT_FOUND", "message", "Instruction not found"));
        }

        @SuppressWarnings("unchecked")
        Map<String, String> request = mapper.readValue(body, Map.class);
        String now = Instant.now().toString();

        Map<String, AttributeValue> item = new HashMap<>(existing.item());
        if (request.containsKey("title")) {
            item.put("title", AttributeValue.fromS(request.get("title")));
        }
        if (request.containsKey("content")) {
            item.put("content", AttributeValue.fromS(request.get("content")));
        }
        item.put("updatedAt", AttributeValue.fromS(now));

        dynamoDb.putItem(PutItemRequest.builder().tableName(tableName).item(item).build());
        return response(200, itemToMap(item));
    }

    private APIGatewayProxyResponseEvent deleteInstruction(String id) {
        dynamoDb.deleteItem(DeleteItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("id", AttributeValue.fromS(id)))
                .build());
        return response(204, null);
    }

    private Map<String, String> itemToMap(Map<String, AttributeValue> item) {
        Map<String, String> map = new LinkedHashMap<>();
        item.forEach((k, v) -> map.put(k, v.s()));
        return map;
    }

    private APIGatewayProxyResponseEvent response(int statusCode, Object body) {
        APIGatewayProxyResponseEvent resp = new APIGatewayProxyResponseEvent();
        resp.setStatusCode(statusCode);
        resp.setHeaders(Map.of("Content-Type", "application/json"));
        if (body != null) {
            try {
                resp.setBody(mapper.writeValueAsString(body));
            } catch (Exception e) {
                resp.setBody("{\"code\":\"INTERNAL_ERROR\",\"message\":\"Serialization error\"}");
            }
        }
        return resp;
    }
}
