package com.aibi.platform.common.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ApiResponse<T>(
        String code,
        String message,
        T data,
        @JsonProperty("trace_id") String traceId) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ApiErrorCode.SUCCESS.getCode(), ApiErrorCode.SUCCESS.getMessage(), data,
                TraceIdContext.currentTraceId());
    }

    public static <T> ApiResponse<T> error(ApiErrorCode errorCode, T data) {
        return error(errorCode.getCode(), errorCode.getMessage(), data);
    }

    public static <T> ApiResponse<T> error(String code, String message, T data) {
        return new ApiResponse<>(code, message, data, TraceIdContext.currentTraceId());
    }
}
