package com.huskyapply.gateway.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "batch_jobs")
public class BatchJob {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private UUID id;

  @ManyToOne
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Column(name = "description", length = 500)
  private String description;

  @Column(name = "status", nullable = false, length = 20)
  private String status = "PENDING";

  @Column(name = "total_jobs")
  private Integer totalJobs = 0;

  @Column(name = "completed_jobs")
  private Integer completedJobs = 0;

  @Column(name = "failed_jobs")
  private Integer failedJobs = 0;

  @OneToMany(mappedBy = "batchJob", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
  private List<Job> jobs = new ArrayList<>();

  @Column(name = "template_id")
  private UUID templateId;

  @Column(name = "model_provider", length = 50)
  private String modelProvider = "openai";

  @Column(name = "model_name", length = 100)
  private String modelName;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  public BatchJob() {}

  public BatchJob(User user, String name, String description, UUID templateId) {
    this.user = user;
    this.name = name;
    this.description = description;
    this.templateId = templateId;
    Instant now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Integer getTotalJobs() {
    return totalJobs;
  }

  public void setTotalJobs(Integer totalJobs) {
    this.totalJobs = totalJobs;
  }

  public Integer getCompletedJobs() {
    return completedJobs;
  }

  public void setCompletedJobs(Integer completedJobs) {
    this.completedJobs = completedJobs;
  }

  public Integer getFailedJobs() {
    return failedJobs;
  }

  public void setFailedJobs(Integer failedJobs) {
    this.failedJobs = failedJobs;
  }

  public List<Job> getJobs() {
    return jobs;
  }

  public void setJobs(List<Job> jobs) {
    this.jobs = jobs;
  }

  public UUID getTemplateId() {
    return templateId;
  }

  public void setTemplateId(UUID templateId) {
    this.templateId = templateId;
  }

  public String getModelProvider() {
    return modelProvider;
  }

  public void setModelProvider(String modelProvider) {
    this.modelProvider = modelProvider;
  }

  public String getModelName() {
    return modelName;
  }

  public void setModelName(String modelName) {
    this.modelName = modelName;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(Instant startedAt) {
    this.startedAt = startedAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(Instant completedAt) {
    this.completedAt = completedAt;
  }

  public double getProgressPercentage() {
    if (totalJobs == 0) return 0.0;
    return ((double) (completedJobs + failedJobs) / totalJobs) * 100.0;
  }

  public boolean isCompleted() {
    return "COMPLETED".equals(status) || "FAILED".equals(status);
  }
}
