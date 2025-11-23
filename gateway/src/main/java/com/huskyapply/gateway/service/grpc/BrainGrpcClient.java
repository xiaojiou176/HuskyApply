package com.huskyapply.gateway.service.grpc;

import com.huskyapply.brain.proto.*;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * gRPC client for communicating with Brain service. Handles job submission, streaming updates, and
 * result retrieval.
 */
@Service
@ConditionalOnProperty(name = "huskyapply.migration.grpc-enabled", havingValue = "true")
public class BrainGrpcClient {

  private static final Logger logger = LoggerFactory.getLogger(BrainGrpcClient.class);

  private final JobProcessingServiceGrpc.JobProcessingServiceStub asyncStub;
  private final JobProcessingServiceGrpc.JobProcessingServiceBlockingStub blockingStub;

  @Value("${huskyapply.migration.grpc-fallback-timeout-ms:5000}")
  private long fallbackTimeoutMs;

  @Value("${huskyapply.migration.max-grpc-retries:3}")
  private int maxRetries;

  @Autowired
  public BrainGrpcClient(
      JobProcessingServiceGrpc.JobProcessingServiceStub asyncStub,
      JobProcessingServiceGrpc.JobProcessingServiceBlockingStub blockingStub) {
    this.asyncStub = asyncStub;
    this.blockingStub = blockingStub;
    logger.info("BrainGrpcClient initialized");
  }

  /**
   * Submit a job to Brain service asynchronously.
   *
   * @param request Job submission request
   * @param onSuccess Callback for successful submission
   * @param onError Callback for errors
   */
  public void submitJobAsync(
      JobSubmissionRequest request,
      Consumer<JobSubmissionResponse> onSuccess,
      Consumer<Throwable> onError) {

    logger.info("Submitting job {} to Brain service via gRPC", request.getJobId());

    asyncStub.submitJob(
        request,
        new StreamObserver<JobSubmissionResponse>() {
          @Override
          public void onNext(JobSubmissionResponse response) {
            logger.info(
                "Job {} submitted successfully: {}", response.getJobId(), response.getMessage());
            onSuccess.accept(response);
          }

          @Override
          public void onError(Throwable t) {
            logger.error("Error submitting job {}: {}", request.getJobId(), t.getMessage());
            onError.accept(t);
          }

          @Override
          public void onCompleted() {
            logger.debug("Job submission completed");
          }
        });
  }

  /**
   * Submit a job synchronously with timeout.
   *
   * @param request Job submission request
   * @return Job submission response
   * @throws StatusRuntimeException if gRPC call fails
   */
  public JobSubmissionResponse submitJobSync(JobSubmissionRequest request) {
    logger.info("Submitting job {} to Brain service via gRPC (blocking)", request.getJobId());

    try {
      JobSubmissionResponse response =
          blockingStub
              .withDeadlineAfter(fallbackTimeoutMs, TimeUnit.MILLISECONDS)
              .submitJob(request);

      logger.info("Job {} submitted successfully: {}", response.getJobId(), response.getMessage());
      return response;

    } catch (StatusRuntimeException e) {
      logger.error("Error submitting job {}: {}", request.getJobId(), e.getStatus());
      throw e;
    }
  }

  /**
   * Stream job updates in real-time.
   *
   * @param jobIds List of job IDs to stream updates for
   * @return Reactive Flux of job updates
   */
  public Flux<JobUpdateResponse> streamJobUpdates(List<String> jobIds) {
    logger.info("Starting job update stream for {} jobs", jobIds.size());

    return Flux.create(
        sink -> {
          JobUpdateRequest request =
              JobUpdateRequest.newBuilder().addAllJobIds(jobIds).setIncludeContent(true).build();

          asyncStub.streamJobUpdates(
              request,
              new StreamObserver<JobUpdateResponse>() {
                @Override
                public void onNext(JobUpdateResponse update) {
                  logger.debug(
                      "Received update for job {}: {}", update.getJobId(), update.getMessage());
                  sink.next(update);
                }

                @Override
                public void onError(Throwable t) {
                  logger.error("Error in job update stream: {}", t.getMessage());
                  sink.error(t);
                }

                @Override
                public void onCompleted() {
                  logger.info("Job update stream completed");
                  sink.complete();
                }
              });
        });
  }

