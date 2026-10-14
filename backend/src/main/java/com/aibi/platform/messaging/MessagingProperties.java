package com.aibi.platform.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.messaging")
public class MessagingProperties {

    private boolean enabled;
    private String exchange = "aibi.tasks";
    private String deadLetterExchange = "aibi.dlx";
    private int publisherBatchSize = 50;
    private long publisherPollDelayMs = 500;
    private int publisherLeaseSeconds = 30;
    private int publisherConfirmTimeoutSeconds = 10;
    private int publisherMaxAttempts = 5;
    private int consumerPrefetch = 4;
    private int consumerConcurrency = 1;
    private int consumerMaxConcurrency = 4;
    private int consumerRetryAttempts = 3;
    private long consumerRetryInitialMs = 1000;
    private double consumerRetryMultiplier = 2.0;
    private long consumerRetryMaxMs = 10000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public String getDeadLetterExchange() {
        return deadLetterExchange;
    }

    public void setDeadLetterExchange(String deadLetterExchange) {
        this.deadLetterExchange = deadLetterExchange;
    }

    public int getPublisherBatchSize() {
        return publisherBatchSize;
    }

    public void setPublisherBatchSize(int publisherBatchSize) {
        this.publisherBatchSize = publisherBatchSize;
    }

    public long getPublisherPollDelayMs() {
        return publisherPollDelayMs;
    }

    public void setPublisherPollDelayMs(long publisherPollDelayMs) {
        this.publisherPollDelayMs = publisherPollDelayMs;
    }

    public int getPublisherLeaseSeconds() {
        return publisherLeaseSeconds;
    }

    public void setPublisherLeaseSeconds(int publisherLeaseSeconds) {
        this.publisherLeaseSeconds = publisherLeaseSeconds;
    }

    public int getPublisherConfirmTimeoutSeconds() {
        return publisherConfirmTimeoutSeconds;
    }

    public void setPublisherConfirmTimeoutSeconds(int publisherConfirmTimeoutSeconds) {
        this.publisherConfirmTimeoutSeconds = publisherConfirmTimeoutSeconds;
    }

    public int getPublisherMaxAttempts() {
        return publisherMaxAttempts;
    }

    public void setPublisherMaxAttempts(int publisherMaxAttempts) {
        this.publisherMaxAttempts = publisherMaxAttempts;
    }

    public int getConsumerPrefetch() {
        return consumerPrefetch;
    }

    public void setConsumerPrefetch(int consumerPrefetch) {
        this.consumerPrefetch = consumerPrefetch;
    }

    public int getConsumerConcurrency() {
        return consumerConcurrency;
    }

    public void setConsumerConcurrency(int consumerConcurrency) {
        this.consumerConcurrency = consumerConcurrency;
    }

    public int getConsumerMaxConcurrency() {
        return consumerMaxConcurrency;
    }

    public void setConsumerMaxConcurrency(int consumerMaxConcurrency) {
        this.consumerMaxConcurrency = consumerMaxConcurrency;
    }

    public int getConsumerRetryAttempts() {
        return consumerRetryAttempts;
    }

    public void setConsumerRetryAttempts(int consumerRetryAttempts) {
        this.consumerRetryAttempts = consumerRetryAttempts;
    }

    public long getConsumerRetryInitialMs() {
        return consumerRetryInitialMs;
    }

    public void setConsumerRetryInitialMs(long consumerRetryInitialMs) {
        this.consumerRetryInitialMs = consumerRetryInitialMs;
    }

    public double getConsumerRetryMultiplier() {
        return consumerRetryMultiplier;
    }

    public void setConsumerRetryMultiplier(double consumerRetryMultiplier) {
        this.consumerRetryMultiplier = consumerRetryMultiplier;
    }

    public long getConsumerRetryMaxMs() {
        return consumerRetryMaxMs;
    }

    public void setConsumerRetryMaxMs(long consumerRetryMaxMs) {
        this.consumerRetryMaxMs = consumerRetryMaxMs;
    }
}
