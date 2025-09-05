package com.huskyapply.gateway.service;

import com.huskyapply.gateway.dto.StatusUpdateEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class SseManager {

  private static final Logger log = LoggerFactory.getLogger(SseManager.class);

  private final ConcurrentHashMap<UUID, SseConnectionInfo> connections = new ConcurrentHashMap<>();
  private final ScheduledExecutorService cleanupExecutor =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "sse-cleanup");
            t.setDaemon(true);
            return t;
          });

  @Value("${sse.connection.timeout:300000}") // 5 minutes default
  private long connectionTimeoutMs;

  @Value("${sse.max.connections:1000}") // Maximum concurrent connections
  private int maxConnections;

  @Value("${sse.cleanup.interval:60}") // Cleanup interval in seconds
  private int cleanupIntervalSeconds;

  private final Counter connectionsCreated;
  private final Counter connectionsRemoved;
  private final Counter messagesSent;
  private final Counter messagesFailedToSend;

  @Autowired
  public SseManager(MeterRegistry meterRegistry) {
    this.connectionsCreated =
        Counter.builder("sse.connections.created")
            .description("Number of SSE connections created")
            .register(meterRegistry);

    this.connectionsRemoved =
        Counter.builder("sse.connections.removed")
            .description("Number of SSE connections removed")
            .register(meterRegistry);

    this.messagesSent =
        Counter.builder("sse.messages.sent")
            .description("Number of SSE messages sent successfully")
            .register(meterRegistry);

    this.messagesFailedToSend =
        Counter.builder("sse.messages.failed")
            .description("Number of SSE messages failed to send")
            .register(meterRegistry);

    // Register gauge for active connections
    Gauge.builder("sse.connections.active", this::getActiveConnectionCount)
        .description("Number of active SSE connections")
        .register(meterRegistry);
  }

  @PostConstruct
  public void init() {
    // Start cleanup task
    cleanupExecutor.scheduleAtFixedRate(
        this::cleanupStaleConnections,
        cleanupIntervalSeconds,
        cleanupIntervalSeconds,
        TimeUnit.SECONDS);

    log.info(
        "SSE Manager initialized with max connections: {}, cleanup interval: {}s",
        maxConnections,
        cleanupIntervalSeconds);
  }

  @PreDestroy
  public void shutdown() {
    log.info("Shutting down SSE Manager");
    cleanupExecutor.shutdown();
    try {
      if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
        cleanupExecutor.shutdownNow();
      }
    } catch (InterruptedException e) {
      cleanupExecutor.shutdownNow();
      Thread.currentThread().interrupt();
    }

    // Close all remaining connections gracefully
    connections
        .values()
        .forEach(
            connectionInfo -> {
              try {
                connectionInfo.emitter.complete();
              } catch (Exception e) {
                log.debug("Error closing SSE connection during shutdown", e);
              }
            });
    connections.clear();
  }

  public boolean add(UUID jobId, SseEmitter emitter) {
    // Check connection limit
    if (connections.size() >= maxConnections) {
      log.warn(
          "SSE connection limit exceeded for jobId: {}, current connections: {}",
          jobId,
          connections.size());
      return false;
    }

    SseConnectionInfo connectionInfo = new SseConnectionInfo(emitter, Instant.now());
    connections.put(jobId, connectionInfo);
    connectionsCreated.increment();

    // Set up emitter callbacks with proper cleanup
    emitter.onCompletion(
        () -> {
          log.debug("SSE connection completed for jobId: {}", jobId);
          removeConnection(jobId);
        });

    emitter.onTimeout(
        () -> {
          log.debug("SSE connection timed out for jobId: {}", jobId);
          removeConnection(jobId);
        });

    emitter.onError(
        throwable -> {
          log.error("SSE connection error for jobId: {}", jobId, throwable);
          removeConnection(jobId);
        });

    log.debug(
        "Added SSE connection for jobId: {}, total connections: {}", jobId, connections.size());
    return true;
  }

  public void send(UUID jobId, StatusUpdateEvent event) {
    SseConnectionInfo connectionInfo = connections.get(jobId);
    if (connectionInfo != null) {
      try {
        connectionInfo.emitter.send(SseEmitter.event().name("status").data(event));
        connectionInfo.lastActivity = Instant.now();
        messagesSent.increment();
        log.debug("Sent status update for jobId: {} with status: {}", jobId, event.getStatus());
      } catch (IOException e) {
        log.error("Failed to send SSE event for jobId: {}", jobId, e);
        messagesFailedToSend.increment();
        removeConnection(jobId);
      }
    } else {
      log.warn("No SSE emitter found for jobId: {}", jobId);
    }
  }

  /**
   * Send streaming update with partial content for real-time AI response streaming. This enables
   * progressive rendering of cover letters as they are generated.
   */
  public void sendStreamingUpdate(UUID jobId, Map<String, Object> streamingData) {
    SseConnectionInfo connectionInfo = connections.get(jobId);
    if (connectionInfo != null) {
      try {
        // Create streaming event with enhanced data structure
        StatusUpdateEvent streamingEvent = new StatusUpdateEvent();
        streamingEvent.setStatus("STREAMING");

        // Set streaming-specific data
        if (streamingData.containsKey("partial_content")) {
          streamingEvent.setGeneratedText((String) streamingData.get("partial_content"));
        }

        // Include progress and metadata
        streamingEvent.setMessage("AI generation in progress...");
        streamingEvent.setTimestamp(Instant.now());

        // Add streaming metadata to the event
        streamingEvent.setStreamingData(streamingData);

        connectionInfo.emitter.send(
            SseEmitter.event()
                .name("streaming")
                .data(streamingEvent)
                .id(UUID.randomUUID().toString()));

        connectionInfo.lastActivity = Instant.now();
        messagesSent.increment();

        // Log progress at reasonable intervals to avoid spam
        Object progress = streamingData.get("progress");
        if (progress instanceof Number) {
          double progressValue = ((Number) progress).doubleValue();
          if (progressValue > 0 && (progressValue * 100) % 10 < 1) { // Log approximately every 10%
            log.debug("Streaming progress for job {}: {:.1f}%", jobId, progressValue * 100);
          }
        }

      } catch (IOException e) {
        log.warn("Failed to send streaming update for job {}: {}", jobId, e.getMessage());
        messagesFailedToSend.increment();
        removeConnection(jobId);
      }
    } else {
      log.debug("No SSE connection found for streaming job {}", jobId);
    }
  }

  public void sendHeartbeat(UUID jobId) {
    SseConnectionInfo connectionInfo = connections.get(jobId);
    if (connectionInfo != null) {
      try {
        connectionInfo.emitter.send(SseEmitter.event().name("heartbeat").data("ping"));
        connectionInfo.lastActivity = Instant.now();
        log.trace("Sent heartbeat for jobId: {}", jobId);
      } catch (IOException e) {
        log.debug("Failed to send heartbeat for jobId: {}, removing connection", jobId);
        removeConnection(jobId);
      }
    }
  }

  private void removeConnection(UUID jobId) {
    SseConnectionInfo removed = connections.remove(jobId);
    if (removed != null) {
      connectionsRemoved.increment();
      try {
        removed.emitter.complete();
      } catch (Exception e) {
        log.debug("Error completing SSE emitter for jobId: {}", jobId, e);
      }
    }
  }

  private void cleanupStaleConnections() {
    Instant cutoff = Instant.now().minusMillis(connectionTimeoutMs);
    int cleanedUp = 0;

    for (Map.Entry<UUID, SseConnectionInfo> entry : connections.entrySet()) {
      SseConnectionInfo connectionInfo = entry.getValue();
      if (connectionInfo.lastActivity.isBefore(cutoff)) {
        UUID jobId = entry.getKey();
        log.debug("Cleaning up stale SSE connection for jobId: {}", jobId);
        removeConnection(jobId);
        cleanedUp++;
      }
    }

    if (cleanedUp > 0) {
      log.info("Cleaned up {} stale SSE connections, remaining: {}", cleanedUp, connections.size());
    }
  }

  public int getActiveConnectionCount() {
    return connections.size();
  }

  public void broadcastToAll(StatusUpdateEvent event) {
    int successCount = 0;
    int failCount = 0;

    for (Map.Entry<UUID, SseConnectionInfo> entry : connections.entrySet()) {
      try {
        entry.getValue().emitter.send(SseEmitter.event().name("broadcast").data(event));
        entry.getValue().lastActivity = Instant.now();
        successCount++;
      } catch (IOException e) {
        log.debug("Failed to broadcast to jobId: {}", entry.getKey(), e);
        removeConnection(entry.getKey());
        failCount++;
      }
    }

    log.info("Broadcast completed: {} successful, {} failed", successCount, failCount);
  }

  private static class SseConnectionInfo {
    final SseEmitter emitter;
    final Instant createdAt;
    volatile Instant lastActivity;

    SseConnectionInfo(SseEmitter emitter, Instant createdAt) {
      this.emitter = emitter;
      this.createdAt = createdAt;
      this.lastActivity = createdAt;
    }
  }
}
