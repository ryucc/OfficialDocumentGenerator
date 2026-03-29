package com.officialpapers.api.service;

public class ServiceUnavailableException extends ApiException {

    public ServiceUnavailableException(String code, String message) {
        super(503, code, message);
    }
}
