package com.aibi.platform.common.exception;

import com.aibi.platform.common.api.ApiErrorCode;

public class TooManyRequestsException extends ApiException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(long retryAfterSeconds) {
        super(ApiErrorCode.TOO_MANY_REQUESTS);
        if (retryAfterSeconds < 1) {
            throw new IllegalArgumentException("retryAfterSeconds 必须大于 0");
        }
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
