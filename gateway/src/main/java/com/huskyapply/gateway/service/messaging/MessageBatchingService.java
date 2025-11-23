package com.huskyapply.gateway.service.messaging;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MessageBatchingService {

  private static final Logger logger = LoggerFactory.getLogger(MessageBatchingService.class);

  private final RabbitTemplate rabbitTemplate;
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
  private final Map<String, List<Map<String, Object>>> batches = new ConcurrentHashMap<>();
  private final AtomicLong totalMessagesProcessed = new AtomicLong(0);
  private final AtomicLong totalBatchesSent = new AtomicLong(0);

  @Value("${rabbitmq.batch.size:50}")
  private int batchSize;

  @Value("${rabbitmq.batch.interval.ms:100}")
  private long batchIntervalMs;

  public MessageBatchingService(RabbitTemplate rabbitTemplate) {
    this.rabbitTemplate = rabbitTemplate;
    startBatchFlusher();
  }

  private void startBatchFlusher() {
    scheduler.scheduleAtFixedRate(
        this::flushAllBatches, batchIntervalMs, batchIntervalMs, TimeUnit.MILLISECONDS);
  }

  public void addToBatch(Map<String, Object> message, String exchange, String routingKey) {
    String key = exchange + "::" + routingKey;
    batches.compute(
        key,
        (k, v) -> {
          if (v == null) {
            v = new ArrayList<>();
          }
          v.add(message);
          if (v.size() >= batchSize) {
            sendBatch(exchange, routingKey, v);
            return new ArrayList<>();
          }
          return v;
        });
  }

  public void addToBatchBulk(List<Object> messages, String exchange, String routingKey) {
    for (Object msg : messages) {
      if (msg instanceof Map) {
        @SuppressWarnings("unchecked")
        Map<String, Object> mapMsg = (Map<String, Object>) msg;
        addToBatch(mapMsg, exchange, routingKey);
      }
    }
  }

  public void flushAllBatches() {
    batches.forEach(
        (key, batch) -> {
          if (!batch.isEmpty()) {
            String[] parts = key.split("::");
            if (parts.length == 2) {
              synchronized (batch) {
                if (!batch.isEmpty()) {
                  // Create a copy to send and clear the original
                  List<Map<String, Object>> batchToSend = new ArrayList<>(batch);
                  batch.clear();
                  sendBatch(parts[0], parts[1], batchToSend);
                }
              }
            }
          }
        });
  }

  private void sendBatch(String exchange, String routingKey, List<Map<String, Object>> batch) {
    try {
      // In a real scenario, you might want to wrap this in a "batch" object
      // For now, we'll just send individual messages or a list if the consumer
      // supports it.
      // Assuming consumer expects individual messages for simplicity in this
      // fallback/hybrid setup,
      // OR we send the list as a single message if the consumer is designed for
      // batching.
      // Given the context, let's send them individually for now to ensure
      // compatibility
      // with the existing Python consumer which might expect single job payloads.
      // OPTIMIZATION: If we truly want batching, the Python side needs to handle
      // List<Payload>.
      // For safety, let's iterate and send.

      for (Map<String, Object> msg : batch) {
        rabbitTemplate.convertAndSend(exchange, routingKey, msg);
      }

      totalMessagesProcessed.addAndGet(batch.size());
      totalBatchesSent.incrementAndGet();
      logger.debug("Flushed batch of {} messages to {}/{}", batch.size(), exchange, routingKey);
    } catch (Exception e) {
      logger.error("Failed to send batch to {}/{}: {}", exchange, routingKey, e.getMessage());
    }
  }

  public BatchingStats getStats() {
    return new BatchingStats(totalMessagesProcessed.get(), totalBatchesSent.get());
  }

  public static class BatchingStats {
    private final long messagesProcessed;
    private final long batchesSent;

    public BatchingStats(long messagesProcessed, long batchesSent) {
      this.messagesProcessed = messagesProcessed;
      this.batchesSent = batchesSent;
    }

    public long getMessagesProcessed() {
      return messagesProcessed;
    }

    public long getBatchesSent() {
      return batchesSent;
    }
  }
}
