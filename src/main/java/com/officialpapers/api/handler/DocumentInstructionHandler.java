package com.officialpapers.api.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import java.util.Map;

public class DocumentInstructionHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        String method = event.getHttpMethod();
        Map<String, String> pathParams = event.getPathParameters();

        return switch (method) {
            case "GET" -> pathParams != null && pathParams.containsKey("instructionId")
                    ? notImplemented("getInstruction")
                    : notImplemented("listInstructions");
            case "POST" -> notImplemented("createInstruction");
            case "PUT" -> notImplemented("updateInstruction");
            case "DELETE" -> notImplemented("deleteInstruction");
            default -> response(405, "{\"code\":\"METHOD_NOT_ALLOWED\",\"message\":\"Method not allowed\"}");
        };
    }

    private APIGatewayProxyResponseEvent notImplemented(String operation) {
        return response(501, "{\"code\":\"NOT_IMPLEMENTED\",\"message\":\"" + operation + " not implemented\"}");
    }

    private APIGatewayProxyResponseEvent response(int statusCode, String body) {
        APIGatewayProxyResponseEvent resp = new APIGatewayProxyResponseEvent();
        resp.setStatusCode(statusCode);
        resp.setHeaders(Map.of("Content-Type", "application/json"));
        resp.setBody(body);
        return resp;
    }
}
