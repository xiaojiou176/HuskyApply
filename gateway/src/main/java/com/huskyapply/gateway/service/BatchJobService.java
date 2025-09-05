package com.huskyapply.gateway.service;

import com.huskyapply.gateway.dto.BatchJobRequest;
import com.huskyapply.gateway.dto.BatchJobResponse;
import com.huskyapply.gateway.exception.ResourceNotFoundException;
import com.huskyapply.gateway.model.BatchJob;
import com.huskyapply.gateway.model.Job;
import com.huskyapply.gateway.model.User;
import com.huskyapply.gateway.repository.BatchJobRepository;
import com.huskyapply.gateway.repository.JobRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class BatchJobService {

  private final BatchJobRepository batchJobRepository;
  private final JobRepository jobRepository;
  private final JobService jobService;

  public BatchJobService(
      BatchJobRepository batchJobRepository, JobRepository jobRepository, JobService jobService) {
    this.batchJobRepository = batchJobRepository;
    this.jobRepository = jobRepository;
    this.jobService = jobService;
  }

  public BatchJobResponse createBatchJob(User user, BatchJobRequest request) {
    BatchJob batchJob = new BatchJob();
    batchJob.setUser(user);
    batchJob.setName(request.getName());
    batchJob.setDescription(request.getDescription());
    batchJob.setTemplateId(request.getTemplateId());
    batchJob.setModelProvider(request.getModelProvider());
    batchJob.setModelName(request.getModelName());
    batchJob.setStatus("PENDING");
    batchJob.setTotalJobs(request.getJobUrls().size());

    batchJob = batchJobRepository.save(batchJob);

    List<Job> jobs = createJobsForBatch(user, batchJob, request);
    batchJob.setJobs(jobs);

    if (request.getAutoStart()) {
      startBatchJob(batchJob);
    }

    return toBatchJobResponse(batchJob);
  }

  public BatchJobResponse getBatchJob(User user, UUID batchJobId) {
    BatchJob batchJob =
        batchJobRepository
            .findById(batchJobId)
            .orElseThrow(() -> new ResourceNotFoundException("Batch job not found"));

    if (!batchJob.getUser().getId().equals(user.getId())) {
      throw new ResourceNotFoundException("Batch job not found");
    }

    return toBatchJobResponse(batchJob);
  }

  public Page<BatchJobResponse> getBatchJobs(User user, String status, Pageable pageable) {
    Page<BatchJob> batchJobs;
    if (status != null && !status.isEmpty()) {
      batchJobs =
          batchJobRepository.findByUserAndStatusOrderByCreatedAtDesc(user, status, pageable);
    } else {
      batchJobs = batchJobRepository.findByUserOrderByCreatedAtDesc(user, pageable);
    }
    return batchJobs.map(this::toBatchJobResponse);
  }

  public List<BatchJobResponse> searchBatchJobs(User user, String searchTerm) {
    List<BatchJob> batchJobs = batchJobRepository.searchByUserAndTerm(user, searchTerm);
    return batchJobs.stream().map(this::toBatchJobResponse).collect(Collectors.toList());
  }

  public BatchJobResponse startBatchJob(User user, UUID batchJobId) {
    BatchJob batchJob =
        batchJobRepository
            .findById(batchJobId)
            .orElseThrow(() -> new ResourceNotFoundException("Batch job not found"));

    if (!batchJob.getUser().getId().equals(user.getId())) {
      throw new ResourceNotFoundException("Batch job not found");
    }

    return toBatchJobResponse(startBatchJob(batchJob));
  }

  public BatchJobResponse cancelBatchJob(User user, UUID batchJobId) {
    BatchJob batchJob =
        batchJobRepository
            .findById(batchJobId)
            .orElseThrow(() -> new ResourceNotFoundException("Batch job not found"));

    if (!batchJob.getUser().getId().equals(user.getId())) {
      throw new ResourceNotFoundException("Batch job not found");
    }

    if (batchJob.isCompleted()) {
      throw new IllegalStateException("Cannot cancel completed batch job");
    }

    batchJob.setStatus("CANCELLED");
    batchJob.setCompletedAt(Instant.now());
    batchJob.setUpdatedAt(Instant.now());

    List<Job> pendingJobs =
        batchJob.getJobs().stream()
            .filter(
                job -> "PENDING".equals(job.getStatus()) || "PROCESSING".equals(job.getStatus()))
            .collect(Collectors.toList());

    for (Job job : pendingJobs) {
      job.setStatus("CANCELLED");
      jobRepository.save(job);
    }

    batchJob = batchJobRepository.save(batchJob);
    return toBatchJobResponse(batchJob);
  }

  public void deleteBatchJob(User user, UUID batchJobId) {
    BatchJob batchJob =
        batchJobRepository
            .findById(batchJobId)
            .orElseThrow(() -> new ResourceNotFoundException("Batch job not found"));

    if (!batchJob.getUser().getId().equals(user.getId())) {
      throw new ResourceNotFoundException("Batch job not found");
    }

    batchJobRepository.delete(batchJob);
  }

  @Transactional(readOnly = true)
  public List<BatchJobResponse> getRecentBatchJobs(User user) {
    List<BatchJob> batchJobs = batchJobRepository.findTop10ByUserOrderByCreatedAtDesc(user);
    return batchJobs.stream().map(this::toBatchJobResponse).collect(Collectors.toList());
  }

  private BatchJob startBatchJob(BatchJob batchJob) {
    if (!"PENDING".equals(batchJob.getStatus())) {
      throw new IllegalStateException("Batch job is not in PENDING status");
    }

    batchJob.setStatus("PROCESSING");
    batchJob.setStartedAt(Instant.now());
    batchJob.setUpdatedAt(Instant.now());

    batchJob = batchJobRepository.save(batchJob);

    for (Job job : batchJob.getJobs()) {
      if ("PENDING".equals(job.getStatus())) {
        try {
          jobService.processJob(job.getId(), batchJob.getModelProvider(), batchJob.getModelName());
        } catch (Exception e) {
          job.setStatus("FAILED");
          jobRepository.save(job);
        }
      }
    }

    return batchJob;
  }

  private List<Job> createJobsForBatch(User user, BatchJob batchJob, BatchJobRequest request) {
    return request.getJobUrls().stream()
        .map(
            jobUrl -> {
              Job job =
                  Job.builder()
                      .jdUrl(jobUrl)
                      .resumeUri(request.getResumeUri())
                      .status("PENDING")
                      .user(user)
                      .createdAt(Instant.now())
                      .build();

              job.setBatchJob(batchJob);
              return jobRepository.save(job);
            })
        .collect(Collectors.toList());
  }

  private BatchJobResponse toBatchJobResponse(BatchJob batchJob) {
    return new BatchJobResponse(
        batchJob.getId().toString(),
        batchJob.getName(),
        batchJob.getDescription(),
        batchJob.getStatus(),
        batchJob.getTotalJobs(),
        batchJob.getCompletedJobs(),
        batchJob.getFailedJobs(),
        batchJob.getProgressPercentage(),
        batchJob.getTemplateId() != null ? batchJob.getTemplateId().toString() : null,
        batchJob.getModelProvider(),
        batchJob.getModelName(),
        batchJob.getCreatedAt(),
        batchJob.getUpdatedAt(),
        batchJob.getStartedAt(),
        batchJob.getCompletedAt());
  }
}
