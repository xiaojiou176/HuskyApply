package com.huskyapply.gateway.service;

import com.huskyapply.gateway.dto.StatusUpdateEvent;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

/**
 * Reactive Server-Sent Events Manager using Redis Pub/Sub
 *
 * <p>This service manages reactive SSE streams using Redis for distributed event broadcasting
 * across multiple Gateway instances.
 *
 * <p>Key features: - Reactive SSE streams with automatic cleanup - Redis-based distributed event
 * broadcasting - Connection pooling and backpressure handling - Automatic stream termination and
 * error recovery
 */
@Service
public class ReactiveSseManager {

  private static final Logger logger = LoggerFactory.getLogger(ReactiveSseManager.class);

  private static final String SSE_CHANNEL_PREFIX = "sse:job:";
  private static final int MAX_CONNECTIONS_PER_JOB = 10;
  private static final Duration DEFAULT_STREAM_TIMEOUT = Duration.ofMinutes(15);

  private final ReactiveRedisTemplate<String, Object> reactiveRedisTemplate;
  private final ReactiveRedisMessageListenerContainer messageListenerContainer;

  // In-memory stream management for this Gateway instance
  private final Map<UUID, Sinks.Many<StatusUpdateEvent>> activeStreams = new ConcurrentHashMap<>();
  private final Map<UUID, Integer> connectionCounts = new ConcurrentHashMap<>();

  public ReactiveSseManager(
      ReactiveRedisTemplate<String, Object> reactiveRedisTemplate,
      ReactiveRedisMessageListenerContainer messageListenerContainer) {
    this.reactiveRedisTemplate = reactiveRedisTemplate;
    this.messageListenerContainer = messageListenerContainer;

    setupCleanupScheduler();
  }

  /**
   * Creates a reactive SSE stream for a specific job
   *
   * <p>The stream automatically: - Subscribes to Redis pub/sub for the job - Handles connection
   * limits and cleanup - Provides heartbeat and error recovery - Terminates on job completion
   */
  public Flux<StatusUpdateEvent> createStream(UUID jobId) {
    logger.info("Creating reactive SSE stream for job: {}", jobId);

    // Check connection limits
    int currentConnections = connectionCounts.getOrDefault(jobId, 0);
    if (currentConnections >= MAX_CONNECTIONS_PER_JOB) {
      logger.warn("Connection limit exceeded for job: {}", jobId);
      return Flux.error(new RuntimeException("Too many concurrent connections for this job"));
    }

    // Create or get existing stream sink
    Sinks.Many<StatusUpdateEvent> sink =
        activeStreams.computeIfAbsent(jobId, id -> Sinks.many().multicast().onBackpressureBuffer());

    // Increment connection count
    connectionCounts.merge(jobId, 1, Integer::sum);

    // Subscribe to Redis pub/sub for distributed events
    String channelName = SSE_CHANNEL_PREFIX + jobId;
    ChannelTopic topic = new ChannelTopic(channelName);

    Flux<StatusUpdateEvent> redisStream =
        messageListenerContainer
            .receive(topic)
            .map(
                message -> {
                  try {
                    // Deserialize message to StatusUpdateEvent
                    return deserializeStatusEvent(message.getMessage());
                  } catch (Exception e) {
                    logger.error("Failed to deserialize SSE message for job: {}", jobId, e);
                    return null;
                  }
                })
            .filter(event -> event != null)
            .doOnNext(
                event -> {
                  logger.debug(
                      "Received Redis SSE event for job: {}, status: {}", jobId, event.getStatus());
                  // Emit to all local connections
                  sink.tryEmitNext(event);
                })
            .doOnError(error -> logger.error("Redis SSE stream error for job: {}", jobId, error))
            .subscribeOn(Schedulers.boundedElastic());

    // Start Redis subscription
    redisStream.subscribe();

    // Return the local stream
    return sink.asFlux()
        .takeUntil(
            event -> {
              // Terminate stream on job completion or failure
              return "COMPLETED".equals(event.getStatus()) || "FAILED".equals(event.getStatus());
            })
        .timeout(DEFAULT_STREAM_TIMEOUT)
        .doOnNext(
            event ->
                logger.debug(
                    "Emitting SSE event for job: {}, status: {}", jobId, event.getStatus()))
        .doOnComplete(
            () -> {
              logger.info("SSE stream completed for job: {}", jobId);
              cleanupConnection(jobId);
            })
        .doOnCancel(
            () -> {
              logger.info("SSE stream cancelled for job: {}", jobId);
              cleanupConnection(jobId);
            })
        .doOnError(
            error -> {
              logger.error("SSE stream error for job: {}", jobId, error);
              cleanupConnection(jobId);
            })
        .onErrorResume(
            error -> {
              // Return error event instead of terminating stream
              StatusUpdateEvent errorEvent = new StatusUpdateEvent();
              errorEvent.setStatus("ERROR");
              errorEvent.setMessage("Stream error: " + error.getMessage());
              return Flux.just(errorEvent);
            });
  }

  /**
   * Broadcasts an event to all SSE streams for a job
   *
   * <p>Uses Redis pub/sub to ensure the event reaches all Gateway instances that have active
   * streams for this job.
   */
  public Mono<Void> broadcastEvent(UUID jobId, StatusUpdateEvent event) {
    logger.debug("Broadcasting SSE event for job: {}, status: {}", jobId, event.getStatus());

    String channelName = SSE_CHANNEL_PREFIX + jobId;

    return reactiveRedisTemplate
        .convertAndSend(channelName, serializeStatusEvent(event))
        .then()
        .doOnSuccess(v -> logger.debug("SSE event broadcast successful for job: {}", jobId))
        .doOnError(error -> logger.error("Failed to broadcast SSE event for job: {}", jobId, error))
        .subscribeOn(Schedulers.parallel());
  }

