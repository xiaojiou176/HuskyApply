package com.huskyapply.gateway.controller;

import com.huskyapply.gateway.dto.TemplateRequest;
import com.huskyapply.gateway.dto.TemplateResponse;
import com.huskyapply.gateway.model.User;
import com.huskyapply.gateway.service.TemplateService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/templates")
public class TemplateController {

  private final TemplateService templateService;

  public TemplateController(TemplateService templateService) {
    this.templateService = templateService;
  }

  @PostMapping
  public ResponseEntity<TemplateResponse> createTemplate(
      @AuthenticationPrincipal User user, @Valid @RequestBody TemplateRequest request) {
    TemplateResponse template = templateService.createTemplate(user, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(template);
  }

  @GetMapping("/{templateId}")
  public ResponseEntity<TemplateResponse> getTemplate(
      @AuthenticationPrincipal User user, @PathVariable UUID templateId) {
    TemplateResponse template = templateService.getTemplate(user, templateId);
    return ResponseEntity.ok(template);
  }

  @GetMapping
  public ResponseEntity<Page<TemplateResponse>> getTemplates(
      @AuthenticationPrincipal User user,
      @RequestParam(required = false) String category,
      Pageable pageable) {
    Page<TemplateResponse> templates = templateService.getTemplates(user, category, pageable);
    return ResponseEntity.ok(templates);
  }

  @PutMapping("/{templateId}")
  public ResponseEntity<TemplateResponse> updateTemplate(
      @AuthenticationPrincipal User user,
      @PathVariable UUID templateId,
      @Valid @RequestBody TemplateRequest request) {
    TemplateResponse template = templateService.updateTemplate(user, templateId, request);
    return ResponseEntity.ok(template);
  }

  @DeleteMapping("/{templateId}")
  public ResponseEntity<Void> deleteTemplate(
      @AuthenticationPrincipal User user, @PathVariable UUID templateId) {
    templateService.deleteTemplate(user, templateId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{templateId}/default")
  public ResponseEntity<TemplateResponse> setDefaultTemplate(
      @AuthenticationPrincipal User user, @PathVariable UUID templateId) {
    TemplateResponse template = templateService.setDefaultTemplate(user, templateId);
    return ResponseEntity.ok(template);
  }

  @PostMapping("/{templateId}/duplicate")
  public ResponseEntity<TemplateResponse> duplicateTemplate(
      @AuthenticationPrincipal User user,
      @PathVariable UUID templateId,
      @RequestParam(required = false) String newName) {
    TemplateResponse template = templateService.duplicateTemplate(user, templateId, newName);
    return ResponseEntity.status(HttpStatus.CREATED).body(template);
  }
}
