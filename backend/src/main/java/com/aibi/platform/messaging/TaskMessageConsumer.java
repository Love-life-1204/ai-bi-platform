package com.aibi.platform.messaging;

import com.aibi.platform.ai.service.AiConversationService;
import com.aibi.platform.commercial.service.CommercialService;
import com.aibi.platform.ingestion.manager.SyncWorkerManager;
import com.aibi.platform.ingestion.service.IngestionServiceImpl;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
public class TaskMessageConsumer {

    private final InboxService inbox;
    private final SyncWorkerManager syncWorker;
    private final IngestionServiceImpl ingestion;
    private final CommercialService commercial;
    private final AiConversationService ai;

    public TaskMessageConsumer(InboxService inbox, SyncWorkerManager syncWorker,
            IngestionServiceImpl ingestion, CommercialService commercial,
            AiConversationService ai) {
        this.inbox = inbox;
        this.syncWorker = syncWorker;
        this.ingestion = ingestion;
        this.commercial = commercial;
        this.ai = ai;
    }

    @RabbitListener(queues = "aibi.sync.q", containerFactory = "taskRabbitListenerContainerFactory")
    public void sync(TaskEnvelope envelope, Message message, Channel channel) throws IOException {
        consume("sync-worker", envelope, message, channel,
                () -> syncWorker.execute(envelope.tenantId(), envelope.taskId()));
    }

    @RabbitListener(queues = "aibi.import.q", containerFactory = "taskRabbitListenerContainerFactory")
    public void importFile(TaskEnvelope envelope, Message message, Channel channel) throws IOException {
        consume("import-worker", envelope, message, channel,
                () -> ingestion.consumeImport(envelope.tenantId(), envelope.taskId()));
    }

    @RabbitListener(queues = "aibi.export.q", containerFactory = "taskRabbitListenerContainerFactory")
    public void export(TaskEnvelope envelope, Message message, Channel channel) throws IOException {
        consume("export-worker", envelope, message, channel,
                () -> commercial.consumeExport(envelope.tenantId(), envelope.taskId()));
    }

    @RabbitListener(queues = "aibi.ai.q", containerFactory = "taskRabbitListenerContainerFactory")
    public void ai(TaskEnvelope envelope, Message message, Channel channel) throws IOException {
        consume("ai-worker", envelope, message, channel,
                () -> ai.consumeTask(
                        envelope.tenantId(), envelope.taskId(), envelope.datasetId()));
    }

    private void consume(String consumerCode, TaskEnvelope envelope,
            Message message, Channel channel, Runnable handler) throws IOException {
        inbox.execute(consumerCode, envelope, handler);
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }
}
