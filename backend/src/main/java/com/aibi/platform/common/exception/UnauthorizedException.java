package com.aibi.platform.common.exception;

import com.aibi.platform.common.api.ApiErrorCode;

public class UnauthorizedException extends ApiException {

    public UnauthorizedException() {
        super(ApiErrorCode.UNAUTHORIZED);
    }
}
