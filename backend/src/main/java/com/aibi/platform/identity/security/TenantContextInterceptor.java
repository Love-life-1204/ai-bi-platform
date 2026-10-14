package com.aibi.platform.identity.security;

import com.aibi.platform.common.exception.BadRequestException;
import com.aibi.platform.identity.service.PermissionContextService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.bind.annotation.RequestHeader;

/** Rejects a spoofed tenant header before any tenant-scoped controller executes. */
@Component
public class TenantContextInterceptor implements HandlerInterceptor {

    private static final String TENANT_HEADER = "X-Tenant-Id";
    private final PermissionContextService permissionContext;

    public TenantContextInterceptor(PermissionContextService permissionContext) {
        this.permissionContext = permissionContext;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String tenantHeader = request.getHeader(TENANT_HEADER);
        if (tenantHeader == null) {
            if (handler instanceof HandlerMethod method) {
                for (var parameter : method.getMethodParameters()) {
                    RequestHeader header = parameter.getParameterAnnotation(RequestHeader.class);
                    if (header != null && (TENANT_HEADER.equals(header.value())
                            || TENANT_HEADER.equals(header.name()))) {
                        // 先验证身份，缺少头不再成为匿名请求的绕过路径。
                        permissionContext.requireAuthenticatedContext(request.getHeader(HttpHeaders.AUTHORIZATION));
                        throw new BadRequestException("缺少 X-Tenant-Id 请求头");
                    }
                }
            }
            return true;
        }
        long tenantId;
        try {
            tenantId = Long.parseLong(tenantHeader);
        } catch (NumberFormatException exception) {
            throw new BadRequestException("X-Tenant-Id 必须是正整数");
        }
        if (tenantId <= 0) {
            throw new BadRequestException("X-Tenant-Id 必须是正整数");
        }
        permissionContext.requireContext(request.getHeader(HttpHeaders.AUTHORIZATION), tenantId);
        return true;
    }
}
