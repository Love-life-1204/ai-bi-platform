package com.aibi.platform.common.exception;

import com.aibi.platform.common.api.ApiErrorCode;

public class ApiException extends RuntimeException {

    private final ApiErrorCode errorCode;
    private final Object data;

    public ApiException(ApiErrorCode errorCode) {
        this(errorCode, errorCode.getMessage(), null);
    }

    public ApiException(ApiErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public ApiException(ApiErrorCode errorCode, String message, Object data) {
        super(message);
        this.errorCode = errorCode;
        this.data = data;
    }

    public ApiErrorCode getErrorCode() {
        return errorCode;
    }

    public Object getData() {
        return data;
    }
}
