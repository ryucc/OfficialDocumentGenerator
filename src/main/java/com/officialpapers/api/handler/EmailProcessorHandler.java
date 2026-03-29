package com.officialpapers.api.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class EmailProcessorHandler implements RequestHandler<SQSEvent, String> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String handleRequest(SQSEvent event, Context context) {
        context.getLogger().log("Received " + event.getRecords().size() + " email message(s) from allowed senders");

        for (SQSEvent.SQSMessage message : event.getRecords()) {
            try {
                processMessage(message, context);
            } catch (Exception e) {
                context.getLogger().log("Error processing message: " + e.getMessage());
            }
        }

        return "OK";
    }

    private void processMessage(SQSEvent.SQSMessage sqsMessage, Context context) throws Exception {
        // Parse SES notification
        JsonNode body = objectMapper.readTree(sqsMessage.getBody());
        JsonNode messageNode = body.get("Message");

        if (messageNode == null || !messageNode.isTextual()) {
            context.getLogger().log("Not an SES notification, skipping");
            return;
        }

        JsonNode sesMessage = objectMapper.readTree(messageNode.asText());
        JsonNode mail = sesMessage.get("mail");

        if (mail == null) {
            context.getLogger().log("No mail section in SES notification");
            return;
        }

        String source = mail.get("source").asText();
        String subject = mail.has("commonHeaders") && mail.get("commonHeaders").has("subject")
            ? mail.get("commonHeaders").get("subject").asText()
            : "No subject";

        context.getLogger().log("Processing email from: " + source + ", subject: " + subject);

        // TODO: Add email processing logic here (parse attachments, extract text, store in DynamoDB)
    }
}
