package com.officialpapers.api.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Set;

public class EmailFilterHandler implements RequestHandler<SesReceiptEvent, EmailFilterResponse> {

    private final Set<String> allowedSenders;
    private final ObjectMapper objectMapper;

    public EmailFilterHandler() {
        String allowedSendersEnv = System.getenv("ALLOWED_SENDERS");
        if (allowedSendersEnv != null && !allowedSendersEnv.isEmpty()) {
            this.allowedSenders = Set.of(allowedSendersEnv.toLowerCase().split(","));
        } else {
            this.allowedSenders = Set.of("shortyliu@gmail.com");
        }
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public EmailFilterResponse handleRequest(SesReceiptEvent event, Context context) {
        try {
            context.getLogger().log("Received SES event: " + objectMapper.writeValueAsString(event));

            if (event.getRecords() == null || event.getRecords().isEmpty()) {
                context.getLogger().log("No records in event, blocking");
                return EmailFilterResponse.block();
            }

            // Process first record (SES Lambda actions receive one record)
            SesReceiptEvent.Record record = event.getRecords().get(0);

            EmailVerifier verifier = new EmailVerifier(allowedSenders, context.getLogger());
            if (verifier.isAllowed(record)) {
                return EmailFilterResponse.allow();
            } else {
                return EmailFilterResponse.block();
            }

        } catch (Exception e) {
            context.getLogger().log("Error processing event: " + e.getMessage());
            e.printStackTrace();
            // On error, block to be safe
            return EmailFilterResponse.block();
        }
    }
}
