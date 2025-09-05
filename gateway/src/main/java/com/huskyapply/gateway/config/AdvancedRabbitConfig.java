package com.huskyapply.gateway.config;

import com.huskyapply.gateway.service.messaging.MessageCompressionService;
import com.huskyapply.gateway.service.messaging.ProtobufMessageConverter;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

/**
 * Advanced RabbitMQ configuration with performance optimizations including: - Priority queues with
 * weighted fair queuing - Message sharding across multiple queues - Quorum queues for high
 * availability - Lazy queues for memory optimization - Connection pooling and channel optimization
 * - Protocol Buffers serialization with compression - Batch processing support with configurable
 * parameters
 */
@Configuration
@ConditionalOnProperty(
    name = "rabbitmq.advanced.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class AdvancedRabbitConfig {

  private static final Logger logger = LoggerFactory.getLogger(AdvancedRabbitConfig.class);

  @Value("${rabbitmq.exchange.name:jobs.exchange}")
  private String exchangeName;

  @Value("${rabbitmq.queue.priority.enabled:true}")
  private boolean priorityQueuesEnabled;

  @Value("${rabbitmq.queue.sharding.enabled:true}")
  private boolean shardingEnabled;

  @Value("${rabbitmq.queue.shards:4}")
  private int numberOfShards;

  @Value("${rabbitmq.queue.quorum.enabled:true}")
  private boolean quorumQueuesEnabled;

  @Value("${rabbitmq.queue.lazy.enabled:true}")
  private boolean lazyQueuesEnabled;

  @Value("${rabbitmq.compression.enabled:true}")
  private boolean compressionEnabled;

  @Value("${rabbitmq.compression.threshold:1024}")
  private int compressionThreshold;

  @Value("${rabbitmq.batch.enabled:true}")
  private boolean batchEnabled;

  @Value("${rabbitmq.batch.size:50}")
  private int batchSize;

  @Value("${rabbitmq.batch.timeout:5000}")
  private int batchTimeoutMs;

  @Value("${rabbitmq.connection.pool.size:20}")
  private int connectionPoolSize;

  @Value("${rabbitmq.channel.cache.size:50}")
  private int channelCacheSize;

  @Value("${rabbitmq.consumer.prefetch:10}")
  private int consumerPrefetch;

  @Value("${rabbitmq.publisher.confirms:true}")
  private boolean publisherConfirms;

  @Value("${rabbitmq.publisher.returns:true}")
  private boolean publisherReturns;

  // Priority Exchanges and Queues Configuration

  /** Main topic exchange with priority routing support */
  @Bean
  @Primary
  public TopicExchange priorityJobsExchange() {
    Map<String, Object> args = new HashMap<>();
    args.put("alternate-exchange", "jobs.alternate.exchange");

    TopicExchange exchange = new TopicExchange(exchangeName, true, false, args);
    logger.info("Created priority jobs exchange: {}", exchangeName);
    return exchange;
  }

  /** Alternate exchange for unroutable messages */
  @Bean
  public FanoutExchange alternateExchange() {
    return new FanoutExchange("jobs.alternate.exchange", true, false);
  }

  /** High priority job queue */
  @Bean
  public Queue highPriorityQueue() {
    return createPriorityQueue("jobs.priority.high", 255, true);
  }

  /** Normal priority job queue */
  @Bean
  public Queue normalPriorityQueue() {
    return createPriorityQueue("jobs.priority.normal", 128, true);
  }

  /** Low priority job queue */
  @Bean
  public Queue lowPriorityQueue() {
    return createPriorityQueue("jobs.priority.low", 64, true);
  }

  /** Express priority queue for urgent jobs */
  @Bean
  public Queue expressPriorityQueue() {
    return createPriorityQueue("jobs.priority.express", 255, false);
  }

  // Sharded Queues for Load Distribution

  /** Create sharded queues for load balancing */
  @Bean
  public Queue[] shardedQueues() {
    if (!shardingEnabled) {
      return new Queue[0];
    }

    Queue[] shards = new Queue[numberOfShards];
    for (int i = 0; i < numberOfShards; i++) {
      String queueName = String.format("jobs.shard.%d", i);
      shards[i] = createOptimizedQueue(queueName, false, true);
      logger.info("Created sharded queue: {}", queueName);
    }
    return shards;
  }

  // Queue Bindings with Priority Routing

  @Bean
  public Binding highPriorityBinding() {
    return BindingBuilder.bind(highPriorityQueue())
        .to(priorityJobsExchange())
        .with("jobs.priority.high");
  }

  @Bean
  public Binding normalPriorityBinding() {
    return BindingBuilder.bind(normalPriorityQueue())
        .to(priorityJobsExchange())
        .with("jobs.priority.normal");
  }

  @Bean
  public Binding lowPriorityBinding() {
    return BindingBuilder.bind(lowPriorityQueue())
        .to(priorityJobsExchange())
        .with("jobs.priority.low");
  }

  @Bean
  public Binding expressPriorityBinding() {
    return BindingBuilder.bind(expressPriorityQueue())
        .to(priorityJobsExchange())
        .with("jobs.priority.express");
  }

  // Batch Processing Queues

  /** Batch job processing queue */
  @Bean
  public Queue batchJobQueue() {
    return createOptimizedQueue("jobs.batch", true, true);
  }

  @Bean
  public Binding batchJobBinding() {
    return BindingBuilder.bind(batchJobQueue()).to(priorityJobsExchange()).with("jobs.batch.*");
  }

  // Dead Letter and Error Handling

  /** Dead letter exchange for failed messages */
  @Bean
  public DirectExchange deadLetterExchange() {
    return new DirectExchange("jobs.dlq.exchange", true, false);
  }

  /** Dead letter queue with TTL and retry policies */
  @Bean
  public Queue deadLetterQueue() {
    Map<String, Object> args = new HashMap<>();
    args.put("x-message-ttl", 300000); // 5 minutes TTL
    args.put("x-max-retries", 3);

    if (quorumQueuesEnabled) {
      args.put("x-queue-type", "quorum");
      args.put("x-quorum-initial-group-size", 3);
    }

    return QueueBuilder.durable("jobs.dlq").withArguments(args).build();
  }

  @Bean
  public Binding deadLetterBinding() {
    return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange()).with("jobs.dlq");
  }

  // Connection Factory with Optimization

  @Bean
  @Primary
  public ConnectionFactory optimizedConnectionFactory() {
    CachingConnectionFactory factory = new CachingConnectionFactory();

    // Connection pool settings
    factory.setChannelCacheSize(channelCacheSize);
    factory.setConnectionCacheSize(connectionPoolSize);
    factory.setChannelCheckoutTimeout(30000); // 30 seconds

    // Connection settings
    factory.setRequestedHeartBeat(Duration.ofSeconds(60));
    factory.setConnectionTimeout(Duration.ofSeconds(30));
    factory.setCloseTimeout(Duration.ofSeconds(30));

    // Publisher settings
    factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
    factory.setPublisherReturns(publisherReturns);

    // Connection recovery
    factory.getRabbitConnectionFactory().setAutomaticRecoveryEnabled(true);
    factory.getRabbitConnectionFactory().setNetworkRecoveryInterval(5000);
    factory.getRabbitConnectionFactory().setTopologyRecoveryEnabled(true);

    logger.info(
        "Configured optimized RabbitMQ connection factory with {} channel cache size",
        channelCacheSize);
    return factory;
  }

  // RabbitTemplate with Advanced Features

  @Bean
  @Primary
  public RabbitTemplate optimizedRabbitTemplate(
      ConnectionFactory connectionFactory, MessageConverter messageConverter) {
    RabbitTemplate template = new RabbitTemplate(connectionFactory);
    template.setMessageConverter(messageConverter);

    // Publisher settings
    template.setMandatory(true);
    template.setConfirmTimeout(30000); // 30 seconds
    template.setReplyTimeout(60000); // 60 seconds

    // Retry configuration
    RetryTemplate retryTemplate = new RetryTemplate();
    SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(3);
    retryTemplate.setRetryPolicy(retryPolicy);

    ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
    backOffPolicy.setInitialInterval(1000);
    backOffPolicy.setMultiplier(2.0);
    backOffPolicy.setMaxInterval(10000);
    retryTemplate.setBackOffPolicy(backOffPolicy);

    template.setRetryTemplate(retryTemplate);

    // Routing optimization
    template.setUseTemporaryReplyQueues(false);
    template.setUserCorrelationId(true);

    logger.info("Configured optimized RabbitTemplate with publisher confirms and retries");
    return template;
  }

  // Message Converter with Protocol Buffers and Compression

  @Bean
  @Primary
  public MessageConverter protobufMessageConverter(MessageCompressionService compressionService) {
    ProtobufMessageConverter converter = new ProtobufMessageConverter(compressionService);
    converter.setCompressionEnabled(compressionEnabled);
    converter.setCompressionThreshold(compressionThreshold);

    logger.info(
        "Configured Protocol Buffers message converter with compression: {}", compressionEnabled);
    return converter;
  }

  // Consumer Configuration

  @Bean
  @Primary
  public SimpleRabbitListenerContainerFactory optimizedRabbitListenerContainerFactory(
      ConnectionFactory connectionFactory, MessageConverter messageConverter) {

    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    factory.setConnectionFactory(connectionFactory);
    factory.setMessageConverter(messageConverter);

    // Consumer settings for high throughput
    factory.setPrefetchCount(consumerPrefetch);
    factory.setConcurrentConsumers(2);
    factory.setMaxConcurrentConsumers(10);
    factory.setConsumerBatchEnabled(batchEnabled);
    if (batchEnabled) {
      factory.setBatchSize(batchSize);
      factory.setBatchTimeout(batchTimeoutMs);
    }

    // Acknowledgment settings
    factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
    factory.setDefaultRequeueRejected(false);

    // Error handling
    factory.setErrorHandler(new ConditionalRejectingErrorHandler());

    // Task executor for parallel processing
    factory.setTaskExecutor(null); // Use default
    factory.setReceiveTimeout(60000L); // 60 seconds

    logger.info(
        "Configured optimized consumer with prefetch: {} and batch size: {}",
        consumerPrefetch,
        batchSize);
    return factory;
  }

  // Utility Methods

  private Queue createPriorityQueue(String name, int maxPriority, boolean lazy) {
    Map<String, Object> args = new HashMap<>();

    if (priorityQueuesEnabled) {
      args.put("x-max-priority", maxPriority);
    }

    if (lazy && lazyQueuesEnabled) {
      args.put("x-queue-mode", "lazy");
    }

    if (quorumQueuesEnabled && !name.contains("express")) {
      args.put("x-queue-type", "quorum");
      args.put("x-quorum-initial-group-size", 3);
    }

    // Dead letter configuration
    args.put("x-dead-letter-exchange", "jobs.dlq.exchange");
    args.put("x-dead-letter-routing-key", "jobs.dlq");
    args.put("x-message-ttl", 3600000); // 1 hour TTL

    return QueueBuilder.durable(name).withArguments(args).build();
  }

  private Queue createOptimizedQueue(String name, boolean priority, boolean lazy) {
    Map<String, Object> args = new HashMap<>();

    if (priority && priorityQueuesEnabled) {
      args.put("x-max-priority", 128);
    }

    if (lazy && lazyQueuesEnabled) {
      args.put("x-queue-mode", "lazy");
    }

    if (quorumQueuesEnabled) {
      args.put("x-queue-type", "quorum");
      args.put("x-quorum-initial-group-size", 3);
    }

    // Performance optimizations
    args.put("x-dead-letter-exchange", "jobs.dlq.exchange");
    args.put("x-dead-letter-routing-key", "jobs.dlq");
    args.put("x-message-ttl", 1800000); // 30 minutes TTL

    return QueueBuilder.durable(name).withArguments(args).build();
  }
}
