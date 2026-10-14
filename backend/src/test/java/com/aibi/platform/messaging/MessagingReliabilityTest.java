package com.aibi.platform.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class MessagingReliabilityTest {

    @Test
    void topologyUsesDurableQuorumTaskAndDeadLetterQueues() {
        MessagingProperties properties = new MessagingProperties();
        RabbitMessagingConfiguration configuration = new RabbitMessagingConfiguration();

        Declarables topology = configuration.taskTopology(properties);
        Set<String> queues = topology.getDeclarables().stream()
                .filter(Queue.class::isInstance)
                .map(Queue.class::cast)
                .peek(queue -> {
                    assertThat(queue.isDurable()).isTrue();
                    assertThat(queue.getArguments()).containsEntry("x-queue-type", "quorum");
                })
                .map(Queue::getName)
                .collect(Collectors.toSet());
        Set<String> routes = topology.getDeclarables().stream()
                .filter(Binding.class::isInstance)
                .map(Binding.class::cast)
                .map(Binding::getRoutingKey)
                .collect(Collectors.toSet());

        for (TaskKind kind : TaskKind.values()) {
            assertThat(queues).contains(kind.queue(), kind.deadLetterQueue());
            assertThat(routes).contains(kind.routingKey(), kind.deadLetterRoutingKey());
        }
    }

    @Test
    void localDispatcherPersistsBeforeBoundedLocalExecution() {
        LocalTaskDispatcher dispatcher = new LocalTaskDispatcher();
        AtomicInteger sequence = new AtomicInteger();

        dispatcher.dispatch(TaskKind.SYNC, 1, 2,
                () -> assertThat(sequence.incrementAndGet()).isEqualTo(1),
                () -> assertThat(sequence.incrementAndGet()).isEqualTo(2));

        assertThat(sequence).hasValue(2);
    }

    @Test
    void outboxClaimSelectionRoundRobinsAcrossTenantsWithoutBreakingTenantOrder() {
        List<OutboxEvent> candidates = List.of(
                event(11, 1), event(12, 1), event(13, 1), event(14, 1),
                event(21, 2), event(31, 3));

        List<OutboxEvent> selected = OutboxEventRepository.selectFairly(candidates, 4, 0);

        assertThat(selected).extracting(event -> event.envelope().tenantId())
                .containsExactly(1L, 2L, 3L, 1L);
        assertThat(selected).extracting(OutboxEvent::id)
                .containsExactly(11L, 21L, 31L, 12L);
    }

    @Test
    void inboxRunsHandlerOnlyOnceForDuplicateDelivery() {
        Set<String> keys = new HashSet<>();
        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(
                new DriverManagerDataSource()) {
            @Override
            public int update(String sql, Map<String, ?> parameters) {
                String key = parameters.get("tenantId") + ":"
                        + parameters.get("consumerCode") + ":" + parameters.get("eventKey");
                if (!keys.add(key)) {
                    throw new DuplicateKeyException("duplicate inbox event");
                }
                return 1;
            }
        };
        InboxService inbox = new InboxService(jdbc);
        TaskEnvelope envelope = new TaskEnvelope(
                "same-event", 7, 9, TaskKind.IMPORT, 1, Instant.EPOCH);
        AtomicInteger executions = new AtomicInteger();

        assertThat(inbox.execute("import-worker", envelope, executions::incrementAndGet)).isTrue();
        assertThat(inbox.execute("import-worker", envelope, executions::incrementAndGet)).isFalse();
        assertThat(executions).hasValue(1);
    }

    private static OutboxEvent event(long id, long tenantId) {
        TaskEnvelope envelope = new TaskEnvelope(
                "event-" + id, tenantId, id, TaskKind.SYNC, 1, Instant.EPOCH);
        return new OutboxEvent(id, envelope.eventId(), envelope, 0);
    }

}
