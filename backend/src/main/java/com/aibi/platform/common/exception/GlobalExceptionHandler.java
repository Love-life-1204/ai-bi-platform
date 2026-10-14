package com.aibi.platform.common.exception;

import com.aibi.platform.common.api.ApiErrorCode;
import com.aibi.platform.common.api.ApiResponse;
import com.aibi.platform.common.api.TraceIdContext;
import com.aibi.platform.common.validation.FieldViolation;
import com.aibi.platform.common.validation.ValidationErrorData;
import jakarta.validation.ConstraintViolationException;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Object>> handleApiException(ApiException exception) {
        HttpHeaders headers = new HttpHeaders();
        if (exception instanceof TooManyRequestsException tooManyRequests) {
            headers.set(HttpHeaders.RETRY_AFTER, Long.toString(tooManyRequests.getRetryAfterSeconds()));
        }
        ApiErrorCode errorCode = exception.getErrorCode();
        return new ResponseEntity<>(
                ApiResponse.error(errorCode.getCode(), exception.getMessage(), exception.getData()),
                headers,
                errorCode.getHttpStatus());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ApiResponse<ValidationErrorData>> handleBindingException(BindException exception) {
        List<FieldViolation> violations = exception.getBindingResult().getAllErrors().stream()
                .map(error -> {
                    String field = error instanceof FieldError fieldError
                            ? fieldError.getField()
                            : error.getObjectName();
                    return new FieldViolation(field,
                            messageOrDefault(error, ApiErrorCode.INVALID_ARGUMENT.getMessage()));
                })
                .sorted(Comparator.comparing(FieldViolation::field).thenComparing(FieldViolation::message))
                .toList();
        return invalidArguments(violations);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<ValidationErrorData>> handleMethodValidation(
            HandlerMethodValidationException exception) {
        List<FieldViolation> violations = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream().map(error -> new FieldViolation(
                        parameterName(result.getMethodParameter().getParameterName()),
                        messageOrDefault(error, ApiErrorCode.INVALID_ARGUMENT.getMessage()))))
                .sorted(Comparator.comparing(FieldViolation::field).thenComparing(FieldViolation::message))
                .toList();
        return invalidArguments(violations);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<ValidationErrorData>> handleConstraintViolation(
            ConstraintViolationException exception) {
        List<FieldViolation> violations = exception.getConstraintViolations().stream()
                .map(violation -> new FieldViolation(violation.getPropertyPath().toString(), violation.getMessage()))
                .sorted(Comparator.comparing(FieldViolation::field).thenComparing(FieldViolation::message))
                .toList();
        return invalidArguments(violations);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<ValidationErrorData>> handleMissingParameter(
            MissingServletRequestParameterException exception) {
        return invalidArguments(List.of(new FieldViolation(exception.getParameterName(), "缺少必填参数")));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<ValidationErrorData>> handleMissingHeader(MissingRequestHeaderException exception) {
        return invalidArguments(List.of(new FieldViolation(exception.getHeaderName(), "缺少必填请求头")));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<ValidationErrorData>> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception) {
        return invalidArguments(List.of(new FieldViolation(exception.getName(), "参数类型不正确")));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<ValidationErrorData>> handleUnreadableMessage() {
        return invalidArguments(List.of(new FieldViolation("requestBody", "请求体格式错误或字段类型不正确")));
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNotFound() {
        return error(ApiErrorCode.RESOURCE_NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed() {
        return error(ApiErrorCode.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedMediaType() {
        return error(ApiErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
        // 仅在服务端记录异常，响应不暴露堆栈、SQL 或连接信息。
        log.error("未处理异常，trace_id={}", TraceIdContext.currentTraceId(), exception);
        return error(ApiErrorCode.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ApiResponse<ValidationErrorData>> invalidArguments(List<FieldViolation> violations) {
        ApiErrorCode errorCode = ApiErrorCode.INVALID_ARGUMENT;
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ApiResponse.error(errorCode, new ValidationErrorData(violations)));
    }

    private ResponseEntity<ApiResponse<Void>> error(ApiErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getHttpStatus()).body(ApiResponse.error(errorCode, null));
    }

    private String parameterName(String name) {
        return name == null || name.isBlank() ? "parameter" : name;
    }

    private String messageOrDefault(MessageSourceResolvable error, String defaultMessage) {
        String message = error.getDefaultMessage();
        return message == null || message.isBlank() ? defaultMessage : message;
    }
}
