package com.aibi.platform.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aibi.platform.ai.manager.DelegatedQueryManager;
import com.aibi.platform.ai.mapper.InMemoryAiStore;
import com.aibi.platform.ai.model.AiModels.Task;
import com.aibi.platform.ai.service.AiInternalToolService;
import com.aibi.platform.common.exception.ForbiddenException;
import com.aibi.platform.identity.dto.AccessCheckRequest;
import com.aibi.platform.identity.dto.TaskAuthorizationSnapshot;
import com.aibi.platform.query.dto.QueryDtos.Column;
import com.aibi.platform.query.dto.QueryDtos.PublicationSnapshot;
import com.aibi.platform.query.dto.QueryDtos.QueryPlan;
import com.aibi.platform.query.dto.QueryDtos.QueryResult;
import com.aibi.platform.query.dto.QueryDtos.ValidationData;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AiInternalToolIdempotencyTest {

    @Test
    void shouldExecuteOnceAndReauthorizeBeforeReturningCachedResult() {
        long taskId = 900L;
        InMemoryAiStore store = new InMemoryAiStore();
        TaskAuthorizationSnapshot snapshot = new TaskAuthorizationSnapshot(
                10, 20, 30, 40, 1, 1,
                new AccessCheckRequest(50L, "ai:ask", null, "ask"));
        Instant now = Instant.now();
        store.insertTask(new Task(taskId, 30, 60, 40, "request-900", "thread-900",
                0, "RUNNING", 1, 0, null, now.plusSeconds(60), now, now, null), snapshot);

        QueryPlan plan = new QueryPlan(101, List.of(201L), List.of(),
                null, List.of(), List.of(), 100);
        ValidationData validation = new ValidationData(
                true, "plan-hash", 101, List.of(201L), List.of());
        QueryResult result = new QueryResult("query-900", "SUCCEEDED",
                List.of(new Column("net_sales", "净销售额", "DECIMAL", "METRIC")),
                List.of(Map.of("net_sales", "1200.00")), 1, false,
                new PublicationSnapshot(7, 9, 2), 12);
        DelegatedQueryManager queries = mock(DelegatedQueryManager.class);
        when(queries.validate(snapshot, plan)).thenReturn(validation);
        when(queries.execute(taskId, snapshot, plan)).thenReturn(result);
        AiInternalToolService service = new AiInternalToolService(
                store, queries, new ObjectMapper().findAndRegisterModules());

        String key = taskId + ":query_execute:2";
        assertEquals(result, service.execute(taskId, plan, key));
        assertEquals(result, service.execute(taskId, plan, key));
        verify(queries, times(1)).execute(taskId, snapshot, plan);
        verify(queries, times(2)).validate(snapshot, plan);

        when(queries.validate(snapshot, plan)).thenThrow(new ForbiddenException());
        assertThrows(ForbiddenException.class, () -> service.execute(taskId, plan, key));
        verify(queries, times(1)).execute(taskId, snapshot, plan);
    }
}
