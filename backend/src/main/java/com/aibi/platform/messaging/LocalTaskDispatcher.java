package com.aibi.platform.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "false", matchIfMissing = true)
public class LocalTaskDispatcher implements TaskDispatcher {

    @Override
    public void dispatch(TaskKind kind, long tenantId, long taskId, Long datasetId,
            Runnable persistTask, Runnable executeLocally) {
        persistTask.run();
        executeLocally.run();
    }
}
