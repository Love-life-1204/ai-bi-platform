package com.aibi.platform.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.aopalliance.aop.Advice;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.core.AcknowledgeMode;

@Configuration(proxyBeanMethods = false)
@EnableRabbit
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
public class RabbitMessagingConfiguration {

    @Bean
    public MessageConverter rabbitMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
            MessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        template.setMandatory(true);
        return template;
    }

    @Bean
    public Declarables taskTopology(MessagingProperties properties) {
        TopicExchange tasks = new TopicExchange(properties.getExchange(), true, false);
        TopicExchange deadLetters = new TopicExchange(
                properties.getDeadLetterExchange(), true, false);
        List<Declarable> declarables = new ArrayList<>();
        declarables.add(tasks);
        declarables.add(deadLetters);
        for (TaskKind kind : TaskKind.values()) {
            Queue queue = QueueBuilder.durable(kind.queue())
                    .quorum()
                    .withArgument("x-delivery-limit", properties.getConsumerRetryAttempts() + 2)
                    .deadLetterExchange(properties.getDeadLetterExchange())
                    .deadLetterRoutingKey(kind.deadLetterRoutingKey())
                    .build();
            Queue deadLetterQueue = QueueBuilder.durable(kind.deadLetterQueue()).quorum().build();
            Binding taskBinding = BindingBuilder.bind(queue).to(tasks).with(kind.routingKey());
            Binding deadLetterBinding = BindingBuilder.bind(deadLetterQueue)
                    .to(deadLetters).with(kind.deadLetterRoutingKey());
            declarables.add(queue);
            declarables.add(deadLetterQueue);
            declarables.add(taskBinding);
            declarables.add(deadLetterBinding);
        }
        return new Declarables(declarables);
    }

    @Bean(name = "taskRabbitListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory taskRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter converter,
            MessagingProperties properties) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(properties.getConsumerPrefetch());
        factory.setConcurrentConsumers(properties.getConsumerConcurrency());
        factory.setMaxConcurrentConsumers(properties.getConsumerMaxConcurrency());
        factory.setDefaultRequeueRejected(false);
        Advice retry = RetryInterceptorBuilder.stateless()
                .maxAttempts(properties.getConsumerRetryAttempts())
                .backOffOptions(properties.getConsumerRetryInitialMs(),
                        properties.getConsumerRetryMultiplier(), properties.getConsumerRetryMaxMs())
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
        factory.setAdviceChain(retry);
        return factory;
    }
}
