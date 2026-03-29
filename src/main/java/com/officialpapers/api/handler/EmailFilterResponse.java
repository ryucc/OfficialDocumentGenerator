package com.officialpapers.api.handler;

public class EmailFilterResponse {
    private String disposition;

    public EmailFilterResponse() {
    }

    public EmailFilterResponse(String disposition) {
        this.disposition = disposition;
    }

    public String getDisposition() {
        return disposition;
    }

    public void setDisposition(String disposition) {
        this.disposition = disposition;
    }

    public static EmailFilterResponse allow() {
        return new EmailFilterResponse("CONTINUE");
    }

    public static EmailFilterResponse block() {
        return new EmailFilterResponse("STOP_RULE");
    }
}
