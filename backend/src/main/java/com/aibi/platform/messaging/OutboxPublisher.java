package com.aibi.platform.messaging;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final OutboxEventRepository repository;
    private final RabbitTemplate rabbit;
    private final MessagingProperties properties;
    private final String workerId = "outbox-" + UUID.randomUUID();

    public OutboxPublisher(OutboxEventRepository repository,
            RabbitTemplate rabbit, MessagingProperties properties) {
        this.repository = repository;
        this.rabbit = rabbit;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.messaging.publisher-poll-delay-ms:500}")
    public void publishAvailable() {
        repository.claim(workerId, properties.getPublisherBatchSize(),
                        Duration.ofSeconds(properties.getPublisherLeaseSeconds()))
                .forEach(this::publish);
    }

    private void publish(OutboxEvent event) {
        CorrelationData correlation = new CorrelationData(event.eventKey());
        try {
            rabbit.convertAndSend(properties.getExchange(), event.envelope().kind().routingKey(),
                    event.envelope(), message -> {
                        message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        message.getMessageProperties().setMessageId(event.eventKey());
                        message.getMessageProperties().setHeader(
                                "tenant_id", event.envelope().tenantId());
                        message.getMessageProperties().setHeader(
                                "task_kind", event.envelope().kind().code());
                        return message;
                    }, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture().get(
                    properties.getPublisherConfirmTimeoutSeconds(), TimeUnit.SECONDS);
            if (!confirm.isAck() || correlation.getReturned() != null) {
                throw new IllegalStateException(confirm.getReason() == null
                        ? "RabbitMQ 未路由消息" : confirm.getReason());
            }
            repository.markPublished(event.id(), workerId);
        } catch (Exception exception) {
            int retry = event.retryCount() + 1;
            long delaySeconds = Math.min(60, 1L << Math.min(retry, 6));
            repository.markFailed(event.id(), workerId, retry,
                    properties.getPublisherMaxAttempts(), Instant.now().plusSeconds(delaySeconds));
            log.warn("Outbox 发布失败: event_key={}, retry={}",
                    event.eventKey(), retry, exception);
        }
    }
}
