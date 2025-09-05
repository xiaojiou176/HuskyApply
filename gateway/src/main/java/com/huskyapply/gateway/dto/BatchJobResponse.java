package com.huskyapply.gateway.dto;

import java.time.Instant;

public class BatchJobResponse {

  private String id;
  private String name;
  private String description;
  private String status;
  private Integer totalJobs;
  private Integer completedJobs;
  private Integer failedJobs;
  private Double progressPercentage;
  private String templateId;
  private String modelProvider;
  private String modelName;
  private Instant createdAt;
  private Instant updatedAt;
  private Instant startedAt;
  private Instant completedAt;

  public BatchJobResponse() {}

  public BatchJobResponse(
      String id,
      String name,
      String description,
      String status,
      Integer totalJobs,
      Integer completedJobs,
      Integer failedJobs,
      Double progressPercentage,
      String templateId,
      String modelProvider,
      String modelName,
      Instant createdAt,
      Instant updatedAt,
      Instant startedAt,
      Instant completedAt) {
    this.id = id;
    this.name = name;
    this.description = description;
    this.status = status;
    this.totalJobs = totalJobs;
    this.completedJobs = completedJobs;
    this.failedJobs = failedJobs;
    this.progressPercentage = progressPercentage;
    this.templateId = templateId;
    this.modelProvider = modelProvider;
    this.modelName = modelName;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.startedAt = startedAt;
    this.completedAt = completedAt;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
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

  public Double getProgressPercentage() {
    return progressPercentage;
  }

  public void setProgressPercentage(Double progressPercentage) {
    this.progressPercentage = progressPercentage;
  }

  public String getTemplateId() {
    return templateId;
  }

  public void setTemplateId(String templateId) {
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

  public Integer getPendingJobs() {
    return totalJobs - completedJobs - failedJobs;
  }

  public boolean isCompleted() {
    return "COMPLETED".equals(status) || "FAILED".equals(status) || "CANCELLED".equals(status);
  }
}
