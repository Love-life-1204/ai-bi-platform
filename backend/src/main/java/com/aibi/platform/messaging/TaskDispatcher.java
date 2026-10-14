package com.aibi.platform.messaging;

public interface TaskDispatcher {

    default void dispatch(TaskKind kind, long tenantId, long taskId,
            Runnable persistTask, Runnable executeLocally) {
        dispatch(kind, tenantId, taskId, null, persistTask, executeLocally);
    }

    void dispatch(TaskKind kind, long tenantId, long taskId, Long datasetId,
            Runnable persistTask, Runnable executeLocally);
}
