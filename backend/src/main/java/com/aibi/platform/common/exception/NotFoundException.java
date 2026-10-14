package com.aibi.platform.common.exception;

import com.aibi.platform.common.api.ApiErrorCode;

public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super(ApiErrorCode.RESOURCE_NOT_FOUND, message);
    }
}
