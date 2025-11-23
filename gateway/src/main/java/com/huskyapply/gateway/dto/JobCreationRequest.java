package com.huskyapply.gateway.dto;

public class JobCreationRequest {
  private String jdUrl;
  private String resumeUri;
  private String modelProvider = "openai";
  private String modelName;
  private Boolean urgent;

  public JobCreationRequest() {}

  public String getJdUrl() {
    return jdUrl;
  }

  public void setJdUrl(String jdUrl) {
    this.jdUrl = jdUrl;
  }

  public String getResumeUri() {
    return resumeUri;
  }

  public void setResumeUri(String resumeUri) {
    this.resumeUri = resumeUri;
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

  public Boolean getUrgent() {
    return urgent;
  }

  public void setUrgent(Boolean urgent) {
    this.urgent = urgent;
  }
}
