package com.huskyapply.gateway.dto;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for user dashboard data.
 *
 * <p>Contains user statistics, recent jobs, templates, and other dashboard information for the user
 * interface.
 */
public class DashboardResponse {

  private UserStatsDto stats;
  private List<JobSummaryDto> recentJobs;
  private List<TemplateDto> templates;
  private List<String> availableCategories;

  public DashboardResponse(
      UserStatsDto stats,
      List<JobSummaryDto> recentJobs,
      List<TemplateDto> templates,
      List<String> availableCategories) {
    this.stats = stats;
    this.recentJobs = recentJobs;
    this.templates = templates;
    this.availableCategories = availableCategories;
  }

  // Getters
  public UserStatsDto getStats() {
    return stats;
  }

  public List<JobSummaryDto> getRecentJobs() {
    return recentJobs;
  }

  public List<TemplateDto> getTemplates() {
    return templates;
  }

  public List<String> getAvailableCategories() {
    return availableCategories;
  }

  /** User statistics DTO for dashboard. */
  public static class UserStatsDto {
    private final Long totalJobs;
    private final Long completedJobs;
    private final Long failedJobs;
    private final Long pendingJobs;
    private final Long processingJobs;
    private final Instant lastJobDate;
    private final Long jobsThisWeek;
    private final Long jobsThisMonth;
    private final Long jobsThisQuarter;
    private final Double avgProcessingTimeSeconds;
    private final Long uniqueCompaniesApplied;

    // Legacy constructor for backward compatibility
    public UserStatsDto(
        Long totalJobs,
        Long completedJobs,
        Long failedJobs,
        Long pendingJobs,
        Long processingJobs,
        Instant lastJobDate,
        Long jobsThisWeek,
        Long jobsThisMonth) {
      this(
          totalJobs,
          completedJobs,
          failedJobs,
          pendingJobs,
          processingJobs,
          lastJobDate,
          jobsThisWeek,
          jobsThisMonth,
          0L,
          null,
          0L);
    }

    // Full constructor with new fields
    public UserStatsDto(
        Long totalJobs,
        Long completedJobs,
        Long failedJobs,
        Long pendingJobs,
        Long processingJobs,
        Instant lastJobDate,
        Long jobsThisWeek,
        Long jobsThisMonth,
        Long jobsThisQuarter,
        Double avgProcessingTimeSeconds,
        Long uniqueCompaniesApplied) {
      this.totalJobs = totalJobs;
      this.completedJobs = completedJobs;
      this.failedJobs = failedJobs;
      this.pendingJobs = pendingJobs;
      this.processingJobs = processingJobs;
      this.lastJobDate = lastJobDate;
      this.jobsThisWeek = jobsThisWeek;
      this.jobsThisMonth = jobsThisMonth;
      this.jobsThisQuarter = jobsThisQuarter;
      this.avgProcessingTimeSeconds = avgProcessingTimeSeconds;
      this.uniqueCompaniesApplied = uniqueCompaniesApplied;
    }

    // Getters
    public Long getTotalJobs() {
      return totalJobs;
    }

    public Long getCompletedJobs() {
      return completedJobs;
    }

    public Long getFailedJobs() {
      return failedJobs;
    }

    public Long getPendingJobs() {
      return pendingJobs;
    }

    public Long getProcessingJobs() {
      return processingJobs;
    }

    public Instant getLastJobDate() {
      return lastJobDate;
    }

    public Long getJobsThisWeek() {
      return jobsThisWeek;
    }

    public Long getJobsThisMonth() {
      return jobsThisMonth;
    }

    public Long getJobsThisQuarter() {
      return jobsThisQuarter;
    }

    public Double getAvgProcessingTimeSeconds() {
      return avgProcessingTimeSeconds;
    }

    public Long getUniqueCompaniesApplied() {
      return uniqueCompaniesApplied;
    }

    // Computed properties
    public Double getSuccessRate() {
      if (totalJobs == 0) return 0.0;
      return (completedJobs.doubleValue() / totalJobs.doubleValue()) * 100.0;
    }
  }

  /** Job summary DTO for dashboard job list. */
  public static class JobSummaryDto {
    private final String id;
    private final String jobTitle;
    private final String companyName;
    private final String status;
    private final Instant createdAt;
    private final boolean hasArtifact;

    public JobSummaryDto(
        String id,
        String jobTitle,
        String companyName,
        String status,
        Instant createdAt,
        boolean hasArtifact) {
      this.id = id;
      this.jobTitle = jobTitle;
      this.companyName = companyName;
      this.status = status;
      this.createdAt = createdAt;
      this.hasArtifact = hasArtifact;
    }

    // Getters
    public String getId() {
      return id;
    }

    public String getJobTitle() {
      return jobTitle;
    }

    public String getCompanyName() {
      return companyName;
    }

    public String getStatus() {
      return status;
    }

    public Instant getCreatedAt() {
      return createdAt;
    }

    public boolean isHasArtifact() {
      return hasArtifact;
    }
  }

  /** Template DTO for dashboard template list. */
  public static class TemplateDto {
    private final String id;
    private final String name;
    private final String description;
    private final String category;
    private final Boolean isDefault;
    private final Integer usageCount;
    private final Instant createdAt;
    private final Instant updatedAt;

    public TemplateDto(
        String id,
        String name,
        String description,
        String category,
        Boolean isDefault,
        Integer usageCount,
        Instant createdAt,
        Instant updatedAt) {
      this.id = id;
      this.name = name;
      this.description = description;
      this.category = category;
      this.isDefault = isDefault;
      this.usageCount = usageCount;
      this.createdAt = createdAt;
      this.updatedAt = updatedAt;
    }

    // Getters
    public String getId() {
      return id;
    }

    public String getName() {
      return name;
    }

    public String getDescription() {
      return description;
    }

    public String getCategory() {
      return category;
    }

    public Boolean getIsDefault() {
      return isDefault;
    }

    public Integer getUsageCount() {
      return usageCount;
    }

    public Instant getCreatedAt() {
      return createdAt;
    }

    public Instant getUpdatedAt() {
      return updatedAt;
    }
  }
}
