package com.huskyapply.gateway.exception;

import java.util.UUID;

/** Exception thrown when an artifact cannot be found for a given job ID. */
public class ArtifactNotFoundException extends RuntimeException {

  private final UUID jobId;

  public ArtifactNotFoundException(UUID jobId) {
    super(String.format("No artifact found for job ID: %s", jobId));
    this.jobId = jobId;
  }

  public ArtifactNotFoundException(UUID jobId, String message) {
    super(message);
    this.jobId = jobId;
  }

  public UUID getJobId() {
    return jobId;
  }
}
