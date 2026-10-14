package com.aibi.platform.config;

import com.aibi.platform.common.api.TraceIdContext;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class TraceIdRestClientCustomizer implements RestClientCustomizer {

    @Override
    public void customize(RestClient.Builder restClientBuilder) {
        restClientBuilder.requestInterceptor((request, body, execution) -> {
            String traceId = TraceIdContext.currentTraceId();
            if (!"unknown".equals(traceId)) {
                request.getHeaders().set(TraceIdContext.TRACE_ID_HEADER, traceId);
            }
            return execution.execute(request, body);
        });
    }
}
