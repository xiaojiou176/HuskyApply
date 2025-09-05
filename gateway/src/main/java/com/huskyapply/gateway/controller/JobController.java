package com.huskyapply.gateway.controller;

import com.huskyapply.gateway.dto.ArtifactResponse;
import com.huskyapply.gateway.dto.JobCreationRequest;
import com.huskyapply.gateway.dto.JobCreationResponse;
import com.huskyapply.gateway.dto.StatusUpdateEvent;
import com.huskyapply.gateway.exception.ArtifactNotFoundException;
import com.huskyapply.gateway.model.Job;
import com.huskyapply.gateway.model.User;
import com.huskyapply.gateway.service.JobService;
import com.huskyapply.gateway.service.JwtService;
import com.huskyapply.gateway.service.SseManager;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class JobController {

  private final JobService jobService;
  private final SseManager sseManager;
  private final JwtService jwtService;
  private final UserDetailsService userDetailsService;

  public JobController(
      JobService jobService,
      SseManager sseManager,
      JwtService jwtService,
      UserDetailsService userDetailsService) {
    this.jobService = jobService;
    this.sseManager = sseManager;
    this.jwtService = jwtService;
    this.userDetailsService = userDetailsService;
  }

  @PostMapping("/api/v1/applications")
  public ResponseEntity<JobCreationResponse> createJob(
      @AuthenticationPrincipal User user, @RequestBody JobCreationRequest request) {
    String traceId = java.util.UUID.randomUUID().toString();
    MDC.put("traceId", traceId);

    try {
      Job createdJob = jobService.createJob(request, user, traceId);
      JobCreationResponse response = new JobCreationResponse(createdJob.getId());
      return ResponseEntity.accepted().body(response);
    } finally {
      MDC.clear();
    }
  }

  @GetMapping(
      value = "/api/v1/applications/{jobId}/stream",
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter streamJobStatus(
      @PathVariable UUID jobId, @RequestParam(name = "token") String token) {
    try {
      // Validate JWT token
      String username = jwtService.extractUsername(token);
      UserDetails userDetails = userDetailsService.loadUserByUsername(username);

      if (!jwtService.isTokenValid(token, userDetails)) {
        // Return an SSE emitter that immediately sends an error and closes
        SseEmitter errorEmitter = new SseEmitter(0L);
        try {
          errorEmitter.send(
              SseEmitter.event().name("error").data("Unauthorized: Invalid or expired token"));
          errorEmitter.complete();
        } catch (IOException e) {
          errorEmitter.completeWithError(e);
        }
        return errorEmitter;
      }

      // Token is valid, proceed with normal SSE connection
      SseEmitter emitter = new SseEmitter(600000L); // 10 minutes timeout

      // Check if connection can be added (respects max connections limit)
      if (!sseManager.add(jobId, emitter)) {
        // Connection limit exceeded, return error
        SseEmitter limitEmitter = new SseEmitter(0L);
        try {
          limitEmitter.send(
              SseEmitter.event()
                  .name("error")
                  .data("Connection limit exceeded. Please try again later."));
          limitEmitter.complete();
        } catch (IOException e) {
          limitEmitter.completeWithError(e);
        }
        return limitEmitter;
      }

      return emitter;

    } catch (Exception e) {
      // Handle any authentication errors
      SseEmitter errorEmitter = new SseEmitter(0L);
      try {
        errorEmitter.send(SseEmitter.event().name("error").data("Authentication failed"));
        errorEmitter.complete();
      } catch (IOException ioE) {
        errorEmitter.completeWithError(ioE);
      }
      return errorEmitter;
    }
  }

  @PostMapping("/api/v1/internal/applications/{jobId}/events")
  public ResponseEntity<Void> updateJobStatus(
      @PathVariable UUID jobId, @RequestBody StatusUpdateEvent event) {
    // Handle status update and potential artifact persistence
    jobService.handleStatusUpdate(jobId, event);

    // Send SSE notification to clients
    sseManager.send(jobId, event);

    return ResponseEntity.ok().build();
  }

  @GetMapping("/api/v1/applications/{jobId}/artifact")
  public ResponseEntity<ArtifactResponse> getArtifact(@PathVariable UUID jobId) {
    try {
      ArtifactResponse artifact = jobService.getArtifactForJob(jobId);
      return ResponseEntity.ok(artifact);
    } catch (ArtifactNotFoundException e) {
      return ResponseEntity.notFound().build();
    }
  }
}
