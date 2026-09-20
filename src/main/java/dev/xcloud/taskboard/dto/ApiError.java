package dev.xcloud.taskboard.dto;

import java.util.Map;

public record ApiError(int status, String error, String message, Map<String, String> fieldErrors) {

    public static ApiError of(int status, String error, String message) {
        return new ApiError(status, error, message, null);
    }

    public static ApiError ofFields(int status, String error, String message, Map<String, String> fieldErrors) {
        return new ApiError(status, error, message, fieldErrors);
    }
}