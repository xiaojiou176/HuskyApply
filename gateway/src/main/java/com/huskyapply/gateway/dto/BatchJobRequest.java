package com.huskyapply.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public class BatchJobRequest {

  @NotBlank(message = "Batch job name is required")
  @Size(max = 100, message = "Batch job name must not exceed 100 characters")
  private String name;

  @Size(max = 500, message = "Description must not exceed 500 characters")
  private String description;

  @NotEmpty(message = "At least one job URL is required")
  private List<String> jobUrls;

  @NotBlank(message = "Resume URI is required")
  private String resumeUri;

  private UUID templateId;

  private String modelProvider = "openai";

  private String modelName;

  private Boolean autoStart = false;

  public BatchJobRequest() {}

  public BatchJobRequest(
      String name,
      String description,
      List<String> jobUrls,
      String resumeUri,
      UUID templateId,
      Boolean autoStart) {
    this.name = name;
    this.description = description;
    this.jobUrls = jobUrls;
    this.resumeUri = resumeUri;
    this.templateId = templateId;
    this.autoStart = autoStart;
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

  public List<String> getJobUrls() {
    return jobUrls;
  }

  public void setJobUrls(List<String> jobUrls) {
    this.jobUrls = jobUrls;
  }

  public String getResumeUri() {
    return resumeUri;
  }

  public void setResumeUri(String resumeUri) {
    this.resumeUri = resumeUri;
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

  public Boolean getAutoStart() {
    return autoStart;
  }

  public void setAutoStart(Boolean autoStart) {
    this.autoStart = autoStart;
  }
}
