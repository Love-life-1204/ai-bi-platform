package com.aibi.platform.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    private String serviceUrl = "http://127.0.0.1:8091";
    private String sharedSecret = "local-development-secret-change-me";
    private int delegationTtlSeconds = 75;
    private int taskTimeoutSeconds = 60;
    private int connectTimeoutMs = 2000;
    private int readTimeoutMs = 15000;
    private int workers = 4;
    private int queueCapacity = 50;

    public String getServiceUrl() { return serviceUrl; }
    public void setServiceUrl(String serviceUrl) { this.serviceUrl = serviceUrl; }
    public String getSharedSecret() { return sharedSecret; }
    public void setSharedSecret(String sharedSecret) { this.sharedSecret = sharedSecret; }
    public int getDelegationTtlSeconds() { return delegationTtlSeconds; }
    public void setDelegationTtlSeconds(int value) { this.delegationTtlSeconds = value; }
    public int getTaskTimeoutSeconds() { return taskTimeoutSeconds; }
    public void setTaskTimeoutSeconds(int value) { this.taskTimeoutSeconds = value; }
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int value) { this.connectTimeoutMs = value; }
    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int value) { this.readTimeoutMs = value; }
    public int getWorkers() { return workers; }
    public void setWorkers(int workers) { this.workers = workers; }
    public int getQueueCapacity() { return queueCapacity; }
    public void setQueueCapacity(int value) { this.queueCapacity = value; }
}