  /**
   * Stream job updates for a single job.
   *
   * @param jobId Job ID to stream updates for
   * @return Reactive Flux of job updates
   */
  public Flux<JobUpdateResponse> streamJobUpdates(String jobId) {
    return streamJobUpdates(List.of(jobId));
  }

  /**
   * Get job result synchronously.
   *
   * @param jobId Job ID
   * @param userId User ID
   * @return Job result response
   * @throws StatusRuntimeException if gRPC call fails
   */
  public JobResultResponse getJobResult(String jobId, String userId) {
    logger.info("Fetching result for job {}", jobId);

    JobResultRequest request =
        JobResultRequest.newBuilder().setJobId(jobId).setUserId(userId).build();

    try {
      JobResultResponse response =
          blockingStub
              .withDeadlineAfter(fallbackTimeoutMs, TimeUnit.MILLISECONDS)
              .getJobResult(request);

      logger.info("Job {} result retrieved successfully", jobId);
      return response;

    } catch (StatusRuntimeException e) {
      logger.error("Error fetching result for job {}: {}", jobId, e.getStatus());
      throw e;
    }
  }

  /**
   * Cancel a job.
   *
   * @param jobId Job ID
   * @param userId User ID
   * @param reason Cancellation reason
   * @return Cancellation response
   * @throws StatusRuntimeException if gRPC call fails
   */
  public CancelJobResponse cancelJob(String jobId, String userId, String reason) {
    logger.info("Cancelling job {}: {}", jobId, reason);

    CancelJobRequest request =
        CancelJobRequest.newBuilder().setJobId(jobId).setUserId(userId).setReason(reason).build();

    try {
      CancelJobResponse response =
          blockingStub
              .withDeadlineAfter(fallbackTimeoutMs, TimeUnit.MILLISECONDS)
              .cancelJob(request);

      logger.info("Job {} cancellation: {}", jobId, response.getMessage());
      return response;

    } catch (StatusRuntimeException e) {
      logger.error("Error cancelling job {}: {}", jobId, e.getStatus());
      throw e;
    }
  }

  /**
   * Check Brain service health.
   *
   * @return Health response
   * @throws StatusRuntimeException if gRPC call fails
   */
  public HealthResponse checkHealth() {
    logger.debug("Checking Brain service health");

    HealthRequest request = HealthRequest.newBuilder().setServiceId("gateway").build();

    try {
      HealthResponse response =
          blockingStub.withDeadlineAfter(2000, TimeUnit.MILLISECONDS).healthCheck(request);

      logger.debug("Brain service health: {}", response.getStatus());
      return response;

    } catch (StatusRuntimeException e) {
      logger.error("Health check failed: {}", e.getStatus());
      throw e;
    }
  }

  /**
   * Get processing metrics from Brain service.
   *
   * @return Metrics response
   * @throws StatusRuntimeException if gRPC call fails
   */
  public MetricsResponse getProcessingMetrics() {
    logger.info("Fetching processing metrics from Brain service");

    MetricsRequest request = MetricsRequest.newBuilder().setServiceId("gateway").build();

    try {
      MetricsResponse response =
          blockingStub
              .withDeadlineAfter(fallbackTimeoutMs, TimeUnit.MILLISECONDS)
              .getProcessingMetrics(request);

      logger.info("Processing metrics retrieved successfully");
      return response;

    } catch (StatusRuntimeException e) {
      logger.error("Error fetching processing metrics: {}", e.getStatus());
      throw e;
    }
  }

  /**
   * Submit a job with retries.
   *
   * @param request Job submission request
   * @return Job submission response
   * @throws RuntimeException if all retries fail
   */
  public JobSubmissionResponse submitJobWithRetries(JobSubmissionRequest request) {
    int attempt = 0;
    Exception lastException = null;

    while (attempt < maxRetries) {
      attempt++;
      try {
        logger.info("Submitting job {} (attempt {}/{})", request.getJobId(), attempt, maxRetries);

        return submitJobSync(request);

      } catch (StatusRuntimeException e) {
        lastException = e;
        logger.warn("Job submission attempt {} failed: {}", attempt, e.getStatus());

        if (attempt < maxRetries) {
          try {
            long backoffMs = (long) Math.pow(2, attempt) * 100;
            logger.info("Retrying after {}ms", backoffMs);
            Thread.sleep(backoffMs);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while retrying", ie);
          }
        }
      }
    }

    throw new RuntimeException(
        "Failed to submit job after " + maxRetries + " attempts", lastException);
  }
}
