package com.huskyapply.gateway.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for artifact data retrieval. Provides a clean API contract for the frontend to
 * access generated content.
 */
public record ArtifactResponse(
    UUID jobId, String contentType, String generatedText, Instant createdAt) {}
