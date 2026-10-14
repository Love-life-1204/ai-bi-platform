package com.aibi.platform.common.exception;

import com.aibi.platform.common.api.ApiErrorCode;

public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super(ApiErrorCode.INVALID_ARGUMENT, message);
    }
}
