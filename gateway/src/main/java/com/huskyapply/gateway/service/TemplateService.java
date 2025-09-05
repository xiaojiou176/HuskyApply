package com.huskyapply.gateway.service;

import com.huskyapply.gateway.config.CacheConfig;
import com.huskyapply.gateway.dto.TemplateRequest;
import com.huskyapply.gateway.dto.TemplateResponse;
import com.huskyapply.gateway.exception.ResourceNotFoundException;
import com.huskyapply.gateway.model.Template;
import com.huskyapply.gateway.model.User;
import com.huskyapply.gateway.repository.TemplateRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TemplateService {

  private static final Logger logger = LoggerFactory.getLogger(TemplateService.class);

  private final TemplateRepository templateRepository;

  public TemplateService(TemplateRepository templateRepository) {
    this.templateRepository = templateRepository;
  }

  @CacheEvict(value = CacheConfig.TEMPLATES_USER_CACHE, key = "#user.id")
  public TemplateResponse createTemplate(User user, TemplateRequest request) {
    logger.debug("Creating template for user: {} - evicting cache", user.getId());
    if (templateRepository.existsByUserAndName(user, request.getName())) {
      throw new IllegalArgumentException(
          "Template with name '" + request.getName() + "' already exists");
    }

    if (request.getIsDefault()) {
      clearCurrentDefaultTemplate(user);
    }

    Template template = new Template();
    template.setUser(user);
    template.setName(request.getName());
    template.setDescription(request.getDescription());
    template.setContent(request.getContent());
    template.setCategory(request.getCategory());
    template.setIsDefault(request.getIsDefault());
    template.setUsageCount(0);

    Instant now = Instant.now();
    template.setCreatedAt(now);
    template.setUpdatedAt(now);

    template = templateRepository.save(template);
    return toTemplateResponse(template);
  }

  public TemplateResponse getTemplate(User user, UUID templateId) {
    Template template =
        templateRepository
            .findById(templateId)
            .orElseThrow(() -> new ResourceNotFoundException("Template not found"));

    if (!template.getUser().getId().equals(user.getId())) {
      throw new ResourceNotFoundException("Template not found");
    }

    return toTemplateResponse(template);
  }

  @Cacheable(
      value = CacheConfig.TEMPLATES_USER_CACHE,
      key = "#user.id + ':' + #category + ':' + #pageable.pageNumber")
  public Page<TemplateResponse> getTemplates(User user, String category, Pageable pageable) {
    logger.debug(
        "Fetching templates for user: {}, category: {} (not in cache)", user.getId(), category);
    Page<Template> templates;
    if (category != null && !category.isEmpty()) {
      templates =
          templateRepository.findByUserAndCategoryOrderByUpdatedAtDesc(user, category, pageable);
    } else {
      templates = templateRepository.findByUserOrderByUpdatedAtDesc(user, pageable);
    }
    return templates.map(this::toTemplateResponse);
  }

  @CacheEvict(value = CacheConfig.TEMPLATES_USER_CACHE, key = "#user.id", allEntries = false)
  public TemplateResponse updateTemplate(User user, UUID templateId, TemplateRequest request) {
    logger.debug("Updating template {} for user: {} - evicting cache", templateId, user.getId());
    Template template =
        templateRepository
            .findById(templateId)
            .orElseThrow(() -> new ResourceNotFoundException("Template not found"));

    if (!template.getUser().getId().equals(user.getId())) {
      throw new ResourceNotFoundException("Template not found");
    }

    if (!template.getName().equals(request.getName())
        && templateRepository.existsByUserAndName(user, request.getName())) {
      throw new IllegalArgumentException(
          "Template with name '" + request.getName() + "' already exists");
    }

    if (request.getIsDefault() && !template.getIsDefault()) {
      clearCurrentDefaultTemplate(user);
    }

    template.setName(request.getName());
    template.setDescription(request.getDescription());
    template.setContent(request.getContent());
    template.setCategory(request.getCategory());
    template.setIsDefault(request.getIsDefault());
    template.setUpdatedAt(Instant.now());

    template = templateRepository.save(template);
    return toTemplateResponse(template);
  }

  @CacheEvict(value = CacheConfig.TEMPLATES_USER_CACHE, key = "#user.id")
  public void deleteTemplate(User user, UUID templateId) {
    logger.debug("Deleting template {} for user: {} - evicting cache", templateId, user.getId());
    Template template =
        templateRepository
            .findById(templateId)
            .orElseThrow(() -> new ResourceNotFoundException("Template not found"));

    if (!template.getUser().getId().equals(user.getId())) {
      throw new ResourceNotFoundException("Template not found");
    }

    templateRepository.delete(template);
  }

  public TemplateResponse setDefaultTemplate(User user, UUID templateId) {
    Template template =
        templateRepository
            .findById(templateId)
            .orElseThrow(() -> new ResourceNotFoundException("Template not found"));

    if (!template.getUser().getId().equals(user.getId())) {
      throw new ResourceNotFoundException("Template not found");
    }

    clearCurrentDefaultTemplate(user);
    template.setIsDefault(true);
    template.setUpdatedAt(Instant.now());

    template = templateRepository.save(template);
    return toTemplateResponse(template);
  }

  public TemplateResponse duplicateTemplate(User user, UUID templateId, String newName) {
    Template original =
        templateRepository
            .findById(templateId)
            .orElseThrow(() -> new ResourceNotFoundException("Template not found"));

    if (!original.getUser().getId().equals(user.getId())) {
      throw new ResourceNotFoundException("Template not found");
    }

    String duplicateName = newName != null ? newName : original.getName() + " (Copy)";

    if (templateRepository.existsByUserAndName(user, duplicateName)) {
      duplicateName = generateUniqueTemplateName(user, duplicateName);
    }

    Template duplicate = new Template();
    duplicate.setUser(user);
    duplicate.setName(duplicateName);
    duplicate.setDescription(original.getDescription());
    duplicate.setContent(original.getContent());
    duplicate.setCategory(original.getCategory());
    duplicate.setIsDefault(false);
    duplicate.setUsageCount(0);

    Instant now = Instant.now();
    duplicate.setCreatedAt(now);
    duplicate.setUpdatedAt(now);

    duplicate = templateRepository.save(duplicate);
    return toTemplateResponse(duplicate);
  }

  public void incrementUsageCount(UUID templateId) {
    templateRepository
        .findById(templateId)
        .ifPresent(
            template -> {
              template.setUsageCount(template.getUsageCount() + 1);
              template.setUpdatedAt(Instant.now());
              templateRepository.save(template);
            });
  }

  private void clearCurrentDefaultTemplate(User user) {
    templateRepository
        .findByUserAndIsDefaultTrue(user)
        .ifPresent(
            currentDefault -> {
              currentDefault.setIsDefault(false);
              templateRepository.save(currentDefault);
            });
  }

  private String generateUniqueTemplateName(User user, String baseName) {
    int counter = 1;
    String uniqueName;
    do {
      uniqueName = baseName + " (" + counter + ")";
      counter++;
    } while (templateRepository.existsByUserAndName(user, uniqueName));
    return uniqueName;
  }

  private TemplateResponse toTemplateResponse(Template template) {
    return new TemplateResponse(
        template.getId().toString(),
        template.getName(),
        template.getDescription(),
        template.getContent(),
        template.getCategory(),
        template.getIsDefault(),
        template.getUsageCount(),
        template.getCreatedAt(),
        template.getUpdatedAt());
  }
}
