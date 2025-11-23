package com.huskyapply.gateway.controller;

import com.huskyapply.gateway.dto.ArtifactResponse;
import com.huskyapply.gateway.dto.JobCreationRequest;
import com.huskyapply.gateway.dto.JobCreationResponse;
import com.huskyapply.gateway.dto.StatusUpdateEvent;
import com.huskyapply.gateway.exception.ArtifactNotFoundException;
import com.huskyapply.gateway.model.User;
import com.huskyapply.gateway.service.JwtService;
import com.huskyapply.gateway.service.ReactiveJobService;
import com.huskyapply.gateway.service.ReactiveSseManager;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;

/**
 * Reactive Job Controller using Spring WebFlux
 *
 * <p>This controller implements non-blocking, reactive endpoints that can handle 10x more
 * concurrent connections with the same hardware resources.
 *
 * <p>Key benefits: - Non-blocking I/O operations - Memory efficient (no thread-per-request) -
 * Backpressure support - Composable async operations
 */
@RestController
@RequestMapping("/api/v2")
public class ReactiveJobController {

  private static final Logger logger = LoggerFactory.getLogger(ReactiveJobController.class);

  private final ReactiveJobService reactiveJobService;
  private final ReactiveSseManager reactiveSseManager;
  private final JwtService jwtService;
  private final ReactiveUserDetailsService reactiveUserDetailsService;

  public ReactiveJobController(
      ReactiveJobService reactiveJobService,
      ReactiveSseManager reactiveSseManager,
      JwtService jwtService,
      ReactiveUserDetailsService reactiveUserDetailsService) {
    this.reactiveJobService = reactiveJobService;
    this.reactiveSseManager = reactiveSseManager;
    this.jwtService = jwtService;
    this.reactiveUserDetailsService = reactiveUserDetailsService;
  }

  /**
   * Reactive job creation endpoint
   *
   * <p>Uses Mono<T> for single-value async operations with automatic backpressure and resource
   * management.
   */
  @PostMapping(value = "/applications", consumes = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<JobCreationResponse>> createJob(
      @AuthenticationPrincipal Mono<User> userMono, @RequestBody JobCreationRequest request) {

    String traceId = UUID.randomUUID().toString();

    return userMono
        .doOnNext(
            user -> {
              MDC.put("traceId", traceId);
              MDC.put("userId", user.getId().toString());
              logger.info("Processing reactive job creation for user: {}", user.getEmail());
            })
        .flatMap(user -> reactiveJobService.createJob(request, user, traceId))
        .map(
            job -> {
              JobCreationResponse response = new JobCreationResponse(job.getId());
              logger.info("Job created successfully: {}", job.getId());
              return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
            })
        .doOnError(error -> logger.error("Job creation failed", error))
        .onErrorReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build())
        .doFinally(signal -> MDC.clear())
        .contextWrite(Context.of("traceId", traceId))
        .subscribeOn(Schedulers.boundedElastic()); // Use bounded elastic for DB operations
  }

  /**
   * Reactive Server-Sent Events stream
   *
   * <p>Returns Flux<ServerSentEvent<T>> for continuous data streaming with automatic connection
   * management and backpressure control.
   */
  @GetMapping(value = "/applications/{jobId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<ServerSentEvent<Object>> streamJobStatus(
      @PathVariable UUID jobId, @RequestParam(name = "token") String token) {

    return validateTokenReactive(token)
        .flatMapMany(
            userDetails -> {
              logger.info("Starting reactive SSE stream for job: {}", jobId);

              // Create SSE stream with heartbeat
              return reactiveSseManager
                  .createStream(jobId)
                  .map(
                      event ->
                          ServerSentEvent.builder()
                              .event("job-status")
                              .data(event)
                              .id(UUID.randomUUID().toString())
                              .build())
                  .mergeWith(
                      // Add heartbeat every 30 seconds
                      Flux.interval(Duration.ofSeconds(30))
                          .map(
                              tick ->
                                  ServerSentEvent.builder()
                                      .event("heartbeat")
                                      .data("ping")
                                      .build()))
                  .takeUntil(
                      event -> {
                        // Complete stream when job is finished
                        if (event.data() instanceof StatusUpdateEvent) {
                          StatusUpdateEvent statusEvent = (StatusUpdateEvent) event.data();
                          return "COMPLETED".equals(statusEvent.getStatus())
                              || "FAILED".equals(statusEvent.getStatus());
                        }
                        return false;
                      })
                  .timeout(Duration.ofMinutes(10)) // 10-minute timeout
                  .doOnNext(event -> logger.debug("Sending SSE event: {}", event))
                  .doOnComplete(() -> logger.info("SSE stream completed for job: {}", jobId))
                  .doOnError(error -> logger.error("SSE stream error for job: {}", jobId, error));
            })
        .onErrorResume(
            error -> {
              logger.error("SSE authentication failed", error);
              return Flux.just(
                  ServerSentEvent.builder()
                      .event("error")
                      .data("Authentication failed: " + error.getMessage())
                      .build());
            });
  }

