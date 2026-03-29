package com.officialpapers.api.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EmailFilterHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final Set<String> allowedSenders;

    public EmailFilterHandler() {
        String allowedSendersEnv = System.getenv("ALLOWED_SENDERS");
        if (allowedSendersEnv != null && !allowedSendersEnv.isEmpty()) {
            this.allowedSenders = Set.of(allowedSendersEnv.toLowerCase().split(","));
        } else {
            this.allowedSenders = Set.of("shortyliu@gmail.com");
        }
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        Map<String, Object> response = new HashMap<>();

        try {
            // Extract sender from SES event
            @SuppressWarnings("unchecked")
            Map<String, Object> sesEvent = (Map<String, Object>) event.get("Records");
            if (sesEvent == null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> records = (List<Map<String, Object>>) event.get("Records");
                if (records != null && !records.isEmpty()) {
                    sesEvent = records.get(0);
                }
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> ses = (Map<String, Object>) event.get("ses");
            if (ses == null && sesEvent != null) {
                ses = (Map<String, Object>) sesEvent.get("ses");
            }

            if (ses == null) {
                context.getLogger().log("No SES data in event, allowing by default");
                response.put("disposition", "CONTINUE");
                return response;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> mail = (Map<String, Object>) ses.get("mail");
            if (mail == null) {
                context.getLogger().log("No mail section, allowing by default");
                response.put("disposition", "CONTINUE");
                return response;
            }

            String source = (String) mail.get("source");
            if (source == null) {
                context.getLogger().log("No source found, allowing by default");
                response.put("disposition", "CONTINUE");
                return response;
            }

            context.getLogger().log("Email from: " + source);

            // Check if sender is in allowlist
            if (allowedSenders.contains(source.toLowerCase())) {
                context.getLogger().log("Sender allowed: " + source);
                response.put("disposition", "CONTINUE");
            } else {
                context.getLogger().log("Sender not in allowlist, blocking: " + source);
                response.put("disposition", "STOP_RULE");
            }

        } catch (Exception e) {
            context.getLogger().log("Error processing event: " + e.getMessage());
            // On error, stop the rule to be safe
            response.put("disposition", "STOP_RULE");
        }

        return response;
    }
}
