package com.aibi.platform.ai.client;

import com.aibi.platform.ai.dto.AiDtos.ExecuteTaskRequest;
import com.aibi.platform.ai.dto.AiDtos.ResumeTaskRequest;
import com.aibi.platform.ai.dto.AiDtos.TaskAcceptedData;
import com.aibi.platform.ai.dto.AiDtos.TaskEventData;
import com.aibi.platform.ai.dto.AiDtos.TaskStatusResponse;
import com.aibi.platform.ai.manager.AiDelegationManager;
import com.aibi.platform.common.exception.ApiException;
import com.aibi.platform.common.api.ApiErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class AiServiceClient {

    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final AiDelegationManager delegation;

    public AiServiceClient(@Qualifier("aiRestClient") RestClient client,
            ObjectMapper objectMapper, AiDelegationManager delegation) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.delegation = delegation;
    }

    public TaskAcceptedData execute(ExecuteTaskRequest request) {
        return post("/internal/v1/ai/tasks/execute", request, TaskAcceptedData.class);
    }

    public TaskAcceptedData resume(String taskId, ResumeTaskRequest request) {
        return post("/internal/v1/ai/tasks/" + taskId + "/resume", request, TaskAcceptedData.class);
    }

    public TaskStatusResponse cancel(String taskId) {
        return post("/internal/v1/ai/tasks/" + taskId + "/cancel", null, TaskStatusResponse.class);
    }

    public TaskStatusResponse status(String taskId) {
        String path = "/internal/v1/ai/tasks/" + taskId;
        byte[] body = new byte[0];
        var signed = delegation.sign("GET", path, body);
        try {
            return client.get().uri(path).headers(headers -> apply(headers, signed))
                    .retrieve().body(TaskStatusResponse.class);
        } catch (RestClientException exception) {
            throw new ApiException(ApiErrorCode.AI_SERVICE_UNAVAILABLE);
        }
    }

    public List<TaskEventData> events(String taskId, long after) {
        String path = "/internal/v1/ai/tasks/" + taskId + "/events";
        byte[] body = new byte[0];
        var signed = delegation.sign("GET", path, body);
        try {
            return client.get().uri(uri -> uri.path(path).queryParam("after", after).build())
                    .headers(headers -> apply(headers, signed)).retrieve()
                    .body(new ParameterizedTypeReference<>() { });
        } catch (RestClientException exception) {
            throw new ApiException(ApiErrorCode.AI_SERVICE_UNAVAILABLE);
        }
    }

    private <T> T post(String path, Object request, Class<T> responseType) {
        byte[] body = request == null ? new byte[0] : write(request);
        var signed = delegation.sign("POST", path, body);
        try {
            return client.post().uri(path).contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> apply(headers, signed)).body(body)
                    .retrieve().body(responseType);
        } catch (RestClientException exception) {
            throw new ApiException(ApiErrorCode.AI_SERVICE_UNAVAILABLE);
        }
    }

    private byte[] write(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("AI 内部请求序列化失败", exception);
        }
    }

    private void apply(org.springframework.http.HttpHeaders headers,
            AiDelegationManager.SignedHeaders signed) {
        headers.set("X-AI-Timestamp", String.valueOf(signed.timestamp()));
        headers.set("X-AI-Nonce", signed.nonce());
        headers.set("X-AI-Signature", signed.signature());
    }
}

