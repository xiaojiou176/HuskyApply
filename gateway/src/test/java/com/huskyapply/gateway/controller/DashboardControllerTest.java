package com.huskyapply.gateway.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.huskyapply.gateway.dto.DashboardResponse;
import com.huskyapply.gateway.model.User;
import com.huskyapply.gateway.service.DashboardService;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
public class DashboardControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private DashboardService dashboardService;

  private DashboardResponse.UserStatsDto mockStats;
  private List<DashboardResponse.JobSummaryDto> mockJobs;
  private List<DashboardResponse.TemplateDto> mockTemplates;
  private DashboardResponse mockDashboardResponse;

  @BeforeEach
  public void setUp() {
    // Mock user statistics
    mockStats = new DashboardResponse.UserStatsDto(10L, 8L, 1L, 1L, 0L, Instant.now(), 3L, 7L);

    // Mock job summaries
    mockJobs =
        Arrays.asList(
            new DashboardResponse.JobSummaryDto(
                UUID.randomUUID().toString(),
                "Software Engineer",
                "Tech Corp",
                "COMPLETED",
                Instant.now(),
                true),
            new DashboardResponse.JobSummaryDto(
                UUID.randomUUID().toString(),
                "Full Stack Developer",
                "StartupCo",
                "PROCESSING",
                Instant.now(),
                false));

    // Mock templates
    mockTemplates =
        Arrays.asList(
            new DashboardResponse.TemplateDto(
                UUID.randomUUID().toString(),
                "Standard Cover Letter",
                "A professional cover letter template",
                "General",
                false,
                5,
                Instant.now(),
                Instant.now()));

    // Mock complete dashboard response
    mockDashboardResponse =
        new DashboardResponse(
            mockStats,
            mockJobs,
            mockTemplates,
            Arrays.asList("General", "Engineering", "Marketing"));
  }

  @Test
  @WithMockUser
  public void getDashboard_ReturnsCompleteDashboard() throws Exception {
    // Arrange
    when(dashboardService.getDashboardData(any(User.class))).thenReturn(mockDashboardResponse);

    // Act & Assert
    mockMvc
        .perform(get("/api/v1/dashboard").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stats.totalJobs").value(10))
        .andExpect(jsonPath("$.stats.completedJobs").value(8))
        .andExpect(jsonPath("$.recentJobs").isArray())
        .andExpect(jsonPath("$.templates").isArray())
        .andExpect(jsonPath("$.availableCategories").isArray());
  }

  @Test
  @WithMockUser
  public void getUserStats_ReturnsUserStats() throws Exception {
    // Arrange
    when(dashboardService.getUserStats(any(User.class))).thenReturn(mockStats);

    // Act & Assert
    mockMvc
        .perform(get("/api/v1/dashboard/stats").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalJobs").value(10))
        .andExpect(jsonPath("$.completedJobs").value(8))
        .andExpect(jsonPath("$.failedJobs").value(1))
        .andExpect(jsonPath("$.pendingJobs").value(1))
        .andExpect(jsonPath("$.processingJobs").value(0));
  }

  @Test
  @WithMockUser
  public void getRecentJobs_ReturnsRecentJobs() throws Exception {
    // Arrange
    when(dashboardService.getRecentJobs(any(User.class))).thenReturn(mockJobs);

    // Act & Assert
    mockMvc
        .perform(get("/api/v1/dashboard/jobs/recent").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].jobTitle").value("Software Engineer"))
        .andExpect(jsonPath("$[0].companyName").value("Tech Corp"))
        .andExpect(jsonPath("$[0].status").value("COMPLETED"))
        .andExpect(jsonPath("$[0].hasArtifact").value(true));
  }

  @Test
  @WithMockUser
  public void getJobsByStatus_ReturnsJobsWithStatus() throws Exception {
    // Arrange
    List<DashboardResponse.JobSummaryDto> completedJobs = Arrays.asList(mockJobs.get(0));
    when(dashboardService.getJobsByStatus(any(User.class), eq("COMPLETED")))
        .thenReturn(completedJobs);

    // Act & Assert
    mockMvc
        .perform(
            get("/api/v1/dashboard/jobs/status/COMPLETED").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].status").value("COMPLETED"));
  }

  @Test
  @WithMockUser
  public void searchJobs_WithValidTerm_ReturnsMatchingJobs() throws Exception {
    // Arrange
    when(dashboardService.searchJobs(any(User.class), eq("Tech")))
        .thenReturn(Arrays.asList(mockJobs.get(0)));

    // Act & Assert
    mockMvc
        .perform(
            get("/api/v1/dashboard/jobs/search")
                .param("searchTerm", "Tech")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].companyName").value("Tech Corp"));
  }

  @Test
  @WithMockUser
  public void searchJobs_WithEmptyTerm_ReturnsBadRequest() throws Exception {
    // Act & Assert
    mockMvc
        .perform(
            get("/api/v1/dashboard/jobs/search")
                .param("searchTerm", "")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser
  public void getTemplates_ReturnsUserTemplates() throws Exception {
    // Arrange
    when(dashboardService.getUserTemplates(any(User.class))).thenReturn(mockTemplates);

    // Act & Assert
    mockMvc
        .perform(get("/api/v1/dashboard/templates").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].name").value("Standard Cover Letter"))
        .andExpect(jsonPath("$[0].category").value("General"));
  }

  @Test
  @WithMockUser
  public void getTemplatesByCategory_ReturnsTemplatesInCategory() throws Exception {
    // Arrange
    when(dashboardService.getTemplatesByCategory(any(User.class), eq("General")))
        .thenReturn(mockTemplates);

    // Act & Assert
    mockMvc
        .perform(
            get("/api/v1/dashboard/templates/category/General")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].category").value("General"));
  }

  @Test
  public void getDashboard_WithoutAuthentication_ReturnsUnauthorized() throws Exception {
    // Act & Assert
    mockMvc
        .perform(get("/api/v1/dashboard").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
  }

  @Test
  public void getUserStats_WithoutAuthentication_ReturnsUnauthorized() throws Exception {
    // Act & Assert
    mockMvc
        .perform(get("/api/v1/dashboard/stats").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
  }
}
