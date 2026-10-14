package com.aibi.platform.common.json;

import com.aibi.platform.common.exception.BadRequestException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.Map;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Only annotated ID arguments use this parser; versions and metrics retain their contract. */
@Configuration(proxyBeanMethods = false)
public class ResourceIdWebConfiguration implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new IdArgumentInterceptor()).addPathPatterns("/api/**").order(10);
    }

    private static final class IdArgumentInterceptor implements HandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            if (!(handler instanceof HandlerMethod method)) {
                return true;
            }
            for (var parameter : method.getMethodParameters()) {
                if (!parameter.hasParameterAnnotation(JsonResourceId.class)) {
                    continue;
                }
                String name = parameter.getParameter().getName();
                String token = null;
                var path = parameter.getParameterAnnotation(PathVariable.class);
                var query = parameter.getParameterAnnotation(RequestParam.class);
                var header = parameter.getParameterAnnotation(RequestHeader.class);
                if (path != null) {
                    name = annotationName(path.name(), path.value(), name);
                    var variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
                    if (variables instanceof Map<?, ?> map) {
                        token = (String) map.get(name);
                    }
                } else if (query != null) {
                    name = annotationName(query.name(), query.value(), name);
                    String[] values = request.getParameterValues(name);
                    if (values != null) {
                        if (values.length != 1) {
                            throw new BadRequestException("资源 ID 参数不允许重复: " + name);
                        }
                        token = values[0];
                    }
                } else if (header != null) {
                    name = annotationName(header.name(), header.value(), name);
                    var values = Collections.list(request.getHeaders(name));
                    if (values.size() > 1) {
                        throw new BadRequestException("资源 ID 请求头不允许重复: " + name);
                    }
                    token = values.isEmpty() ? null : values.get(0);
                }
                if (token != null) {
                    try {
                        ResourceIds.parse(token);
                    } catch (IllegalArgumentException exception) {
                        throw new BadRequestException("资源 ID 必须是规范正整数且不超出 64 位范围: " + name);
                    }
                }
            }
            return true;
        }

        private static String annotationName(String name, String value, String fallback) {
            return !name.isEmpty() ? name : !value.isEmpty() ? value : fallback;
        }
    }
}
