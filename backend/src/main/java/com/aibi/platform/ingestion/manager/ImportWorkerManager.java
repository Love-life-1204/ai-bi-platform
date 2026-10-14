package com.aibi.platform.ingestion.manager;

import com.aibi.platform.common.exception.TooManyRequestsException;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ImportWorkerManager {

    private static final Logger log = LoggerFactory.getLogger(ImportWorkerManager.class);
    private final ThreadPoolExecutor executor;

    public ImportWorkerManager(IngestionProperties properties) {
        AtomicInteger sequence = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(properties.getImportWorkers(),
                properties.getImportWorkers(), 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(properties.getImportQueueCapacity()), runnable -> {
                    Thread thread = new Thread(runnable,
                            "file-import-" + sequence.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    public void submit(long tenantId, long importId, Runnable task) {
        try {
            executor.execute(() -> {
                try {
                    task.run();
                } catch (RuntimeException exception) {
                    log.warn("文件导入任务失败: tenant_id={}, import_id={}",
                            tenantId, importId, exception);
                }
            });
        } catch (RejectedExecutionException exception) {
            throw new TooManyRequestsException(5);
        }
    }

    @PreDestroy
    public void close() {
        executor.shutdownNow();
    }
}
