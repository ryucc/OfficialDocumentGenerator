package com.officialpapers.api.service;

public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String code, String message) {
        super(401, code, message);
    }
}
