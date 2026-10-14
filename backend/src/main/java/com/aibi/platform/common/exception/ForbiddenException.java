package com.aibi.platform.common.exception;

import com.aibi.platform.common.api.ApiErrorCode;

public class ForbiddenException extends ApiException {

    public ForbiddenException() {
        super(ApiErrorCode.FORBIDDEN);
    }
}
