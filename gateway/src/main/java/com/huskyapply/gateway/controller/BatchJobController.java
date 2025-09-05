package com.huskyapply.gateway.controller;

import com.huskyapply.gateway.dto.BatchJobRequest;
import com.huskyapply.gateway.dto.BatchJobResponse;
import com.huskyapply.gateway.model.User;
import com.huskyapply.gateway.service.BatchJobService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/batch-jobs")
public class BatchJobController {

  private final BatchJobService batchJobService;

  public BatchJobController(BatchJobService batchJobService) {
    this.batchJobService = batchJobService;
  }

  @PostMapping
  public ResponseEntity<BatchJobResponse> createBatchJob(
      @AuthenticationPrincipal User user, @Valid @RequestBody BatchJobRequest request) {
    BatchJobResponse batchJob = batchJobService.createBatchJob(user, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(batchJob);
  }

  @GetMapping("/{batchJobId}")
  public ResponseEntity<BatchJobResponse> getBatchJob(
      @AuthenticationPrincipal User user, @PathVariable UUID batchJobId) {
    BatchJobResponse batchJob = batchJobService.getBatchJob(user, batchJobId);
    return ResponseEntity.ok(batchJob);
  }

  @GetMapping
  public ResponseEntity<Page<BatchJobResponse>> getBatchJobs(
      @AuthenticationPrincipal User user,
      @RequestParam(required = false) String status,
      Pageable pageable) {
    Page<BatchJobResponse> batchJobs = batchJobService.getBatchJobs(user, status, pageable);
    return ResponseEntity.ok(batchJobs);
  }

  @GetMapping("/search")
  public ResponseEntity<List<BatchJobResponse>> searchBatchJobs(
      @AuthenticationPrincipal User user, @RequestParam String searchTerm) {

    if (searchTerm == null || searchTerm.trim().isEmpty()) {
      return ResponseEntity.badRequest().build();
    }

    List<BatchJobResponse> batchJobs = batchJobService.searchBatchJobs(user, searchTerm.trim());
    return ResponseEntity.ok(batchJobs);
  }

  @PostMapping("/{batchJobId}/start")
  public ResponseEntity<BatchJobResponse> startBatchJob(
      @AuthenticationPrincipal User user, @PathVariable UUID batchJobId) {
    BatchJobResponse batchJob = batchJobService.startBatchJob(user, batchJobId);
    return ResponseEntity.ok(batchJob);
  }

  @PostMapping("/{batchJobId}/cancel")
  public ResponseEntity<BatchJobResponse> cancelBatchJob(
      @AuthenticationPrincipal User user, @PathVariable UUID batchJobId) {
    BatchJobResponse batchJob = batchJobService.cancelBatchJob(user, batchJobId);
    return ResponseEntity.ok(batchJob);
  }

  @DeleteMapping("/{batchJobId}")
  public ResponseEntity<Void> deleteBatchJob(
      @AuthenticationPrincipal User user, @PathVariable UUID batchJobId) {
    batchJobService.deleteBatchJob(user, batchJobId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/recent")
  public ResponseEntity<List<BatchJobResponse>> getRecentBatchJobs(
      @AuthenticationPrincipal User user) {
    List<BatchJobResponse> batchJobs = batchJobService.getRecentBatchJobs(user);
    return ResponseEntity.ok(batchJobs);
  }
}
