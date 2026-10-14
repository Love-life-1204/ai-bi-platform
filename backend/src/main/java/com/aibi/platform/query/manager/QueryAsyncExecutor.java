package com.aibi.platform.query.manager;

import com.aibi.platform.common.exception.TooManyRequestsException;
import com.aibi.platform.query.config.QueryProperties;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class QueryAsyncExecutor {

    private final ThreadPoolExecutor executor;

    public QueryAsyncExecutor(QueryProperties properties) {
        int workers = Math.max(1, properties.getGlobalConcurrency());
        executor = new ThreadPoolExecutor(workers, workers, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(Math.max(1, properties.getQueueCapacity())),
                runnable -> {
                    Thread thread = new Thread(runnable, "query-async-worker");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    public void submit(Runnable task) {
        try {
            executor.execute(task);
        } catch (java.util.concurrent.RejectedExecutionException exception) {
            throw new TooManyRequestsException(1);
        }
    }

    @PreDestroy
    public void close() {
        executor.shutdownNow();
    }
}
