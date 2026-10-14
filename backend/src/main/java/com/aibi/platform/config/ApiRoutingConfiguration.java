package com.aibi.platform.config;

import com.aibi.platform.identity.security.TenantContextInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class ApiRoutingConfiguration implements WebMvcConfigurer {

    private static final String LEGACY_API_PATTERN = "/api/v1/**";
    private final TenantContextInterceptor tenantContextInterceptor;

    public ApiRoutingConfiguration(TenantContextInterceptor tenantContextInterceptor) {
        this.tenantContextInterceptor = tenantContextInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantContextInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/test/**");
        registry.addInterceptor(new LegacyApiDeprecationInterceptor())
                .addPathPatterns(LEGACY_API_PATTERN);
    }

    private static final class LegacyApiDeprecationInterceptor implements HandlerInterceptor {

        @Override
        public boolean preHandle(
                HttpServletRequest request,
                HttpServletResponse response,
                Object handler) {
            response.setHeader("Deprecation", "true");
            response.setHeader("Warning", "299 - \"Deprecated API path; migrate to /api\"");
            return true;
        }
    }
}