  /** Gets the current number of active connections for a job */
  public Mono<Integer> getConnectionCount(UUID jobId) {
    return Mono.fromCallable(() -> connectionCounts.getOrDefault(jobId, 0))
        .subscribeOn(Schedulers.parallel());
  }

  /** Gets statistics about all active SSE streams */
  public Mono<Map<String, Object>> getStreamStatistics() {
    return Mono.fromCallable(
            () -> {
              Map<String, Object> stats = new ConcurrentHashMap<>();
              stats.put("activeJobs", activeStreams.size());
              stats.put(
                  "totalConnections",
                  connectionCounts.values().stream().mapToInt(Integer::intValue).sum());
              stats.put(
                  "averageConnectionsPerJob",
                  activeStreams.isEmpty()
                      ? 0.0
                      : connectionCounts.values().stream()
                          .mapToInt(Integer::intValue)
                          .average()
                          .orElse(0.0));
              return stats;
            })
        .subscribeOn(Schedulers.parallel());
  }

  /** Forcefully terminates all streams for a job (admin function) */
  public Mono<Void> terminateStreamsForJob(UUID jobId) {
    return Mono.fromRunnable(
            () -> {
              logger.info("Forcefully terminating all streams for job: {}", jobId);

              Sinks.Many<StatusUpdateEvent> sink = activeStreams.get(jobId);
              if (sink != null) {
                // Send termination event
                StatusUpdateEvent terminationEvent = new StatusUpdateEvent();
                terminationEvent.setStatus("TERMINATED");
                terminationEvent.setMessage("Stream terminated by administrator");

                sink.tryEmitNext(terminationEvent);
                sink.tryEmitComplete();
              }

              cleanupConnection(jobId);
            })
        .subscribeOn(Schedulers.parallel())
        .then();
  }

  // Private helper methods

  private void cleanupConnection(UUID jobId) {
    // Decrement connection count
    connectionCounts.computeIfPresent(
        jobId,
        (id, count) -> {
          int newCount = count - 1;
          return newCount > 0 ? newCount : null;
        });

    // Remove stream if no more connections
    if (connectionCounts.getOrDefault(jobId, 0) == 0) {
      Sinks.Many<StatusUpdateEvent> sink = activeStreams.remove(jobId);
      if (sink != null) {
        sink.tryEmitComplete();
      }
      logger.debug("Cleaned up SSE stream for job: {}", jobId);
    }
  }

  private StatusUpdateEvent deserializeStatusEvent(String message) {
    // Simple JSON deserialization (in production, use Jackson ObjectMapper)
    try {
      // Parse JSON manually for this example
      StatusUpdateEvent event = new StatusUpdateEvent();
      if (message.contains("\"status\":")) {
        String status = extractJsonValue(message, "status");
        event.setStatus(status);
      }
      if (message.contains("\"message\":")) {
        String eventMessage = extractJsonValue(message, "message");
        event.setMessage(eventMessage);
      }
      if (message.contains("\"generatedText\":")) {
        String generatedText = extractJsonValue(message, "generatedText");
        event.setGeneratedText(generatedText);
      }
      return event;
    } catch (Exception e) {
      logger.error("Failed to deserialize status event: {}", message, e);
      return null;
    }
  }

  private String serializeStatusEvent(StatusUpdateEvent event) {
    // Simple JSON serialization (in production, use Jackson ObjectMapper)
    StringBuilder json = new StringBuilder("{");

    if (event.getStatus() != null) {
      json.append("\"status\":\"").append(event.getStatus()).append("\",");
    }
    if (event.getMessage() != null) {
      json.append("\"message\":\"").append(event.getMessage()).append("\",");
    }
    if (event.getGeneratedText() != null) {
      json.append("\"generatedText\":\"")
          .append(event.getGeneratedText().replace("\"", "\\\""))
          .append("\",");
    }

    // Remove trailing comma
    if (json.length() > 1 && json.charAt(json.length() - 1) == ',') {
      json.setLength(json.length() - 1);
    }

    json.append("}");
    return json.toString();
  }

  private String extractJsonValue(String json, String key) {
    String searchKey = "\"" + key + "\":\"";
    int startIndex = json.indexOf(searchKey);
    if (startIndex == -1) return null;

    startIndex += searchKey.length();
    int endIndex = json.indexOf("\"", startIndex);
    if (endIndex == -1) return null;

    return json.substring(startIndex, endIndex);
  }

  private void setupCleanupScheduler() {
    // Schedule periodic cleanup of stale connections
    Flux.interval(Duration.ofMinutes(5))
        .doOnNext(tick -> cleanupStaleConnections())
        .onErrorContinue((error, obj) -> logger.error("Error during SSE cleanup scheduler", error))
        .subscribe();

    logger.info("SSE cleanup scheduler initialized");
  }

  private void cleanupStaleConnections() {
    logger.debug("Running SSE connection cleanup");

    // Remove empty streams and reset connection counts
    activeStreams
        .entrySet()
        .removeIf(
            entry -> {
              UUID jobId = entry.getKey();
              Sinks.Many<StatusUpdateEvent> sink = entry.getValue();

              // Check if sink is terminated or has errors
              if (sink.currentSubscriberCount() == 0) {
                logger.debug("Removing stale SSE stream for job: {}", jobId);
                connectionCounts.remove(jobId);
                return true;
              }

              return false;
            });

    logger.debug(
        "SSE cleanup completed. Active streams: {}, Total connections: {}",
        activeStreams.size(),
        connectionCounts.values().stream().mapToInt(Integer::intValue).sum());
  }
}
