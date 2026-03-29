package com.officialpapers.api.service;

public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super(400, "BAD_REQUEST", message);
    }
}
