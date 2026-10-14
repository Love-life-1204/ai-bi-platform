package com.aibi.platform.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.media.StringSchema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    @Bean
    public OpenAPI platformOpenApi() {
        Parameter traceId = new HeaderParameter()
                .name("X-Trace-Id")
                .description("可选的链路追踪标识，8—64 位安全字符")
                .required(false)
                .schema(new StringSchema());
        return new OpenAPI()
                .info(new Info()
                        .title("AI BI 平台 API")
                        .version("1.1.0")
                        .description("公共业务接口统一使用 /api 根路径；所有 JSON API 均返回 "
                                + "code、message、data、trace_id 统一信封。")
                        .contact(new Contact().name("AI BI 平台团队")))
                .components(new Components().addParameters("TraceId", traceId));
    }

    @Bean
    public OpenApiCustomizer excludeLegacyVersionedPaths() {
        return openApi -> {
            if (openApi.getPaths() != null) {
                openApi.getPaths().entrySet().removeIf(entry -> entry.getKey().startsWith("/api/v1/"));
            }
        };
    }
}
