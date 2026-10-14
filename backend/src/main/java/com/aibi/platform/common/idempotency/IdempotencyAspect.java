package com.aibi.platform.common.idempotency;

import com.aibi.platform.common.api.ApiErrorCode;
import com.aibi.platform.common.exception.ApiException;
import com.aibi.platform.common.exception.BadRequestException;
import com.aibi.platform.manager.IdempotencyManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.regex.Pattern;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class IdempotencyAspect {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String TENANT_ID_HEADER = "X-Tenant-Id";
    public static final String MEMBER_ID_HEADER = "X-Member-Id";
    private static final Pattern SAFE_KEY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{7,127}");

    private final IdempotencyManager manager;
    private final ObjectMapper objectMapper;

    public IdempotencyAspect(IdempotencyManager manager, ObjectMapper objectMapper) {
        this.manager = manager;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(idempotent)")
    public Object execute(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new IllegalStateException("幂等处理只能在 HTTP 请求中使用");
        }
        var request = attributes.getRequest();
        String key = requireSafeKey(request.getHeader(IDEMPOTENCY_KEY_HEADER));
        long tenantId = requirePositiveLong(request.getHeader(TENANT_ID_HEADER), TENANT_ID_HEADER);
        long memberId = requirePositiveLong(request.getHeader(MEMBER_ID_HEADER), MEMBER_ID_HEADER);
        String route = request.getMethod() + " " + request.getRequestURI();
        if (route.length() > 128) {
            throw new BadRequestException("请求路由过长");
        }

        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        String requestHash = hashRequest(route, joinPoint.getArgs());
        IdempotencyScope scope = new IdempotencyScope(tenantId, memberId, route, key);
        IdempotencyClaim claim = manager.claim(scope, requestHash, Duration.ofSeconds(idempotent.ttlSeconds()));

        if (claim.state() == IdempotencyClaim.State.REPLAY) {
            return deserialize(claim.responseJson(), method);
        }
        if (claim.state() == IdempotencyClaim.State.HASH_CONFLICT) {
            throw new ApiException(ApiErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
        if (claim.state() == IdempotencyClaim.State.IN_PROGRESS) {
            throw new ApiException(ApiErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS);
        }

        try {
            Object result = joinPoint.proceed();
            manager.complete(scope, requestHash, objectMapper.writeValueAsString(result));
            return result;
        } catch (Throwable throwable) {
            manager.fail(scope, requestHash);
            throw throwable;
        }
    }

    private String requireSafeKey(String key) {
        if (key == null || !SAFE_KEY.matcher(key).matches()) {
            throw new BadRequestException("Idempotency-Key 必须为 8—128 位安全字符");
        }
        return key;
    }

    private long requirePositiveLong(String value, String headerName) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // 统一转换为安全的参数错误。
        }
        throw new BadRequestException(headerName + " 必须为正整数");
    }

    private String hashRequest(String route, Object[] arguments) {
        Object[] serializableArguments = Arrays.stream(arguments)
                .filter(argument -> !(argument instanceof ServletRequest))
                .filter(argument -> !(argument instanceof ServletResponse))
                .toArray();
        try {
            byte[] body = objectMapper.writeValueAsBytes(serializableArguments);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(route.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            return HexFormat.of().formatHex(digest.digest(body));
        } catch (JsonProcessingException exception) {
            throw new BadRequestException("无法生成幂等请求摘要");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 不支持 SHA-256", exception);
        }
    }

    private Object deserialize(String responseJson, Method method) throws JsonProcessingException {
        if (Void.TYPE.equals(method.getReturnType())) {
            return null;
        }
        JavaType returnType = objectMapper.getTypeFactory().constructType(method.getGenericReturnType());
        return objectMapper.readValue(responseJson, returnType);
    }
}
