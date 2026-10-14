package com.aibi.platform.common.api;

import org.springframework.http.HttpStatus;

public enum ApiErrorCode {
    SUCCESS("SUCCESS", "请求成功", HttpStatus.OK),
    INVALID_ARGUMENT("INVALID_ARGUMENT", "请求参数不合法", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED("UNAUTHORIZED", "请先登录或重新登录", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("FORBIDDEN", "无权执行此操作", HttpStatus.FORBIDDEN),
    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND", "请求的资源不存在", HttpStatus.NOT_FOUND),
    CONFLICT("CONFLICT", "资源状态冲突", HttpStatus.CONFLICT),
    TENANT_CODE_ALREADY_EXISTS("TENANT_CODE_ALREADY_EXISTS", "租户编码已存在", HttpStatus.CONFLICT),
    DATASOURCE_CODE_ALREADY_EXISTS("DATASOURCE_CODE_ALREADY_EXISTS", "数据源编码已存在", HttpStatus.CONFLICT),
    DATASOURCE_AUTH_FAILED("DATASOURCE_AUTH_FAILED", "数据源认证失败", HttpStatus.BAD_REQUEST),
    DATASOURCE_NETWORK_TIMEOUT("DATASOURCE_NETWORK_TIMEOUT", "数据源网络连接超时", HttpStatus.GATEWAY_TIMEOUT),
    DATASOURCE_PERMISSION_DENIED("DATASOURCE_PERMISSION_DENIED", "源账号不是只读账号", HttpStatus.FORBIDDEN),
    DATASOURCE_TARGET_BLOCKED("DATASOURCE_TARGET_BLOCKED", "数据源目标地址不符合安全策略", HttpStatus.BAD_REQUEST),
    DATASOURCE_CONNECTION_FAILED("DATASOURCE_CONNECTION_FAILED", "数据源连接失败", HttpStatus.BAD_GATEWAY),
    OPTIMISTIC_LOCK_CONFLICT("OPTIMISTIC_LOCK_CONFLICT", "数据已被修改，请刷新后重试", HttpStatus.CONFLICT),
    IDEMPOTENCY_KEY_REUSED("IDEMPOTENCY_KEY_REUSED", "幂等键已用于其他请求", HttpStatus.CONFLICT),
    IDEMPOTENCY_REQUEST_IN_PROGRESS("IDEMPOTENCY_REQUEST_IN_PROGRESS", "相同请求正在处理", HttpStatus.CONFLICT),
    TOO_MANY_REQUESTS("TOO_MANY_REQUESTS", "请求过于频繁，请稍后重试", HttpStatus.TOO_MANY_REQUESTS),
    QUOTA_EXCEEDED("QUOTA_EXCEEDED", "套餐配额不足", HttpStatus.TOO_MANY_REQUESTS),
    DATA_NOT_READY("DATA_NOT_READY", "已发布数据尚未就绪", HttpStatus.CONFLICT),
    QUERY_TIMEOUT("QUERY_TIMEOUT", "查询执行超时", HttpStatus.GATEWAY_TIMEOUT),
    QUERY_CANCELLED("QUERY_CANCELLED", "查询已取消", HttpStatus.CONFLICT),
    QUERY_EXECUTION_FAILED("QUERY_EXECUTION_FAILED", "查询执行失败", HttpStatus.BAD_GATEWAY),
    AI_SERVICE_UNAVAILABLE("AI_SERVICE_UNAVAILABLE", "智能分析服务暂时不可用", HttpStatus.SERVICE_UNAVAILABLE),
    AI_TASK_TIMEOUT("AI_TASK_TIMEOUT", "智能分析任务已超时", HttpStatus.GATEWAY_TIMEOUT),
    METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED", "请求方法不受支持", HttpStatus.METHOD_NOT_ALLOWED),
    UNSUPPORTED_MEDIA_TYPE("UNSUPPORTED_MEDIA_TYPE", "请求内容类型不受支持", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", "系统内部错误", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    ApiErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
