package com.huskyapply.gateway.repository;

import com.huskyapply.gateway.model.Job;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive Job Repository using R2DBC for non-blocking database operations
 *
 * <p>This repository provides fully reactive database access using R2DBC, enabling non-blocking I/O
 * operations with PostgreSQL.
 *
 * <p>Key benefits: - Non-blocking database operations - Reactive streams with backpressure -
 * Connection pool optimization - Parallel query execution
 */
@Repository
public interface ReactiveJobRepository extends ReactiveCrudRepository<Job, UUID> {

  /** Find all jobs for a specific user (reactive) */
  Flux<Job> findByUserId(UUID userId);

  /** Find jobs by user and status (reactive) */
  Flux<Job> findByUserIdAndStatus(UUID userId, String status);

  /** Count total jobs for a user (reactive) */
  Mono<Long> countByUserId(UUID userId);

  /** Count jobs by user and status (reactive) */
  Mono<Long> countByUserIdAndStatus(UUID userId, String status);

  /** Find jobs by status with custom query (reactive) */
  @Query("SELECT * FROM jobs WHERE status = :status ORDER BY created_at DESC")
  Flux<Job> findByStatusOrderByCreatedAtDesc(String status);

  /** Find jobs created within a time period (reactive) */
  @Query(
      "SELECT * FROM jobs WHERE user_id = :userId AND created_at >= NOW() - INTERVAL ':days days'")
  Flux<Job> findByUserIdAndCreatedAtAfter(UUID userId, int days);

  /** Count jobs by status across all users (reactive) */
  @Query("SELECT COUNT(*) FROM jobs WHERE status = :status")
  Mono<Long> countByStatus(String status);

  /** Find jobs with pagination using limit/offset (reactive) */
  @Query(
      "SELECT * FROM jobs WHERE user_id = :userId ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
  Flux<Job> findByUserIdWithPagination(UUID userId, int limit, int offset);

  /** Get job statistics grouped by status (reactive aggregation) */
  @Query("SELECT status, COUNT(*) as count FROM jobs WHERE user_id = :userId GROUP BY status")
  Flux<JobStatusCount> getJobStatusCounts(UUID userId);

  /** Find jobs that need cleanup (older than specified days) */
  @Query(
      "SELECT * FROM jobs WHERE status IN ('COMPLETED', 'FAILED') AND created_at < NOW() - INTERVAL ':days days'")
  Flux<Job> findJobsForCleanup(int days);

  /** Update job status atomically (reactive) */
  @Query("UPDATE jobs SET status = :status, updated_at = NOW() WHERE id = :jobId")
  Mono<Void> updateJobStatus(UUID jobId, String status);

  /** Batch update job statuses (reactive) */
  @Query("UPDATE jobs SET status = :newStatus WHERE status = :oldStatus AND user_id = :userId")
  Mono<Integer> updateJobStatusBatch(UUID userId, String oldStatus, String newStatus);

  /** Interface for job status count aggregation */
  interface JobStatusCount {
    String getStatus();

    Long getCount();
  }
}