  /**
   * Reactive job status update endpoint (internal)
   *
   * <p>Processes status updates asynchronously and broadcasts to all connected SSE streams without
   * blocking.
   */
  @PostMapping("/internal/applications/{jobId}/events")
  public Mono<ResponseEntity<Void>> updateJobStatus(
      @PathVariable UUID jobId, @RequestBody StatusUpdateEvent event) {

    return reactiveJobService
        .handleStatusUpdate(jobId, event)
        .then(reactiveSseManager.broadcastEvent(jobId, event))
        .then(
            Mono.fromRunnable(
                () ->
                    logger.info(
                        "Status update processed for job: {}, status: {}",
                        jobId,
                        event.getStatus())))
        .thenReturn(ResponseEntity.ok().build())
        .doOnError(
            error -> logger.error("Failed to process status update for job: {}", jobId, error))
        .onErrorReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build())
        .subscribeOn(Schedulers.parallel()); // Use parallel scheduler for CPU-bound work
  }

  /**
   * Reactive artifact retrieval
   *
   * <p>Retrieves job artifacts with caching and error handling using non-blocking database
   * operations.
   */
  @GetMapping("/applications/{jobId}/artifact")
  public Mono<ResponseEntity<ArtifactResponse>> getArtifact(@PathVariable UUID jobId) {
    return reactiveJobService
        .getArtifactForJob(jobId)
        .map(
            artifact -> {
              logger.info("Retrieved artifact for job: {}", jobId);
              return ResponseEntity.ok(artifact);
            })
        .onErrorResume(
            ArtifactNotFoundException.class,
            error -> {
              logger.warn("Artifact not found for job: {}", jobId);
              return Mono.just(ResponseEntity.notFound().build());
            })
        .onErrorResume(
            error -> {
              logger.error("Failed to retrieve artifact for job: {}", jobId, error);
              return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
            })
        .subscribeOn(Schedulers.boundedElastic());
  }

  /**
   * Reactive batch job processing
   *
   * <p>Processes multiple jobs in parallel using Flux.flatMap with controlled concurrency to
   * prevent resource exhaustion.
   */
  @PostMapping("/applications/batch")
  public Flux<JobCreationResponse> createBatchJobs(
      @AuthenticationPrincipal Mono<User> userMono,
      @RequestBody Flux<JobCreationRequest> requestFlux) {

    return userMono
        .flatMapMany(
            user ->
                requestFlux
                    .doOnNext(
                        request ->
                            logger.debug("Processing batch job request: {}", request.getJdUrl()))
                    .flatMap(
                        request -> {
                          String traceId = UUID.randomUUID().toString();
                          return reactiveJobService
                              .createJob(request, user, traceId)
                              .map(job -> new JobCreationResponse(job.getId()))
                              .doOnError(
                                  error ->
                                      logger.error(
                                          "Batch job creation failed for: {}",
                                          request.getJdUrl(),
                                          error))
                              .onErrorResume(error -> Mono.empty()); // Skip failed jobs
                        },
                        5) // Process max 5 jobs concurrently
            )
        .doOnComplete(() -> logger.info("Batch job processing completed"))
        .subscribeOn(Schedulers.boundedElastic());
  }

  /**
   * Reactive health check endpoint
   *
   * <p>Returns system health information asynchronously including database connectivity, cache
   * status, and message queue health.
   */
  @GetMapping("/health/reactive")
  public Mono<ResponseEntity<Map<String, Object>>> healthCheck() {
    return reactiveJobService
        .getHealthStatus()
        .map(
            healthInfo -> {
              boolean isHealthy = (Boolean) healthInfo.getOrDefault("healthy", false);
              HttpStatus status = isHealthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
              return ResponseEntity.status(status).body(healthInfo);
            })
        .timeout(Duration.ofSeconds(5))
        .onErrorReturn(
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("healthy", false, "error", "Health check timeout")))
        .subscribeOn(Schedulers.parallel());
  }

  // Private helper methods

  private Mono<org.springframework.security.core.userdetails.UserDetails> validateTokenReactive(
      String token) {
    return Mono.fromCallable(() -> jwtService.extractUsername(token))
        .flatMap(username -> reactiveUserDetailsService.findByUsername(username))
        .filter(userDetails -> jwtService.isTokenValid(token, userDetails))
        .switchIfEmpty(Mono.error(new SecurityException("Invalid or expired token")))
        .subscribeOn(Schedulers.boundedElastic());
  }

  /**
   * Global error handler for reactive endpoints
   *
   * <p>Handles errors uniformly across all reactive operations with proper logging and
   * client-friendly error responses.
   */
  @ExceptionHandler(Exception.class)
  public Mono<ResponseEntity<Map<String, String>>> handleReactiveErrors(Exception ex) {
    logger.error("Reactive controller error", ex);

    Map<String, String> errorResponse =
        Map.of(
            "error", ex.getClass().getSimpleName(),
            "message", ex.getMessage() != null ? ex.getMessage() : "Internal server error",
            "timestamp", java.time.Instant.now().toString());

    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse));
  }
}
