package com.officialpapers.api.service;

public class ConflictException extends ApiException {

    public ConflictException(String code, String message) {
        super(409, code, message);
    }
}
