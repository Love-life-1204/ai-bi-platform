package com.aibi.platform.common.api;

import java.util.Optional;
import org.slf4j.MDC;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

public final class TraceIdContext {

    public static final String TRACE_ID_ATTRIBUTE = TraceIdContext.class.getName() + ".traceId";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String MDC_KEY = "trace_id";

    private TraceIdContext() {
    }

    public static String currentTraceId() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            Object traceId = attributes.getAttribute(TRACE_ID_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
            if (traceId instanceof String value && !value.isBlank()) {
                return value;
            }
        }
        return Optional.ofNullable(MDC.get(MDC_KEY)).orElse("unknown");
    }
}
