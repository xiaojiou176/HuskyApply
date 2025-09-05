package com.huskyapply.gateway.service;

import com.huskyapply.gateway.dto.ArtifactResponse;
import com.huskyapply.gateway.dto.JobCreationRequest;
import com.huskyapply.gateway.dto.StatusUpdateEvent;
import com.huskyapply.gateway.exception.ArtifactNotFoundException;
// Temporarily commented out gRPC imports
// import com.huskyapply.gateway.grpc.JobProcessingClient;
import com.huskyapply.gateway.model.Artifact;
import com.huskyapply.gateway.model.Job;
import com.huskyapply.gateway.model.User;
import com.huskyapply.gateway.repository.ArtifactRepository;
import com.huskyapply.gateway.repository.JobRepository;
import com.huskyapply.gateway.service.messaging.MessageBatchingService;
// import com.huskyapply.grpc.jobprocessing.v1.JobSubmissionResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobService {

  private static final Logger logger = LoggerFactory.getLogger(JobService.class);

  @Value("${rabbitmq.exchange.name:job_exchange}")
  private String exchangeName;

  @Value("${rabbitmq.routing.key:job.processing}")
  private String routingKey;

  @Value("${grpc.enabled:false}") // Temporarily disabled for RabbitMQ focus  
  private boolean grpcEnabled;

  @Value("${rabbitmq.fallback.enabled:true}")
  private boolean rabbitMQFallbackEnabled;

  private final JobRepository jobRepository;
  private final ArtifactRepository artifactRepository;
  private final RabbitTemplate rabbitTemplate;
  private final SubscriptionService subscriptionService;
  private final MessageBatchingService batchingService;

  // gRPC client for Brain communication
  // private final JobProcessingClient grpcClient; // Temporarily disabled

  // Use @Autowired to avoid circular dependency issues
  @Autowired private DashboardService dashboardService;

  // Enhanced multi-layer caching
  @Autowired private MultiLayerCacheService multiLayerCache;

  public JobService(
      JobRepository jobRepository,
      ArtifactRepository artifactRepository,
      RabbitTemplate rabbitTemplate,
      SubscriptionService subscriptionService,
      MessageBatchingService batchingService) {
      // JobProcessingClient grpcClient) { // Temporarily disabled
    this.jobRepository = jobRepository;
    this.artifactRepository = artifactRepository;
    this.rabbitTemplate = rabbitTemplate;
    this.subscriptionService = subscriptionService;
    this.batchingService = batchingService;
    // this.grpcClient = grpcClient; // Temporarily disabled

    logger.info(
        "JobService initialized with gRPC enabled: {}, RabbitMQ fallback: {}",
        grpcEnabled,
        rabbitMQFallbackEnabled);
  }

  public Job createJob(JobCreationRequest request, User user, String traceId) {
    // Check subscription limits
    subscriptionService.validateJobCreationLimits(user);

    // Check AI model access
    if (!subscriptionService.hasModelAccess(
        user, request.getModelProvider(), request.getModelName())) {
      throw new IllegalArgumentException(
          "Your subscription plan does not include access to the selected AI model");
    }

    Job job =
        Job.builder()
            .jdUrl(request.getJdUrl())
            .resumeUri(request.getResumeUri())
            .status("PENDING")
            .user(user)
            .build();

    Job createdJob = jobRepository.save(job);

    // Attempt gRPC submission first, fallback to RabbitMQ if enabled
    boolean submissionSuccessful = false;

    if (grpcEnabled) {
      // submissionSuccessful = submitJobViaGrpc(createdJob, request, user, traceId); // Temporarily disabled
      logger.info("gRPC is disabled, skipping gRPC submission for job {}", createdJob.getId());
    }

    // Fallback to RabbitMQ if gRPC fails and fallback is enabled
    if (!submissionSuccessful && rabbitMQFallbackEnabled) {
      logger.info(
          "gRPC submission failed or disabled, falling back to RabbitMQ for job {}",
          createdJob.getId());
      submitJobViaRabbitMQ(createdJob, request, user, traceId);
    } else if (!submissionSuccessful) {
      logger.error(
          "Job submission failed for job {} - no available communication method",
          createdJob.getId());
      // Update job status to failed
      createdJob.setStatus("FAILED");
      jobRepository.save(createdJob);
      throw new RuntimeException(
          "Failed to submit job for processing - no available communication method");
    }

    return createdJob;
  }

  /*
  // Temporarily commented out gRPC method 
  private boolean submitJobViaGrpc(Job job, JobCreationRequest request, User user, String traceId) {
    try {
      logger.info("Submitting job {} via gRPC to Brain service", job.getId());

      // Submit job asynchronously via gRPC
      CompletableFuture<JobSubmissionResponse> responseFuture =
          grpcClient.submitJob(
              job,
              user,
              request.getModelProvider() != null ? request.getModelProvider() : "openai",
              request.getModelName() != null ? request.getModelName() : "gpt-4o",
              traceId);

      // Handle response asynchronously to avoid blocking
      responseFuture.whenComplete(
          (response, throwable) -> {
            if (throwable != null) {
              logger.error(
                  "gRPC job submission failed for job {}: {}", job.getId(), throwable.getMessage());
              // Could trigger fallback mechanism here if needed
            } else {
              logger.info(
                  "gRPC job submission successful for job {}: status={}, queue_position={}",
                  job.getId(),
                  response.getStatus(),
                  response.getQueuePosition());

              // Update job with estimated completion time if provided
              if (response.getEstimatedCompletionMs() > 0) {
                try {
                  job.setStatus("QUEUED"); // Update to more specific status
                  jobRepository.save(job);
                } catch (Exception e) {
                  logger.warn(
                      "Failed to update job status after gRPC submission: {}", e.getMessage());
                }
              }
            }
          });

      logger.debug("gRPC job submission initiated successfully for job {}", job.getId());
      return true;

    } catch (Exception e) {
      logger.error("Failed to submit job {} via gRPC: {}", job.getId(), e.getMessage(), e);
      return false;
    }
  }

  /**
   * Submit job via RabbitMQ using the traditional message queue approach.
   *
   * @param job The created job entity
   * @param request Original job creation request
   * @param user User who created the job
   * @param traceId Distributed tracing ID
   */
  private void submitJobViaRabbitMQ(
      Job job, JobCreationRequest request, User user, String traceId) {
    try {
      logger.info("Submitting job {} via RabbitMQ to Brain service", job.getId());

      Map<String, Object> messagePayload = new HashMap<>();
      messagePayload.put("jobId", job.getId());
      messagePayload.put("jdUrl", job.getJdUrl());
      messagePayload.put("resumeUri", job.getResumeUri());
      messagePayload.put(
          "modelProvider",
          request.getModelProvider() != null ? request.getModelProvider() : "openai");
      messagePayload.put(
          "modelName", request.getModelName() != null ? request.getModelName() : "gpt-4o");
      messagePayload.put("userId", user.getId().toString());
      messagePayload.put("traceId", traceId);

      // Determine priority based on subscription plan or request
      String priority = determinePriority(user, request);
      messagePayload.put("priority", priority);

      // Use batching service for high-throughput message publishing
      String dynamicRoutingKey = buildRoutingKey(priority);
      batchingService.addToBatch(messagePayload, exchangeName, dynamicRoutingKey);

      logger.debug("RabbitMQ job submission completed for job {}", job.getId());

    } catch (Exception e) {
      logger.error("Failed to submit job {} via RabbitMQ: {}", job.getId(), e.getMessage(), e);
      throw new RuntimeException("Failed to submit job via RabbitMQ", e);
    }
  }

  @Transactional
  public void handleStatusUpdate(UUID jobId, StatusUpdateEvent event) {
    logger.info("Handling status update for job {} with status {}", jobId, event.getStatus());

    Optional<Job> jobOptional = jobRepository.findById(jobId);
    if (jobOptional.isEmpty()) {
      logger.warn("Job not found for ID: {}", jobId);
      return;
    }

    Job job = jobOptional.get();
    String oldStatus = job.getStatus();
    job.setStatus(event.getStatus());
    jobRepository.save(job);

    // Enhanced cache invalidation when job status changes
    if (!event.getStatus().equals(oldStatus)) {
      try {
        // Evict traditional dashboard caches
        dashboardService.evictUserStatsCache(job.getUser().getId().toString());
        dashboardService.evictUserJobsCache(job.getUser().getId().toString());

        // Evict multi-layer caches
        multiLayerCache.evict("jobs-metadata", jobId.toString());
        multiLayerCache.evict("dashboard-stats", job.getUser().getId().toString());

        logger.debug(
            "Evicted all cache layers for user {} and job {} due to status change",
            job.getUser().getId(),
            jobId);
      } catch (Exception e) {
        logger.warn(
            "Failed to evict caches for user {} and job {}: {}",
            job.getUser().getId(),
            jobId,
            e.getMessage());
      }
    }

    // If status is COMPLETED and content is provided, persist the artifact
    if ("COMPLETED".equals(event.getStatus())
        && event.getContent() != null
        && !event.getContent().trim().isEmpty()) {
      logger.info("Creating artifact for completed job {}", jobId);

      // Calculate word count
      int wordCount = event.getContent().trim().split("\\s+").length;

      Artifact artifact =
          Artifact.builder()
              .job(job)
              .contentType("cover_letter")
              .generatedText(event.getContent())
              .wordCount(wordCount)
              .build();

      artifactRepository.save(artifact);
      logger.info("Successfully saved artifact for job {} with {} words", jobId, wordCount);
    }
  }

  /**
   * Retrieves the artifact for a given job ID with enhanced multi-layer caching. Uses L1 (Caffeine)
   * + L2 (Redis) cache hierarchy for optimal performance.
   *
   * @param jobId the UUID of the job
   * @return ArtifactResponse containing the artifact data
   * @throws ArtifactNotFoundException if no artifact is found for the job
   */
  public ArtifactResponse getArtifactForJob(UUID jobId) {
    logger.info("Retrieving artifact for job {}", jobId);

    // Use multi-layer cache with intelligent fallback
    Optional<ArtifactResponse> cachedResult =
        multiLayerCache.get(
            "jobs-metadata",
            jobId.toString(),
            ArtifactResponse.class,
            () -> {
              // Cache miss - fetch from database
              Optional<Artifact> artifactOptional = artifactRepository.findByJobId(jobId);

              if (artifactOptional.isEmpty()) {
                return null; // Will throw exception after cache check
              }

              Artifact artifact = artifactOptional.get();
              logger.info(
                  "Loaded artifact from database for job {} with content type: {}",
                  jobId,
                  artifact.getContentType());

              return new ArtifactResponse(
                  artifact.getJob().getId(),
                  artifact.getContentType(),
                  artifact.getGeneratedText(),
                  artifact.getCreatedAt());
            });

    if (cachedResult.isEmpty()) {
      logger.warn("No artifact found for job ID: {}", jobId);
      throw new ArtifactNotFoundException(jobId);
    }

    return cachedResult.get();
  }

  /**
   * Processes a job by sending it to the message queue for AI processing.
   *
   * @param jobId the UUID of the job to process
   * @throws IllegalStateException if job is not in PENDING status
   */
  public void processJob(UUID jobId) {
    processJob(jobId, "openai", null);
  }

  public void processJob(UUID jobId, String modelProvider, String modelName) {
    logger.info(
        "Processing job {} with model provider: {}, model: {}", jobId, modelProvider, modelName);

    Optional<Job> jobOptional = jobRepository.findById(jobId);
    if (jobOptional.isEmpty()) {
      logger.warn("Job not found for ID: {}", jobId);
      throw new IllegalArgumentException("Job not found: " + jobId);
    }

    Job job = jobOptional.get();
    if (!"PENDING".equals(job.getStatus())) {
      throw new IllegalStateException("Job is not in PENDING status: " + job.getStatus());
    }

    job.setStatus("PROCESSING");
    jobRepository.save(job);

    String traceId = "batch-" + UUID.randomUUID().toString();

    Map<String, Object> messagePayload = new HashMap<>();
    messagePayload.put("jobId", job.getId());
    messagePayload.put("jdUrl", job.getJdUrl());
    messagePayload.put("resumeUri", job.getResumeUri());
    messagePayload.put("modelProvider", modelProvider != null ? modelProvider : "openai");
    if (modelName != null) {
      messagePayload.put("modelName", modelName);
    }

    rabbitTemplate.convertAndSend(
        exchangeName,
        routingKey,
        messagePayload,
        msg -> {
          msg.getMessageProperties().getHeaders().put("X-Trace-ID", traceId);
          return msg;
        });

    logger.info("Successfully sent job {} to processing queue with trace ID {}", jobId, traceId);
  }

  /** Determine job priority based on user subscription plan and request parameters. */
  private String determinePriority(User user, JobCreationRequest request) {
    // Check if user has premium subscription for high priority
    if (subscriptionService.hasHighPriorityAccess(user)) {
      return "HIGH";
    }

    // Check request-specific priority (e.g., urgent flag)
    if (request.getUrgent() != null && request.getUrgent()) {
      return "EXPRESS";
    }

    // Default to normal priority
    return "NORMAL";
  }

  /** Build dynamic routing key based on priority level. */
  private String buildRoutingKey(String priority) {
    switch (priority.toUpperCase()) {
      case "EXPRESS":
      case "URGENT":
        return "jobs.priority.express";
      case "HIGH":
        return "jobs.priority.high";
      case "LOW":
        return "jobs.priority.low";
      default:
        return "jobs.priority.normal";
    }
  }

  /** Process multiple jobs in batch for improved throughput. */
  public void processJobsBatch(
      java.util.List<UUID> jobIds, String modelProvider, String modelName) {
    logger.info(
        "Processing batch of {} jobs with model provider: {}, model: {}",
        jobIds.size(),
        modelProvider,
        modelName);

    java.util.List<Object> messages = new java.util.ArrayList<>();
    String traceId = "batch-" + UUID.randomUUID().toString();

    for (UUID jobId : jobIds) {
      Optional<Job> jobOptional = jobRepository.findById(jobId);
      if (jobOptional.isEmpty()) {
        logger.warn("Job not found for ID: {}", jobId);
        continue;
      }

      Job job = jobOptional.get();
      if (!"PENDING".equals(job.getStatus())) {
        logger.warn("Job {} is not in PENDING status: {}", jobId, job.getStatus());
        continue;
      }

      job.setStatus("PROCESSING");
      jobRepository.save(job);

      Map<String, Object> messagePayload = new HashMap<>();
      messagePayload.put("jobId", job.getId());
      messagePayload.put("jdUrl", job.getJdUrl());
      messagePayload.put("resumeUri", job.getResumeUri());
      messagePayload.put("modelProvider", modelProvider != null ? modelProvider : "openai");
      if (modelName != null) {
        messagePayload.put("modelName", modelName);
      }
      messagePayload.put("userId", job.getUser().getId().toString());
      messagePayload.put("traceId", traceId);
      messagePayload.put("priority", "NORMAL"); // Batch jobs use normal priority

      messages.add(messagePayload);
    }

    if (!messages.isEmpty()) {
      // Use bulk batching for improved efficiency
      batchingService.addToBatchBulk(messages, exchangeName, "jobs.priority.normal");
      logger.info(
          "Successfully queued {} jobs for batch processing with trace ID {}",
          messages.size(),
          traceId);
    }
  }

  /** Force flush all pending batches (useful for shutdown or maintenance). */
  public void flushPendingMessages() {
    logger.info("Flushing all pending message batches");
    batchingService.flushAllBatches();
  }

  /** Get message batching statistics for monitoring. */
  public MessageBatchingService.BatchingStats getBatchingStats() {
    return batchingService.getStats();
  }
}
