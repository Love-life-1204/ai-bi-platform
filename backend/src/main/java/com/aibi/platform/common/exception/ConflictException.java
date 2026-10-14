package com.aibi.platform.common.exception;

import com.aibi.platform.common.api.ApiErrorCode;

public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(ApiErrorCode.CONFLICT, message);
    }
}
