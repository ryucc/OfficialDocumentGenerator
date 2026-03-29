package com.officialpapers.api.service;

public abstract class ApiException extends RuntimeException {

    private final int statusCode;
    private final String code;

    protected ApiException(int statusCode, String code, String message) {
        super(message);
        this.statusCode = statusCode;
        this.code = code;
    }

    public int statusCode() {
        return statusCode;
    }

    public String code() {
        return code;
    }
}
