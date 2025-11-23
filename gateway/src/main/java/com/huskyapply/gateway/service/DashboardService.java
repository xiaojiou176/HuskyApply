package com.huskyapply.gateway.service;

import com.huskyapply.gateway.dto.DashboardResponse;
import com.huskyapply.gateway.dto.DashboardResponse.JobSummaryDto;
import com.huskyapply.gateway.dto.DashboardResponse.TemplateDto;
import com.huskyapply.gateway.dto.DashboardResponse.UserStatsDto;
import com.huskyapply.gateway.model.Job;
import com.huskyapply.gateway.model.User;
import com.huskyapply.gateway.repository.JobRepository;
import com.huskyapply.gateway.repository.TemplateRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

  private final JobRepository jobRepository;
  private final TemplateRepository templateRepository;
  private final org.springframework.cache.CacheManager cacheManager;

  public DashboardService(
      JobRepository jobRepository,
      TemplateRepository templateRepository,
      org.springframework.cache.CacheManager cacheManager) {
    this.jobRepository = jobRepository;
    this.templateRepository = templateRepository;
    this.cacheManager = cacheManager;
  }

  /** Search for jobs based on a search term. */
  public List<JobSummaryDto> searchJobs(User user, String searchTerm) {
    // This would ideally be a database search
    return getRecentJobs(user).stream()
        .filter(
            job ->
                job.getJobTitle().toLowerCase().contains(searchTerm.toLowerCase())
                    || job.getCompanyName().toLowerCase().contains(searchTerm.toLowerCase()))
        .collect(Collectors.toList());
  }

  /** Filter jobs by status. */
  public List<JobSummaryDto> getJobsByStatus(User user, String status) {
    return getRecentJobs(user).stream()
        .filter(job -> job.getStatus().equalsIgnoreCase(status))
        .collect(Collectors.toList());
  }

  /** Filter templates by category. */
  public List<TemplateDto> getTemplatesByCategory(User user, String category) {
    return getUserTemplates(user).stream()
        .filter(t -> t.getCategory().equalsIgnoreCase(category))
        .collect(Collectors.toList());
  }

  /** Evict user stats cache. */
  public void evictUserStatsCache(String userId) {
    if (cacheManager != null) {
      org.springframework.cache.Cache cache = cacheManager.getCache("dashboard-stats");
      if (cache != null) {
        cache.evict(userId);
      }
    }
  }

  /** Evict user jobs cache. */
  public void evictUserJobsCache(String userId) {
    if (cacheManager != null) {
      org.springframework.cache.Cache cache = cacheManager.getCache("jobs-metadata");
      if (cache != null) {
        cache.evict(userId);
      }
    }
  }

  /** Evict all dashboard related caches for a user. */
  public void evictUserDashboardCache(User user) {
    evictUserStatsCache(user.getId().toString());
    evictUserJobsCache(user.getId().toString());
  }

  /** Assemble full dashboard data for a user. */
  public DashboardResponse getDashboardData(User user) {
    UserStatsDto stats = getUserStats(user);
    List<JobSummaryDto> recentJobs = getRecentJobs(user);
    List<TemplateDto> templates = getUserTemplates(user);
    List<String> categories = getAvailableCategories(user);
    return new DashboardResponse(stats, recentJobs, templates, categories);
  }

  /** Compute user statistics using repository queries. */
  public UserStatsDto getUserStats(User user) {
    long totalJobs = jobRepository.countByUserId(user.getId());
    long completedJobs = jobRepository.countByUserIdAndStatus(user.getId(), "COMPLETED");
    long failedJobs = jobRepository.countByUserIdAndStatus(user.getId(), "FAILED");
    long pendingJobs = jobRepository.countByUserIdAndStatus(user.getId(), "PENDING");
    long processingJobs = jobRepository.countByUserIdAndStatus(user.getId(), "PROCESSING");
    // Get most recent job date (if any)
    Instant lastJobDate =
        jobRepository.findTop10ByUserIdOrderByCreatedAtDesc(user.getId()).stream()
            .findFirst()
            .map(Job::getCreatedAt)
            .orElse(null);
    long jobsThisWeek =
        jobRepository.countByUserIdAndCreatedAtAfter(
            user.getId(), Instant.now().minus(7, ChronoUnit.DAYS));
    long jobsThisMonth =
        jobRepository.countByUserIdAndCreatedAtAfter(
            user.getId(), Instant.now().minus(30, ChronoUnit.DAYS));
    // Use legacy constructor (without new fields) for compatibility
    return new UserStatsDto(
        totalJobs,
        completedJobs,
        failedJobs,
        pendingJobs,
        processingJobs,
        lastJobDate,
        jobsThisWeek,
        jobsThisMonth);
  }

  /** Retrieve the 10 most recent jobs for the user. */
  public List<JobSummaryDto> getRecentJobs(User user) {
    return jobRepository.findTop10ByUserIdOrderByCreatedAtDesc(user.getId()).stream()
        .map(
            job ->
                new JobSummaryDto(
                    job.getId().toString(),
                    job.getJobTitle(),
                    job.getCompanyName(),
                    job.getStatus(),
                    job.getCreatedAt(),
                    job.getArtifacts() != null && !job.getArtifacts().isEmpty()))
        .collect(Collectors.toList());
  }

  /** Retrieve all templates belonging to the user, ordered by update time. */
  public List<TemplateDto> getUserTemplates(User user) {
    return templateRepository.findByUserOrderByUpdatedAtDesc(user).stream()
        .map(
            t ->
                new TemplateDto(
                    t.getId().toString(),
                    t.getName(),
                    t.getDescription(),
                    t.getCategory(),
                    t.getIsDefault(),
                    t.getUsageCount(),
                    t.getCreatedAt(),
                    t.getUpdatedAt()))
        .collect(Collectors.toList());
  }

  /** List distinct template categories available for the user. */
  public List<String> getAvailableCategories(User user) {
    return templateRepository.findDistinctCategoriesByUser(user);
  }

  // Additional search methods could be added here if needed.
}
