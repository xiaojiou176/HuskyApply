package com.huskyapply.gateway.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.huskyapply.gateway.dto.DashboardResponse;
import com.huskyapply.gateway.model.Artifact;
import com.huskyapply.gateway.model.Job;
import com.huskyapply.gateway.model.Template;
import com.huskyapply.gateway.model.User;
import com.huskyapply.gateway.repository.JobRepository;
import com.huskyapply.gateway.repository.TemplateRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

  @Mock private JobRepository jobRepository;

  @Mock private TemplateRepository templateRepository;

  @Mock private CacheManager cacheManager;

  private DashboardService dashboardService;

  private User testUser;
  private Instant now;

  @BeforeEach
  void setUp() {
    dashboardService = new DashboardService(jobRepository, templateRepository, cacheManager);

    // Create test data
    testUser = new User("test@example.com", "password");
    testUser.setId(UUID.randomUUID());

    now = Instant.now();
  }

  @Test
  void getDashboardData_Success() {
    // Arrange
    mockJobRepositoryCounts();
    mockRecentJobs();
    mockUserTemplates();
    mockTemplateCategories();

    // Act
    DashboardResponse result = dashboardService.getDashboardData(testUser);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.getStats()).isNotNull();
    assertThat(result.getRecentJobs()).hasSize(2);
    assertThat(result.getTemplates()).hasSize(2);
    assertThat(result.getAvailableCategories()).containsExactly("technical", "business");

    // Verify stats
    DashboardResponse.UserStatsDto stats = result.getStats();
    assertThat(stats.getTotalJobs()).isEqualTo(10L);
    assertThat(stats.getCompletedJobs()).isEqualTo(7L);
    assertThat(stats.getFailedJobs()).isEqualTo(1L);
    assertThat(stats.getPendingJobs()).isEqualTo(1L);
    assertThat(stats.getProcessingJobs()).isEqualTo(1L);
    assertThat(stats.getJobsThisWeek()).isEqualTo(3L);
    assertThat(stats.getJobsThisMonth()).isEqualTo(8L);
  }

  @Test
  void getUserStats_WithJobs() {
    // Arrange
    mockJobRepositoryCounts();

    Job recentJob = createTestJob("Recent Job", "COMPLETED", now.minus(1, ChronoUnit.DAYS));
    when(jobRepository.findTop10ByUserIdOrderByCreatedAtDesc(testUser.getId()))
        .thenReturn(Collections.singletonList(recentJob));

    // Act
    DashboardResponse.UserStatsDto result = dashboardService.getUserStats(testUser);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.getTotalJobs()).isEqualTo(10L);
    assertThat(result.getCompletedJobs()).isEqualTo(7L);
    assertThat(result.getFailedJobs()).isEqualTo(1L);
    assertThat(result.getPendingJobs()).isEqualTo(1L);
    assertThat(result.getProcessingJobs()).isEqualTo(1L);
    assertThat(result.getJobsThisWeek()).isEqualTo(3L);
    assertThat(result.getJobsThisMonth()).isEqualTo(8L);
    assertThat(result.getLastJobDate()).isEqualTo(recentJob.getCreatedAt());

    // Verify repository calls with correct time ranges (3 calls: week, month,
    // quarter)
    verify(jobRepository, times(3))
        .countByUserIdAndCreatedAtAfter(eq(testUser.getId()), any(Instant.class));
  }

  @Test
  void getUserStats_NoJobs() {
    // Arrange
    when(jobRepository.countByUserId(testUser.getId())).thenReturn(0L);
    when(jobRepository.countByUserIdAndStatus(testUser.getId(), "COMPLETED")).thenReturn(0L);
    when(jobRepository.countByUserIdAndStatus(testUser.getId(), "FAILED")).thenReturn(0L);
    when(jobRepository.countByUserIdAndStatus(testUser.getId(), "PENDING")).thenReturn(0L);
    when(jobRepository.countByUserIdAndStatus(testUser.getId(), "PROCESSING")).thenReturn(0L);
    when(jobRepository.countByUserIdAndCreatedAtAfter(eq(testUser.getId()), any(Instant.class)))
        .thenReturn(0L);
    when(jobRepository.findTop10ByUserIdOrderByCreatedAtDesc(testUser.getId()))
        .thenReturn(Collections.emptyList());

    // Act
    DashboardResponse.UserStatsDto result = dashboardService.getUserStats(testUser);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.getTotalJobs()).isZero();
    assertThat(result.getCompletedJobs()).isZero();
    assertThat(result.getFailedJobs()).isZero();
    assertThat(result.getPendingJobs()).isZero();
    assertThat(result.getProcessingJobs()).isZero();
    assertThat(result.getJobsThisWeek()).isZero();
    assertThat(result.getJobsThisMonth()).isZero();
    assertThat(result.getLastJobDate()).isNull();
  }

  @Test
  void getRecentJobs_Success() {
    // Arrange
    Job job1 =
        createTestJobWithArtifacts(
            "Software Engineer", "COMPLETED", now.minus(1, ChronoUnit.HOURS));
    Job job2 = createTestJob("Product Manager", "PENDING", now.minus(2, ChronoUnit.HOURS));

    when(jobRepository.findTop10ByUserIdOrderByCreatedAtDesc(testUser.getId()))
        .thenReturn(Arrays.asList(job1, job2));

    // Act
    List<DashboardResponse.JobSummaryDto> result = dashboardService.getRecentJobs(testUser);

    // Assert
    assertThat(result).hasSize(2);

    DashboardResponse.JobSummaryDto firstJob = result.get(0);
    assertThat(firstJob.getId()).isEqualTo(job1.getId().toString());
    assertThat(firstJob.getJobTitle()).isEqualTo("Software Engineer");
    assertThat(firstJob.getCompanyName()).isEqualTo("TechCorp");
    assertThat(firstJob.getStatus()).isEqualTo("COMPLETED");
    assertThat(firstJob.isHasArtifact()).isTrue();
    assertThat(firstJob.getCreatedAt()).isEqualTo(job1.getCreatedAt());

    DashboardResponse.JobSummaryDto secondJob = result.get(1);
    assertThat(secondJob.getId()).isEqualTo(job2.getId().toString());
    assertThat(secondJob.getJobTitle()).isEqualTo("Product Manager");
    assertThat(secondJob.isHasArtifact()).isFalse();
  }

  @Test
  void getRecentJobs_EmptyList() {
    // Arrange
    when(jobRepository.findTop10ByUserIdOrderByCreatedAtDesc(testUser.getId()))
        .thenReturn(Collections.emptyList());

    // Act
    List<DashboardResponse.JobSummaryDto> result = dashboardService.getRecentJobs(testUser);

    // Assert
    assertThat(result).isEmpty();
  }

  @Test
  void getUserTemplates_Success() {
    // Arrange
    Template template1 = createTestTemplate("Cover Letter Template", "technical");
    Template template2 = createTestTemplate("Thank You Note", "business");

    when(templateRepository.findByUserOrderByUpdatedAtDesc(testUser))
        .thenReturn(Arrays.asList(template1, template2));

    // Act
    List<DashboardResponse.TemplateDto> result = dashboardService.getUserTemplates(testUser);

    // Assert
    assertThat(result).hasSize(2);

    DashboardResponse.TemplateDto firstTemplate = result.get(0);
    assertThat(firstTemplate.getId()).isEqualTo(template1.getId().toString());
    assertThat(firstTemplate.getName()).isEqualTo("Cover Letter Template");
    assertThat(firstTemplate.getCategory()).isEqualTo("technical");
    assertThat(firstTemplate.getIsDefault()).isTrue();
    assertThat(firstTemplate.getUsageCount()).isEqualTo(5);

    DashboardResponse.TemplateDto secondTemplate = result.get(1);
    assertThat(secondTemplate.getName()).isEqualTo("Thank You Note");
    assertThat(secondTemplate.getCategory()).isEqualTo("business");
  }

  @Test
  void searchJobs_Success() {
    // Arrange
    String searchTerm = "engineer";
    Job job1 = createTestJob("Software Engineer", "COMPLETED", now);
    Job job2 = createTestJob("DevOps Engineer", "PENDING", now.minus(1, ChronoUnit.DAYS));

    when(jobRepository.searchByUserAndTerm(testUser.getId(), searchTerm))
        .thenReturn(Arrays.asList(job1, job2));

    // Act
    List<DashboardResponse.JobSummaryDto> result =
        dashboardService.searchJobs(testUser, searchTerm);

    // Assert
    assertThat(result).hasSize(2);
    assertThat(result.get(0).getJobTitle()).isEqualTo("Software Engineer");
    assertThat(result.get(1).getJobTitle()).isEqualTo("DevOps Engineer");

    verify(jobRepository).searchByUserAndTerm(testUser.getId(), searchTerm);
  }

  @Test
  void getJobsByStatus_Success() {
    // Arrange
    String status = "COMPLETED";
    Job job1 = createTestJob("Job 1", status, now);
    Job job2 = createTestJob("Job 2", status, now.minus(1, ChronoUnit.DAYS));

    when(jobRepository.findByUserIdAndStatusOrderByCreatedAtDesc(testUser.getId(), status))
        .thenReturn(Arrays.asList(job1, job2));

    // Act
    List<DashboardResponse.JobSummaryDto> result =
        dashboardService.getJobsByStatus(testUser, status);

    // Assert
    assertThat(result).hasSize(2);
    assertThat(result.get(0).getStatus()).isEqualTo(status);
    assertThat(result.get(1).getStatus()).isEqualTo(status);

    verify(jobRepository).findByUserIdAndStatusOrderByCreatedAtDesc(testUser.getId(), status);
  }

  @Test
  void getTemplatesByCategory_Success() {
    // Arrange
    String category = "technical";
    Template template1 = createTestTemplate("Template 1", category);
    Template template2 = createTestTemplate("Template 2", category);

    when(templateRepository.findByUserAndCategoryOrderByUpdatedAtDesc(testUser, category))
        .thenReturn(Arrays.asList(template1, template2));

    // Act
    List<DashboardResponse.TemplateDto> result =
        dashboardService.getTemplatesByCategory(testUser, category);

    // Assert
    assertThat(result).hasSize(2);
    assertThat(result.get(0).getCategory()).isEqualTo(category);
    assertThat(result.get(1).getCategory()).isEqualTo(category);

    verify(templateRepository).findByUserAndCategoryOrderByUpdatedAtDesc(testUser, category);
  }

  @Test
  void getUserStats_VerifyTimeRangeCalculations() {
    // Arrange
    mockJobRepositoryCounts();
    when(jobRepository.findTop10ByUserIdOrderByCreatedAtDesc(testUser.getId()))
        .thenReturn(Collections.emptyList());

    // Act
    dashboardService.getUserStats(testUser);

    // Assert - Verify the time range calculations are approximately correct (3
    // calls: week, month,
    // quarter)
    verify(jobRepository, times(3))
        .countByUserIdAndCreatedAtAfter(eq(testUser.getId()), any(Instant.class));
  }

  // Helper methods

  private void mockJobRepositoryCounts() {
    when(jobRepository.countByUserId(testUser.getId())).thenReturn(10L);
    when(jobRepository.countByUserIdAndStatus(testUser.getId(), "COMPLETED")).thenReturn(7L);
    when(jobRepository.countByUserIdAndStatus(testUser.getId(), "FAILED")).thenReturn(1L);
    when(jobRepository.countByUserIdAndStatus(testUser.getId(), "PENDING")).thenReturn(1L);
    when(jobRepository.countByUserIdAndStatus(testUser.getId(), "PROCESSING")).thenReturn(1L);
    when(jobRepository.countByUserIdAndCreatedAtAfter(eq(testUser.getId()), any(Instant.class)))
        .thenReturn(3L) // This week
        .thenReturn(8L); // This month
  }

  private void mockRecentJobs() {
    Job job1 = createTestJob("Software Engineer", "COMPLETED", now.minus(1, ChronoUnit.HOURS));
    Job job2 = createTestJob("Product Manager", "PENDING", now.minus(2, ChronoUnit.HOURS));

    when(jobRepository.findTop10ByUserIdOrderByCreatedAtDesc(testUser.getId()))
        .thenReturn(Arrays.asList(job1, job2));
  }

  private void mockUserTemplates() {
    Template template1 = createTestTemplate("Cover Letter Template", "technical");
    Template template2 = createTestTemplate("Thank You Note", "business");

    when(templateRepository.findByUserOrderByUpdatedAtDesc(testUser))
        .thenReturn(Arrays.asList(template1, template2));
  }

  private void mockTemplateCategories() {
    when(templateRepository.findDistinctCategoriesByUser(testUser))
        .thenReturn(Arrays.asList("technical", "business"));
  }

  private Job createTestJob(String title, String status, Instant createdAt) {
    Job job = new Job();
    job.setId(UUID.randomUUID());
    job.setJobTitle(title);
    job.setCompanyName("TechCorp");
    job.setStatus(status);
    job.setCreatedAt(createdAt);
    job.setUser(testUser);
    job.setArtifacts(Collections.emptyList());
    return job;
  }

  private Job createTestJobWithArtifacts(String title, String status, Instant createdAt) {
    Job job = createTestJob(title, status, createdAt);

    Artifact artifact = new Artifact();
    artifact.setId(UUID.randomUUID());
    artifact.setJob(job);
    artifact.setContentType("cover_letter");
    artifact.setGeneratedText("Test content");

    job.setArtifacts(Collections.singletonList(artifact));
    return job;
  }

  private Template createTestTemplate(String name, String category) {
    Template template = new Template();
    template.setId(UUID.randomUUID());
    template.setName(name);
    template.setDescription("Test template description");
    template.setCategory(category);
    template.setIsDefault(true);
    template.setUsageCount(5);
    template.setUser(testUser);
    template.setCreatedAt(now.minus(1, ChronoUnit.DAYS));
    template.setUpdatedAt(now);
    return template;
  }
}
