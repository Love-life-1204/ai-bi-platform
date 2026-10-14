package com.aibi.platform.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

class RabbitMqLiveAcceptanceTest {

    private static final String EXCHANGE = "aibi.tasks";
    private static final String QUEUE = "aibi.sync.q";
    private static final String DEAD_LETTER_QUEUE = "aibi.sync.dlq";

    @Test
    @EnabledIfEnvironmentVariable(named = "RABBITMQ_LIVE_TEST", matches = "true")
    void verifiesConfirmPersistenceAckRedeliveryDuplicateAndDeadLetter() throws Exception {
        ConnectionFactory factory = connectionFactory();
        try (Connection connection = factory.newConnection("aibi-live-acceptance")) {
            Channel channel = connection.createChannel();
            assertThat(channel.queueDeclarePassive(QUEUE).getMessageCount()).isZero();
            assertThat(channel.queueDeclarePassive(DEAD_LETTER_QUEUE).getMessageCount()).isZero();
            channel.confirmSelect();

            String confirmedId = publish(channel, "confirmed");
            channel.waitForConfirmsOrDie(Duration.ofSeconds(10).toMillis());
            GetResponse confirmed = awaitMessage(channel, QUEUE);
            assertThat(confirmed.getProps().getMessageId()).isEqualTo(confirmedId);
            assertThat(confirmed.getProps().getDeliveryMode()).isEqualTo(2);
            channel.basicAck(confirmed.getEnvelope().getDeliveryTag(), false);

            String redeliveryId = publish(channel, "redelivery");
            channel.waitForConfirmsOrDie(Duration.ofSeconds(10).toMillis());
            GetResponse unacked = awaitMessage(channel, QUEUE);
            assertThat(unacked.getProps().getMessageId()).isEqualTo(redeliveryId);
            channel.close();

            try (Channel recoveryChannel = connection.createChannel()) {
                GetResponse redelivered = awaitMessage(recoveryChannel, QUEUE);
                assertThat(redelivered.getProps().getMessageId()).isEqualTo(redeliveryId);
                assertThat(redelivered.getEnvelope().isRedeliver()).isTrue();
                recoveryChannel.basicAck(redelivered.getEnvelope().getDeliveryTag(), false);

                String duplicateId = UUID.randomUUID().toString();
                publish(recoveryChannel, duplicateId, "duplicate-one");
                publish(recoveryChannel, duplicateId, "duplicate-two");
                GetResponse duplicateOne = awaitMessage(recoveryChannel, QUEUE);
                GetResponse duplicateTwo = awaitMessage(recoveryChannel, QUEUE);
                assertThat(duplicateOne.getProps().getMessageId()).isEqualTo(duplicateId);
                assertThat(duplicateTwo.getProps().getMessageId()).isEqualTo(duplicateId);
                recoveryChannel.basicAck(duplicateOne.getEnvelope().getDeliveryTag(), false);
                recoveryChannel.basicAck(duplicateTwo.getEnvelope().getDeliveryTag(), false);

                String rejectedId = publish(recoveryChannel, "dead-letter");
                GetResponse rejected = awaitMessage(recoveryChannel, QUEUE);
                assertThat(rejected.getProps().getMessageId()).isEqualTo(rejectedId);
                recoveryChannel.basicReject(rejected.getEnvelope().getDeliveryTag(), false);
                GetResponse deadLetter = awaitMessage(recoveryChannel, DEAD_LETTER_QUEUE);
                assertThat(deadLetter.getProps().getMessageId()).isEqualTo(rejectedId);
                recoveryChannel.basicAck(deadLetter.getEnvelope().getDeliveryTag(), false);
            }
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RABBITMQ_LIVE_TEST", matches = "true")
    void routesAndDrainsBoundedMixedTenantBacklogAcrossAllTaskQueues() throws Exception {
        int messagesPerKind = 100;
        ConnectionFactory factory = connectionFactory();
        try (Connection connection = factory.newConnection("aibi-mixed-load-acceptance");
                Channel channel = connection.createChannel()) {
            for (TaskKind kind : TaskKind.values()) {
                assertThat(channel.queueDeclarePassive(kind.queue()).getMessageCount()).isZero();
            }
            channel.confirmSelect();
            for (int sequence = 0; sequence < messagesPerKind; sequence++) {
                for (TaskKind kind : TaskKind.values()) {
                    long tenantId = sequence % 10 + 1;
                    AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                            .messageId(UUID.randomUUID().toString())
                            .contentType("application/json")
                            .deliveryMode(2)
                            .headers(Map.of("tenant_id", tenantId, "task_kind", kind.code()))
                            .build();
                    channel.basicPublish(EXCHANGE, kind.routingKey(), true, properties,
                            ("{\"sequence\":" + sequence + "}")
                                    .getBytes(StandardCharsets.UTF_8));
                }
            }
            channel.waitForConfirmsOrDie(Duration.ofSeconds(20).toMillis());
            for (TaskKind kind : TaskKind.values()) {
                assertThat(channel.queueDeclarePassive(kind.queue()).getMessageCount())
                        .isEqualTo(messagesPerKind);
            }

            for (int sequence = 0; sequence < messagesPerKind; sequence++) {
                for (TaskKind kind : TaskKind.values()) {
                    GetResponse response = awaitMessage(channel, kind.queue());
                    assertThat(response.getEnvelope().getRoutingKey())
                            .isEqualTo(kind.routingKey());
                    assertThat(response.getProps().getHeaders()).containsKeys(
                            "tenant_id", "task_kind");
                    channel.basicAck(response.getEnvelope().getDeliveryTag(), false);
                }
            }
            for (TaskKind kind : TaskKind.values()) {
                assertThat(channel.queueDeclarePassive(kind.queue()).getMessageCount()).isZero();
            }
        }
    }

    private static String publish(Channel channel, String marker) throws Exception {
        String messageId = UUID.randomUUID().toString();
        publish(channel, messageId, marker);
        return messageId;
    }

    private static void publish(Channel channel, String messageId, String marker) throws Exception {
        AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                .messageId(messageId)
                .contentType("application/json")
                .deliveryMode(2)
                .build();
        byte[] body = ("{\"acceptance\":\"" + marker + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        channel.basicPublish(EXCHANGE, "task.sync", true, properties, body);
    }

    private static GetResponse awaitMessage(Channel channel, String queue) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        GetResponse response;
        do {
            response = channel.basicGet(queue, false);
            if (response != null) {
                return response;
            }
            Thread.sleep(100);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("No message arrived in " + queue + " within 10 seconds");
    }

    private static ConnectionFactory connectionFactory() throws Exception {
        String trustStorePath = requiredEnvironment("RABBITMQ_TRUST_STORE");
        char[] trustStorePassword = requiredEnvironment("RABBITMQ_TRUST_STORE_PASSWORD")
                .toCharArray();
        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(Path.of(trustStorePath))) {
            trustStore.load(input, trustStorePassword);
        }
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagerFactory.getTrustManagers(), null);

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(System.getenv().getOrDefault("RABBITMQ_HOST", "localhost"));
        factory.setPort(Integer.parseInt(System.getenv().getOrDefault("RABBITMQ_PORT", "5671")));
        factory.setVirtualHost(System.getenv().getOrDefault("RABBITMQ_VHOST", "/aibi"));
        factory.setUsername(requiredEnvironment("RABBITMQ_USERNAME"));
        factory.setPassword(requiredEnvironment("RABBITMQ_PASSWORD"));
        factory.useSslProtocol(sslContext);
        factory.enableHostnameVerification();
        factory.setAutomaticRecoveryEnabled(false);
        return factory;
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set for the live acceptance test");
        }
        return value;
    }
}
