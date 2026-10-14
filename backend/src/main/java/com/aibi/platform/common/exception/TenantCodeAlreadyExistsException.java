package com.aibi.platform.common.exception;

import com.aibi.platform.common.api.ApiErrorCode;

public class TenantCodeAlreadyExistsException extends ApiException {

    public TenantCodeAlreadyExistsException() {
        super(ApiErrorCode.TENANT_CODE_ALREADY_EXISTS);
    }
}
