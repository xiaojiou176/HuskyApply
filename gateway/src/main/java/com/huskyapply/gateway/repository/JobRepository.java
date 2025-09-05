package com.huskyapply.gateway.repository;

import com.huskyapply.gateway.model.Job;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface JobRepository extends ReactiveCrudRepository<Job, UUID> {

  /** Find all jobs for a specific user ordered by creation date. */
  Flux<Job> findByUserIdOrderByCreatedAtDesc(UUID userId);

  /** Find jobs by user with pagination. */
  Flux<Job> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

  /** Find jobs by user and status. */
  Flux<Job> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, String status);

  /** Find recent jobs for a user (limit by count). */
  @Query("SELECT * FROM jobs WHERE user_id = :userId ORDER BY created_at DESC LIMIT 10")
  Flux<Job> findTop10ByUserIdOrderByCreatedAtDesc(UUID userId);

  /** Find jobs created within a time period. */
  Flux<Job> findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(UUID userId, Instant after);

  /** Count jobs by user. */
  Mono<Long> countByUserId(UUID userId);

  /** Count jobs by user and status. */
  Mono<Long> countByUserIdAndStatus(UUID userId, String status);

  /** Count jobs created within a time period. */
  Mono<Long> countByUserIdAndCreatedAtAfter(UUID userId, Instant after);

  /** Get user job statistics - OPTIMIZED using materialized view. */
  @Query(
      "SELECT "
          + "total_jobs, completed_jobs, failed_jobs, pending_jobs, processing_jobs, "
          + "last_job_date, jobs_this_week, jobs_this_month, jobs_this_quarter, "
          + "avg_processing_time_seconds, unique_companies_applied "
          + "FROM user_job_stats_mv WHERE user_id = :userId")
  Mono<Object[]> getUserJobStatsOptimized(UUID userId);

  /** Fallback method for getUserJobStats when materialized view is not available. */
  @Query(
      "SELECT "
          + "COUNT(*) as totalJobs, "
          + "SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) as completedJobs, "
          + "SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) as failedJobs, "
          + "SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END) as pendingJobs, "
          + "SUM(CASE WHEN status = 'PROCESSING' THEN 1 ELSE 0 END) as processingJobs, "
          + "MAX(created_at) as lastJobDate "
          + "FROM jobs WHERE user_id = :userId")
  Mono<Object[]> getUserJobStats(UUID userId);

  /** Full-text search jobs - OPTIMIZED using search vector. */
  @Query(
      "SELECT * FROM jobs WHERE user_id = :userId AND "
          + "search_vector @@ plainto_tsquery('english', :searchTerm) "
          + "ORDER BY ts_rank(search_vector, plainto_tsquery('english', :searchTerm)) DESC, "
          + "created_at DESC LIMIT :limit")
  Flux<Job> searchByUserAndTermOptimized(UUID userId, String searchTerm, int limit);

  /** Fallback search method using LIKE for backward compatibility. */
  @Query(
      "SELECT * FROM jobs WHERE user_id = :userId AND "
          + "(LOWER(company_name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR "
          + "LOWER(job_title) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) "
          + "ORDER BY created_at DESC")
  Flux<Job> searchByUserAndTerm(UUID userId, String searchTerm);

  /** Find jobs for batch processing - OPTIMIZED with index. */
  @Query("SELECT * FROM jobs WHERE batch_job_id = :batchJobId ORDER BY created_at ASC")
  Flux<Job> findByBatchJobIdOrderByCreatedAtAsc(UUID batchJobId);

  /** Count active jobs for user (for quota checking) - OPTIMIZED with index. */
  @Query(
      "SELECT COUNT(*) FROM jobs WHERE user_id = :userId AND status IN ('PENDING', 'PROCESSING')")
  Mono<Long> countActiveJobsByUser(UUID userId);

  /** Find recent successful jobs for portfolio display - uses covering index. */
  @Query(
      "SELECT * FROM jobs WHERE user_id = :userId AND status = 'COMPLETED' "
          + "ORDER BY created_at DESC")
  Flux<Job> findRecentCompletedJobsByUser(UUID userId, Pageable pageable);

  /** Find jobs by status and age for cleanup operations - OPTIMIZED with composite index. */
  @Query(
      "SELECT * FROM jobs WHERE status = ANY(:statuses) AND created_at < :cutoffDate "
          + "ORDER BY created_at ASC")
  Flux<Job> findJobsForCleanup(String[] statuses, Instant cutoffDate);

  /** Get daily job statistics for admin dashboard. */
  @Query(
      "SELECT date, status, job_count, avg_processing_time_seconds, "
          + "median_processing_time_seconds, p95_processing_time_seconds, unique_users "
          + "FROM job_performance_metrics_mv "
          + "WHERE date >= :fromDate ORDER BY date DESC, status")
  Flux<Object[]> getJobPerformanceMetrics(Instant fromDate);

  /** Find popular skills across all jobs. */
  @Query(
      "SELECT skill, frequency, job_count, user_count, success_rate "
          + "FROM popular_skills_mv "
          + "ORDER BY frequency DESC LIMIT :limit")
  Flux<Object[]> getPopularSkills(int limit);

  /** Check if user has pending jobs (for preventing duplicate submissions). */
  @Query(
      "SELECT CASE WHEN COUNT(*) > 0 THEN true ELSE false END FROM jobs "
          + "WHERE user_id = :userId AND jd_url = :jdUrl AND status IN ('PENDING', 'PROCESSING')")
  Mono<Boolean> hasUserPendingJobForUrl(UUID userId, String jdUrl);
}
