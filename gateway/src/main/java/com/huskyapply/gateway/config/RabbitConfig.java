package com.huskyapply.gateway.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration with retry logic and dead letter queues.
 *
 * <p>This configuration sets up proper error handling, retries, and dead letter queues for robust
 * message processing in the HuskyApply system.
 */
@Configuration
public class RabbitConfig {

  @Value("${rabbitmq.exchange.name}")
  private String exchangeName;

  @Value("${rabbitmq.queue.name}")
  private String queueName;

  @Value("${rabbitmq.routing.key}")
  private String routingKey;

  // Dead Letter Queue configuration
  private static final String DLQ_EXCHANGE = "jobs.dlq.exchange";
  private static final String DLQ_QUEUE = "jobs.dlq";
  private static final String DLQ_ROUTING_KEY = "jobs.dlq";

  /** Main exchange for job processing. */
  @Bean
  public DirectExchange jobsExchange() {
    return new DirectExchange(exchangeName, true, false);
  }

  /** Main queue for job processing with DLQ configuration. */
  @Bean
  public Queue jobsQueue() {
    return QueueBuilder.durable(queueName)
        .withArgument("x-dead-letter-exchange", DLQ_EXCHANGE)
        .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY)
        .withArgument("x-message-ttl", 300000) // 5 minutes TTL
        .build();
  }

  /** Binding for main queue. */
  @Bean
  public Binding jobsBinding() {
    return BindingBuilder.bind(jobsQueue()).to(jobsExchange()).with(routingKey);
  }

  /** Dead Letter Queue exchange. */
  @Bean
  public DirectExchange dlqExchange() {
    return new DirectExchange(DLQ_EXCHANGE, true, false);
  }

  /** Dead Letter Queue for failed messages. */
  @Bean
  public Queue dlqQueue() {
    return QueueBuilder.durable(DLQ_QUEUE).build();
  }

  /** Binding for Dead Letter Queue. */
  @Bean
  public Binding dlqBinding() {
    return BindingBuilder.bind(dlqQueue()).to(dlqExchange()).with(DLQ_ROUTING_KEY);
  }

  /** RabbitTemplate with JSON converter and retry configuration. */
  @Bean
  public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
    RabbitTemplate template = new RabbitTemplate(connectionFactory);
    template.setMessageConverter(new Jackson2JsonMessageConverter());

    // Enable publisher confirms for reliability
    template.setMandatory(true);

    return template;
  }

  /** Message converter for JSON serialization. */
  @Bean
  public Jackson2JsonMessageConverter messageConverter() {
    return new Jackson2JsonMessageConverter();
  }

  /** Container factory with retry configuration. */
  @Bean
  public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
      ConnectionFactory connectionFactory) {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    factory.setConnectionFactory(connectionFactory);
    factory.setMessageConverter(new Jackson2JsonMessageConverter());

    // Configure retry and recovery
    factory.setDefaultRequeueRejected(false); // Don't requeue on error
    factory.setAcknowledgeMode(AcknowledgeMode.MANUAL); // Manual acknowledgment

    return factory;
  }
}
