package com.aibi.platform;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aibi.platform.common.api.TraceIdContext;
import com.aibi.platform.common.idempotency.Idempotent;
import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.common.exception.ForbiddenException;
import com.aibi.platform.common.exception.TooManyRequestsException;
import com.aibi.platform.common.exception.UnauthorizedException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ApiContractIntegrationTest.ContractTestController.class)
@ActiveProfiles("test")
class ApiContractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldWrapSuccessfulResponseAndPropagateTraceId() throws Exception {
        String traceId = "test-trace-123456";

        mockMvc.perform(get("/api/system/status")
                        .header(TraceIdContext.TRACE_ID_HEADER, traceId))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, traceId))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("请求成功"))
                .andExpect(jsonPath("$.data.serviceName").value("ai-bi-backend"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.trace_id").value(traceId));
    }

    @Test
    void shouldReplaceUnsafeTraceId() throws Exception {
        mockMvc.perform(get("/api/system/status")
                        .header(TraceIdContext.TRACE_ID_HEADER, "bad trace\r\nvalue"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, matchesPattern("[a-f0-9]{32}")))
                .andExpect(jsonPath("$.trace_id", matchesPattern("[a-f0-9]{32}")));
    }

    @Test
    void shouldReturnStructuredValidationErrors() throws Exception {
        mockMvc.perform(post("/api/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"a\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value("请求参数不合法"))
                .andExpect(jsonPath("$.data.errors", hasSize(1)))
                .andExpect(jsonPath("$.data.errors[0].field").value("name"))
                .andExpect(jsonPath("$.data.errors[0].message").value("名称长度必须为 2 到 10 个字符"))
                .andExpect(jsonPath("$.trace_id").isNotEmpty());
    }

    @Test
    void shouldReturnReadableMessageForMalformedJson() throws Exception {
        mockMvc.perform(post("/api/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.data.errors[0].field").value("requestBody"))
                .andExpect(jsonPath("$.data.errors[0].message").value("请求体格式错误或字段类型不正确"));
    }

    @Test
    void shouldNormalizeMissingAndIllegalQueryParameters() throws Exception {
        mockMvc.perform(get("/api/test/query"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.data.errors[0].field").value("page"))
                .andExpect(jsonPath("$.data.errors[0].message").value("缺少必填参数"));

        mockMvc.perform(get("/api/test/query").param("page", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.data.errors[0].field").value("page"))
                .andExpect(jsonPath("$.data.errors[0].message").value("参数类型不正确"));
    }

    @Test
    void shouldWrapStringResponsesWithoutConverterErrors() throws Exception {
        mockMvc.perform(get("/api/test/text"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data").value("ok"))
                .andExpect(jsonPath("$.trace_id").isNotEmpty());
    }

    @Test
    void shouldNormalizeRequiredErrorStatuses() throws Exception {
        assertError(401, "UNAUTHORIZED");
        assertError(403, "FORBIDDEN");
        assertError(409, "CONFLICT");
        assertError(429, "TOO_MANY_REQUESTS");
        assertError(500, "INTERNAL_SERVER_ERROR");
    }

    @Test
    void shouldReturnStructured404ForUnknownApi() throws Exception {
        mockMvc.perform(get("/api/not-existing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("请求的资源不存在"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.trace_id").isNotEmpty());
    }

    @Test
    void shouldExposeRetryAfterForRateLimit() throws Exception {
        mockMvc.perform(get("/api/test/errors/429"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"));
    }

    @Test
    void shouldExposeInitialOpenApiDocument() throws Exception {
        String document = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("AI BI 平台 API"))
                .andExpect(jsonPath("$.info.version").value("1.1.0"))
                .andExpect(jsonPath("$.paths['/api/system/status']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/system/status']").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode paths = new ObjectMapper().readTree(document).path("paths");

        assertThat(paths.fieldNames()).toIterable()
                .allMatch(path -> path.startsWith("/api/"))
                .allMatch(path -> path.matches(
                        "^/api(?:/(?:[a-z0-9]+(?:-[a-z0-9]+)*|\\{[a-z][A-Za-z0-9]*}))+$"))
                .noneMatch(path -> path.matches("^/api/v\\d+(?:/|$).*$"));
    }

    @Test
    void shouldKeepLegacyVersionedPathDuringMigration() throws Exception {
        mockMvc.perform(get("/api/v1/system/status"))
                .andExpect(status().isOk())
                .andExpect(header().string("Deprecation", "true"))
                .andExpect(header().string("Warning", "299 - \"Deprecated API path; migrate to /api\""))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test
    void shouldReplayIdempotentRequestAndRejectDifferentPayload() throws Exception {
        String key = "idem-test-123456";
        var request = post("/api/test/idempotent")
                .header("Idempotency-Key", key)
                .header("X-Tenant-Id", "101")
                .header("X-Member-Id", "202")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":\"same\"}");

        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sequence").value(1));
        mockMvc.perform(post("/api/test/idempotent")
                        .header("Idempotency-Key", key)
                        .header("X-Tenant-Id", "101")
                        .header("X-Member-Id", "202")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"same\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sequence").value(1));
        mockMvc.perform(post("/api/test/idempotent")
                        .header("Idempotency-Key", key)
                        .header("X-Tenant-Id", "101")
                        .header("X-Member-Id", "202")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"different\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    private void assertError(int httpStatus, String code) throws Exception {
        mockMvc.perform(get("/api/test/errors/{status}", httpStatus))
                .andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.trace_id").isNotEmpty());
    }

    @RestController
    @RequestMapping("/api/test")
    static class ContractTestController {

        private final AtomicInteger idempotentCalls = new AtomicInteger();

        @PostMapping("/validate")
        TestRequest validate(@Valid @RequestBody TestRequest request) {
            return request;
        }

        @GetMapping("/query")
        int query(@RequestParam int page) {
            return page;
        }

        @GetMapping("/text")
        String text() {
            return "ok";
        }

        @PostMapping("/idempotent")
        @Idempotent
        IdempotentResponse idempotent(@RequestBody IdempotentRequest request) {
            return new IdempotentResponse(request.value(), idempotentCalls.incrementAndGet());
        }

        @GetMapping("/errors/{status}")
        void error(@PathVariable int status) {
            switch (status) {
                case 401 -> throw new UnauthorizedException();
                case 403 -> throw new ForbiddenException();
                case 409 -> throw new ConflictException("测试资源冲突");
                case 429 -> throw new TooManyRequestsException(30);
                case 500 -> throw new IllegalStateException("敏感内部错误不应返回给客户端");
                default -> throw new IllegalArgumentException("不支持的测试状态码");
            }
        }
    }

    record TestRequest(
            @NotBlank(message = "名称不能为空")
            @Size(min = 2, max = 10, message = "名称长度必须为 2 到 10 个字符")
            String name) {
    }

    record IdempotentRequest(String value) {
    }

    record IdempotentResponse(String value, int sequence) {
    }
}
