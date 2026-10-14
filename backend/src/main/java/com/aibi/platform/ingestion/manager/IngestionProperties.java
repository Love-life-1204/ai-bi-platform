package com.aibi.platform.ingestion.manager;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ingestion")
public class IngestionProperties {

    private Path storageDir;
    private long maxFileBytes = 104_857_600L;
    private int previewRows = 100;
    private int batchSize = 1000;
    private int leaseSeconds = 60;
    private int maxAttempts = 3;
    private int importWorkers = 2;
    private int importQueueCapacity = 50;

    public Path getStorageDir() {
        return storageDir;
    }

    public void setStorageDir(Path storageDir) {
        this.storageDir = storageDir;
    }

    public long getMaxFileBytes() {
        return maxFileBytes;
    }

    public void setMaxFileBytes(long maxFileBytes) {
        this.maxFileBytes = maxFileBytes;
    }

    public int getPreviewRows() {
        return previewRows;
    }

    public void setPreviewRows(int previewRows) {
        this.previewRows = previewRows;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getLeaseSeconds() {
        return leaseSeconds;
    }

    public void setLeaseSeconds(int leaseSeconds) {
        this.leaseSeconds = leaseSeconds;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public int getImportWorkers() {
        return importWorkers;
    }

    public void setImportWorkers(int importWorkers) {
        this.importWorkers = importWorkers;
    }

    public int getImportQueueCapacity() {
        return importQueueCapacity;
    }

    public void setImportQueueCapacity(int importQueueCapacity) {
        this.importQueueCapacity = importQueueCapacity;
    }
}
