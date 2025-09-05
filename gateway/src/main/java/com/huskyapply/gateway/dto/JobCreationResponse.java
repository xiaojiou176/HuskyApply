package com.huskyapply.gateway.dto;

import java.util.UUID;

public class JobCreationResponse {
  private UUID jobId;

  public JobCreationResponse() {}

  public JobCreationResponse(UUID jobId) {
    this.jobId = jobId;
  }

  public UUID getJobId() {
    return jobId;
  }

  public void setJobId(UUID jobId) {
    this.jobId = jobId;
  }
}
