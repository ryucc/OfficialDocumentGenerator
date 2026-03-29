package com.officialpapers.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiErrorDto {

    private String code;
    private String message;
    private Map<String, Object> details;

    public ApiErrorDto(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
