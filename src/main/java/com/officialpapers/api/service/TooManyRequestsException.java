package com.officialpapers.api.service;

public class TooManyRequestsException extends ApiException {

    public TooManyRequestsException(String code, String message) {
        super(429, code, message);
    }
}
