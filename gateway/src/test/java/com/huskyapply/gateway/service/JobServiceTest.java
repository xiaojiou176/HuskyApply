package com.huskyapply.gateway.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.huskyapply.gateway.dto.ArtifactResponse;
import com.huskyapply.gateway.dto.JobCreationRequest;
import com.huskyapply.gateway.dto.StatusUpdateEvent;
import com.huskyapply.gateway.exception.ArtifactNotFoundException;
import com.huskyapply.gateway.model.Artifact;
import com.huskyapply.gateway.model.Job;
import com.huskyapply.gateway.model.User;
import com.huskyapply.gateway.repository.ArtifactRepository;
import com.huskyapply.gateway.repository.JobRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

  @Mock private JobRepository jobRepository;

  @Mock private ArtifactRepository artifactRepository;

  @Mock private RabbitTemplate rabbitTemplate;

  @Mock private SubscriptionService subscriptionService;

  private JobService jobService;

  private User testUser;
  private JobCreationRequest testJobRequest;
  private Job testJob;

  @BeforeEach
  void setUp() {
    jobService =
        new JobService(jobRepository, artifactRepository, rabbitTemplate, subscriptionService);

    // Set up test configuration values
    ReflectionTestUtils.setField(jobService, "exchangeName", "jobs.exchange");
    ReflectionTestUtils.setField(jobService, "routingKey", "jobs.queue");

    // Create test data
    testUser = new User();
    testUser.setId(UUID.randomUUID());
    testUser.setEmail("test@example.com");

    testJobRequest = new JobCreationRequest();
    testJobRequest.setJdUrl("https://example.com/job");
    testJobRequest.setResumeUri("s3://bucket/resume.pdf");
    testJobRequest.setModelProvider("openai");
    testJobRequest.setModelName("gpt-4o");

    testJob =
        Job.builder()
            .id(UUID.randomUUID())
            .jdUrl(testJobRequest.getJdUrl())
            .resumeUri(testJobRequest.getResumeUri())
            .status("PENDING")
            .user(testUser)
            .build();
  }

  @Test
  void createJob_Success() {
    // Arrange
    when(jobRepository.save(any(Job.class))).thenReturn(testJob);
    doNothing().when(subscriptionService).validateJobCreationLimits(testUser);
    when(subscriptionService.hasModelAccess(testUser, "openai", "gpt-4o")).thenReturn(true);

    // Act
    Job result = jobService.createJob(testJobRequest, testUser, "trace-123");

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo(testJob.getId());
    assertThat(result.getJdUrl()).isEqualTo(testJobRequest.getJdUrl());
    assertThat(result.getResumeUri()).isEqualTo(testJobRequest.getResumeUri());
    assertThat(result.getStatus()).isEqualTo("PENDING");
    assertThat(result.getUser()).isEqualTo(testUser);

    // Verify subscription validation
    verify(subscriptionService).validateJobCreationLimits(testUser);
    verify(subscriptionService).hasModelAccess(testUser, "openai", "gpt-4o");

    // Verify job is saved
    verify(jobRepository).save(any(Job.class));

    // Verify message is sent to RabbitMQ
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> messageCaptor = ArgumentCaptor.forClass(Map.class);
    verify(rabbitTemplate)
        .convertAndSend(
            eq("jobs.exchange"),
            eq("jobs.queue"),
            messageCaptor.capture(),
            any(org.springframework.amqp.core.MessagePostProcessor.class));

    Map<String, Object> sentMessage = messageCaptor.getValue();
    assertThat(sentMessage).containsEntry("jobId", testJob.getId());
    assertThat(sentMessage).containsEntry("jdUrl", testJob.getJdUrl());
    assertThat(sentMessage).containsEntry("resumeUri", testJob.getResumeUri());
    assertThat(sentMessage).containsEntry("modelProvider", "openai");
    assertThat(sentMessage).containsEntry("modelName", "gpt-4o");
  }

  @Test
  void createJob_SubscriptionLimitExceeded() {
    // Arrange
    doThrow(new IllegalArgumentException("Job limit exceeded"))
        .when(subscriptionService)
        .validateJobCreationLimits(testUser);

    // Act & Assert
    assertThatThrownBy(() -> jobService.createJob(testJobRequest, testUser, "trace-123"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Job limit exceeded");

    // Verify no job is saved and no message sent
    verify(jobRepository, never()).save(any(Job.class));
    verify(rabbitTemplate, never())
        .convertAndSend(
            any(String.class),
            any(String.class),
            any(Object.class),
            any(org.springframework.amqp.core.MessagePostProcessor.class));
  }

  @Test
  void createJob_NoModelAccess() {
    // Arrange
    doNothing().when(subscriptionService).validateJobCreationLimits(testUser);
    when(subscriptionService.hasModelAccess(testUser, "openai", "gpt-4o")).thenReturn(false);

    // Act & Assert
    assertThatThrownBy(() -> jobService.createJob(testJobRequest, testUser, "trace-123"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Your subscription plan does not include access to the selected AI model");

    // Verify no job is saved and no message sent
    verify(jobRepository, never()).save(any(Job.class));
    verify(rabbitTemplate, never())
        .convertAndSend(
            any(String.class),
            any(String.class),
            any(Object.class),
            any(org.springframework.amqp.core.MessagePostProcessor.class));
  }

  @Test
  void createJob_WithDefaultModelProvider() {
    // Arrange
    JobCreationRequest requestWithoutProvider = new JobCreationRequest();
    requestWithoutProvider.setJdUrl("https://example.com/job");
    requestWithoutProvider.setResumeUri("s3://bucket/resume.pdf");
    requestWithoutProvider.setModelName("gpt-4o");

    when(jobRepository.save(any(Job.class))).thenReturn(testJob);
    doNothing().when(subscriptionService).validateJobCreationLimits(testUser);
    when(subscriptionService.hasModelAccess(testUser, "openai", "gpt-4o")).thenReturn(true);

    // Act
    Job result = jobService.createJob(requestWithoutProvider, testUser, "trace-123");

    // Assert
    assertThat(result).isNotNull();

    // Verify default model provider is used
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> messageCaptor = ArgumentCaptor.forClass(Map.class);
    verify(rabbitTemplate)
        .convertAndSend(
            eq("jobs.exchange"),
            eq("jobs.queue"),
            messageCaptor.capture(),
            any(org.springframework.amqp.rabbit.connection.CorrelationData.class));

    Map<String, Object> sentMessage = messageCaptor.getValue();
    assertThat(sentMessage).containsEntry("modelProvider", "openai");
  }

  @Test
  void handleStatusUpdate_CompletedWithContent() {
    // Arrange
    UUID jobId = testJob.getId();
    StatusUpdateEvent event = new StatusUpdateEvent();
    event.setStatus("COMPLETED");
    event.setContent("This is a generated cover letter with multiple words for testing.");

    when(jobRepository.findById(jobId)).thenReturn(Optional.of(testJob));
    when(jobRepository.save(any(Job.class))).thenReturn(testJob);
    when(artifactRepository.save(any(Artifact.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // Act
    jobService.handleStatusUpdate(jobId, event);

    // Assert
    verify(jobRepository).save(testJob);
    assertThat(testJob.getStatus()).isEqualTo("COMPLETED");

    // Verify artifact is created
    ArgumentCaptor<Artifact> artifactCaptor = ArgumentCaptor.forClass(Artifact.class);
    verify(artifactRepository).save(artifactCaptor.capture());

    Artifact savedArtifact = artifactCaptor.getValue();
    assertThat(savedArtifact.getJob()).isEqualTo(testJob);
    assertThat(savedArtifact.getContentType()).isEqualTo("cover_letter");
    assertThat(savedArtifact.getGeneratedText()).isEqualTo(event.getContent());
    assertThat(savedArtifact.getWordCount()).isEqualTo(11);
  }

  @Test
  void handleStatusUpdate_CompletedWithoutContent() {
    // Arrange
    UUID jobId = testJob.getId();
    StatusUpdateEvent event = new StatusUpdateEvent();
    event.setStatus("COMPLETED");
    event.setContent("");

    when(jobRepository.findById(jobId)).thenReturn(Optional.of(testJob));
    when(jobRepository.save(any(Job.class))).thenReturn(testJob);

    // Act
    jobService.handleStatusUpdate(jobId, event);

    // Assert
    verify(jobRepository).save(testJob);
    assertThat(testJob.getStatus()).isEqualTo("COMPLETED");

    // Verify no artifact is created for empty content
    verify(artifactRepository, never()).save(any(Artifact.class));
  }

  @Test
  void handleStatusUpdate_ProcessingStatus() {
    // Arrange
    UUID jobId = testJob.getId();
    StatusUpdateEvent event = new StatusUpdateEvent();
    event.setStatus("PROCESSING");

    when(jobRepository.findById(jobId)).thenReturn(Optional.of(testJob));
    when(jobRepository.save(any(Job.class))).thenReturn(testJob);

    // Act
    jobService.handleStatusUpdate(jobId, event);

    // Assert
    verify(jobRepository).save(testJob);
    assertThat(testJob.getStatus()).isEqualTo("PROCESSING");

    // Verify no artifact is created for processing status
    verify(artifactRepository, never()).save(any(Artifact.class));
  }

  @Test
  void handleStatusUpdate_JobNotFound() {
    // Arrange
    UUID jobId = UUID.randomUUID();
    StatusUpdateEvent event = new StatusUpdateEvent();
    event.setStatus("COMPLETED");

    when(jobRepository.findById(jobId)).thenReturn(Optional.empty());

    // Act
    jobService.handleStatusUpdate(jobId, event);

    // Assert
    verify(jobRepository, never()).save(any(Job.class));
    verify(artifactRepository, never()).save(any(Artifact.class));
  }

  @Test
  void getArtifactForJob_Success() {
    // Arrange
    UUID jobId = testJob.getId();
    Instant now = Instant.now();

    Artifact artifact =
        Artifact.builder()
            .job(testJob)
            .contentType("cover_letter")
            .generatedText("Generated cover letter content")
            .wordCount(4)
            .createdAt(now)
            .build();

    when(artifactRepository.findByJobId(jobId)).thenReturn(Optional.of(artifact));

    // Act
    ArtifactResponse result = jobService.getArtifactForJob(jobId);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.jobId()).isEqualTo(jobId);
    assertThat(result.contentType()).isEqualTo("cover_letter");
    assertThat(result.generatedText()).isEqualTo("Generated cover letter content");
    assertThat(result.createdAt()).isEqualTo(now);
  }

  @Test
  void getArtifactForJob_NotFound() {
    // Arrange
    UUID jobId = UUID.randomUUID();
    when(artifactRepository.findByJobId(jobId)).thenReturn(Optional.empty());

    // Act & Assert
    assertThatThrownBy(() -> jobService.getArtifactForJob(jobId))
        .isInstanceOf(ArtifactNotFoundException.class);
  }

  @Test
  void processJob_DefaultModelProvider() {
    // Arrange
    UUID jobId = testJob.getId();
    when(jobRepository.findById(jobId)).thenReturn(Optional.of(testJob));
    when(jobRepository.save(any(Job.class))).thenReturn(testJob);

    // Act
    jobService.processJob(jobId);

    // Assert
    assertThat(testJob.getStatus()).isEqualTo("PROCESSING");
    verify(jobRepository).save(testJob);

    // Verify message is sent to RabbitMQ
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> messageCaptor = ArgumentCaptor.forClass(Map.class);
    verify(rabbitTemplate)
        .convertAndSend(
            eq("jobs.exchange"),
            eq("jobs.queue"),
            messageCaptor.capture(),
            any(org.springframework.amqp.core.MessagePostProcessor.class));

    Map<String, Object> sentMessage = messageCaptor.getValue();
    assertThat(sentMessage).containsEntry("jobId", jobId);
    assertThat(sentMessage).containsEntry("modelProvider", "openai");
  }

  @Test
  void processJob_WithCustomModel() {
    // Arrange
    UUID jobId = testJob.getId();
    when(jobRepository.findById(jobId)).thenReturn(Optional.of(testJob));
    when(jobRepository.save(any(Job.class))).thenReturn(testJob);

    // Act
    jobService.processJob(jobId, "anthropic", "claude-3-sonnet");

    // Assert
    assertThat(testJob.getStatus()).isEqualTo("PROCESSING");
    verify(jobRepository).save(testJob);

    // Verify message with custom model
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> messageCaptor = ArgumentCaptor.forClass(Map.class);
    verify(rabbitTemplate)
        .convertAndSend(
            eq("jobs.exchange"),
            eq("jobs.queue"),
            messageCaptor.capture(),
            any(org.springframework.amqp.rabbit.connection.CorrelationData.class));

    Map<String, Object> sentMessage = messageCaptor.getValue();
    assertThat(sentMessage).containsEntry("modelProvider", "anthropic");
    assertThat(sentMessage).containsEntry("modelName", "claude-3-sonnet");
  }

  @Test
  void processJob_JobNotFound() {
    // Arrange
    UUID jobId = UUID.randomUUID();
    when(jobRepository.findById(jobId)).thenReturn(Optional.empty());

    // Act & Assert
    assertThatThrownBy(() -> jobService.processJob(jobId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Job not found: " + jobId);

    verify(jobRepository, never()).save(any(Job.class));
    verify(rabbitTemplate, never())
        .convertAndSend(
            any(String.class),
            any(String.class),
            any(Object.class),
            any(org.springframework.amqp.core.MessagePostProcessor.class));
  }

  @Test
  void processJob_NotInPendingStatus() {
    // Arrange
    UUID jobId = testJob.getId();
    testJob.setStatus("PROCESSING");
    when(jobRepository.findById(jobId)).thenReturn(Optional.of(testJob));

    // Act & Assert
    assertThatThrownBy(() -> jobService.processJob(jobId))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Job is not in PENDING status: PROCESSING");

    verify(jobRepository, never()).save(any(Job.class));
    verify(rabbitTemplate, never())
        .convertAndSend(
            any(String.class),
            any(String.class),
            any(Object.class),
            any(org.springframework.amqp.core.MessagePostProcessor.class));
  }

  @Test
  void handleStatusUpdate_WordCountCalculation() {
    // Arrange
    UUID jobId = testJob.getId();
    StatusUpdateEvent event = new StatusUpdateEvent();
    event.setStatus("COMPLETED");
    event.setContent("   Word1    Word2   Word3   "); // Test trimming and multiple spaces

    when(jobRepository.findById(jobId)).thenReturn(Optional.of(testJob));
    when(jobRepository.save(any(Job.class))).thenReturn(testJob);
    when(artifactRepository.save(any(Artifact.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // Act
    jobService.handleStatusUpdate(jobId, event);

    // Assert
    ArgumentCaptor<Artifact> artifactCaptor = ArgumentCaptor.forClass(Artifact.class);
    verify(artifactRepository).save(artifactCaptor.capture());

    Artifact savedArtifact = artifactCaptor.getValue();
    assertThat(savedArtifact.getWordCount()).isEqualTo(3);
  }
}
