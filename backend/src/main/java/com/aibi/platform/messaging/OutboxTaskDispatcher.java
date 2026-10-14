package com.aibi.platform.messaging;

import com.aibi.platform.common.transaction.MySqlTransactional;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
public class OutboxTaskDispatcher implements TaskDispatcher {

    private final OutboxEventRepository repository;

    public OutboxTaskDispatcher(OutboxEventRepository repository) {
        this.repository = repository;
    }

    @Override
    @MySqlTransactional
    public void dispatch(TaskKind kind, long tenantId, long taskId, Long datasetId,
            Runnable persistTask, Runnable executeLocally) {
        persistTask.run();
        String eventId = kind.code() + ":" + tenantId + ":" + taskId;
        repository.insert(new TaskEnvelope(
                eventId, tenantId, taskId, kind, 1, Instant.now(), datasetId));
    }
}
