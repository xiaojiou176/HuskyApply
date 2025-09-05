package com.huskyapply.gateway.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.huskyapply.gateway.dto.TemplateRequest;
import com.huskyapply.gateway.dto.TemplateResponse;
import com.huskyapply.gateway.exception.ResourceNotFoundException;
import com.huskyapply.gateway.model.Template;
import com.huskyapply.gateway.model.User;
import com.huskyapply.gateway.repository.TemplateRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class TemplateServiceTest {

  @Mock private TemplateRepository templateRepository;

  private TemplateService templateService;

  private User testUser;
  private TemplateRequest testTemplateRequest;
  private Template testTemplate;
  private Instant now;

  @BeforeEach
  void setUp() {
    templateService = new TemplateService(templateRepository);

    now = Instant.now();

    // Create test data
    testUser = new User();
    testUser.setId(UUID.randomUUID());
    testUser.setEmail("test@example.com");

    testTemplateRequest = new TemplateRequest();
    testTemplateRequest.setName("Test Template");
    testTemplateRequest.setDescription("Test Description");
    testTemplateRequest.setContent("Test template content with {{variables}}");
    testTemplateRequest.setCategory("technical");
    testTemplateRequest.setIsDefault(false);

    testTemplate = createTestTemplate("Test Template", "technical", false);
  }

  @Test
  void createTemplate_Success() {
    // Arrange
    when(templateRepository.existsByUserAndName(testUser, "Test Template")).thenReturn(false);
    when(templateRepository.save(any(Template.class))).thenReturn(testTemplate);

    // Act
    TemplateResponse result = templateService.createTemplate(testUser, testTemplateRequest);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo(testTemplate.getId().toString());
    assertThat(result.getName()).isEqualTo("Test Template");
    assertThat(result.getDescription()).isEqualTo("Test Description");
    assertThat(result.getContent()).isEqualTo("Test template content with {{variables}}");
    assertThat(result.getCategory()).isEqualTo("technical");
    assertThat(result.getIsDefault()).isFalse();
    assertThat(result.getUsageCount()).isZero();

    // Verify template is saved with correct properties
    ArgumentCaptor<Template> templateCaptor = ArgumentCaptor.forClass(Template.class);
    verify(templateRepository).save(templateCaptor.capture());

    Template savedTemplate = templateCaptor.getValue();
    assertThat(savedTemplate.getUser()).isEqualTo(testUser);
    assertThat(savedTemplate.getName()).isEqualTo("Test Template");
    assertThat(savedTemplate.getUsageCount()).isZero();
    assertThat(savedTemplate.getCreatedAt()).isNotNull();
    assertThat(savedTemplate.getUpdatedAt()).isNotNull();
  }

  @Test
  void createTemplate_WithDefaultFlag() {
    // Arrange
    testTemplateRequest.setIsDefault(true);

    Template currentDefaultTemplate = createTestTemplate("Current Default", "business", true);

    when(templateRepository.existsByUserAndName(testUser, "Test Template")).thenReturn(false);
    when(templateRepository.findByUserAndIsDefaultTrue(testUser))
        .thenReturn(Optional.of(currentDefaultTemplate));
    when(templateRepository.save(any(Template.class))).thenReturn(testTemplate);

    // Act
    TemplateResponse result = templateService.createTemplate(testUser, testTemplateRequest);

    // Assert
    assertThat(result.getIsDefault()).isTrue();

    // Verify current default is cleared
    verify(templateRepository).findByUserAndIsDefaultTrue(testUser);
    assertThat(currentDefaultTemplate.getIsDefault()).isFalse();

    // Verify both templates are saved (current default update + new template)
    verify(templateRepository, times(2)).save(any(Template.class));
  }

  @Test
  void createTemplate_DuplicateName() {
    // Arrange
    when(templateRepository.existsByUserAndName(testUser, "Test Template")).thenReturn(true);

    // Act & Assert
    assertThatThrownBy(() -> templateService.createTemplate(testUser, testTemplateRequest))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Template with name 'Test Template' already exists");

    verify(templateRepository, never()).save(any(Template.class));
  }

  @Test
  void getTemplate_Success() {
    // Arrange
    UUID templateId = testTemplate.getId();
    when(templateRepository.findById(templateId)).thenReturn(Optional.of(testTemplate));

    // Act
    TemplateResponse result = templateService.getTemplate(testUser, templateId);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo(templateId.toString());
    assertThat(result.getName()).isEqualTo(testTemplate.getName());
  }

  @Test
  void getTemplate_NotFound() {
    // Arrange
    UUID templateId = UUID.randomUUID();
    when(templateRepository.findById(templateId)).thenReturn(Optional.empty());

    // Act & Assert
    assertThatThrownBy(() -> templateService.getTemplate(testUser, templateId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Template not found");
  }

  @Test
  void getTemplate_WrongUser() {
    // Arrange
    UUID templateId = testTemplate.getId();
    User differentUser = new User();
    differentUser.setId(UUID.randomUUID());
    differentUser.setEmail("different@example.com");

    when(templateRepository.findById(templateId)).thenReturn(Optional.of(testTemplate));

    // Act & Assert
    assertThatThrownBy(() -> templateService.getTemplate(differentUser, templateId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Template not found");
  }

  @Test
  void getTemplates_WithoutCategory() {
    // Arrange
    Template template1 = createTestTemplate("Template 1", "technical", false);
    Template template2 = createTestTemplate("Template 2", "business", true);
    Page<Template> templatePage = new PageImpl<>(Arrays.asList(template1, template2));

    Pageable pageable = PageRequest.of(0, 10);
    when(templateRepository.findByUserOrderByUpdatedAtDesc(testUser, pageable))
        .thenReturn(templatePage);

    // Act
    Page<TemplateResponse> result = templateService.getTemplates(testUser, null, pageable);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(2);
    assertThat(result.getContent().get(0).getName()).isEqualTo("Template 1");
    assertThat(result.getContent().get(1).getName()).isEqualTo("Template 2");

    verify(templateRepository).findByUserOrderByUpdatedAtDesc(testUser, pageable);
    verify(templateRepository, never())
        .findByUserAndCategoryOrderByUpdatedAtDesc(any(), any(), any());
  }

  @Test
  void getTemplates_WithCategory() {
    // Arrange
    String category = "technical";
    Template template1 = createTestTemplate("Template 1", category, false);
    Page<Template> templatePage = new PageImpl<>(Arrays.asList(template1));

    Pageable pageable = PageRequest.of(0, 10);
    when(templateRepository.findByUserAndCategoryOrderByUpdatedAtDesc(testUser, category, pageable))
        .thenReturn(templatePage);

    // Act
    Page<TemplateResponse> result = templateService.getTemplates(testUser, category, pageable);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).getCategory()).isEqualTo(category);

    verify(templateRepository)
        .findByUserAndCategoryOrderByUpdatedAtDesc(testUser, category, pageable);
  }

  @Test
  void updateTemplate_Success() {
    // Arrange
    UUID templateId = testTemplate.getId();

    TemplateRequest updateRequest = new TemplateRequest();
    updateRequest.setName("Updated Template");
    updateRequest.setDescription("Updated Description");
    updateRequest.setContent("Updated content");
    updateRequest.setCategory("business");
    updateRequest.setIsDefault(false);

    when(templateRepository.findById(templateId)).thenReturn(Optional.of(testTemplate));
    when(templateRepository.save(any(Template.class))).thenReturn(testTemplate);

    // Act
    TemplateResponse result = templateService.updateTemplate(testUser, templateId, updateRequest);

    // Assert
    assertThat(result).isNotNull();

    // Verify template properties are updated
    assertThat(testTemplate.getName()).isEqualTo("Updated Template");
    assertThat(testTemplate.getDescription()).isEqualTo("Updated Description");
    assertThat(testTemplate.getContent()).isEqualTo("Updated content");
    assertThat(testTemplate.getCategory()).isEqualTo("business");
    assertThat(testTemplate.getUpdatedAt()).isNotNull();

    verify(templateRepository).save(testTemplate);
  }

  @Test
  void updateTemplate_MakeDefault() {
    // Arrange
    UUID templateId = testTemplate.getId();
    Template currentDefaultTemplate = createTestTemplate("Current Default", "business", true);

    TemplateRequest updateRequest = new TemplateRequest();
    updateRequest.setName("Test Template");
    updateRequest.setDescription("Test Description");
    updateRequest.setContent("Test content");
    updateRequest.setCategory("technical");
    updateRequest.setIsDefault(true);

    when(templateRepository.findById(templateId)).thenReturn(Optional.of(testTemplate));
    when(templateRepository.findByUserAndIsDefaultTrue(testUser))
        .thenReturn(Optional.of(currentDefaultTemplate));
    when(templateRepository.save(any(Template.class))).thenReturn(testTemplate);

    // Act
    templateService.updateTemplate(testUser, templateId, updateRequest);

    // Assert
    assertThat(testTemplate.getIsDefault()).isTrue();
    assertThat(currentDefaultTemplate.getIsDefault()).isFalse();

    // Verify both templates are saved
    verify(templateRepository, times(2)).save(any(Template.class));
  }

  @Test
  void updateTemplate_DuplicateNameError() {
    // Arrange
    UUID templateId = testTemplate.getId();

    TemplateRequest updateRequest = new TemplateRequest();
    updateRequest.setName("Existing Template");
    updateRequest.setDescription("Test Description");
    updateRequest.setContent("Test content");
    updateRequest.setCategory("technical");
    updateRequest.setIsDefault(false);

    when(templateRepository.findById(templateId)).thenReturn(Optional.of(testTemplate));
    when(templateRepository.existsByUserAndName(testUser, "Existing Template")).thenReturn(true);

    // Act & Assert
    assertThatThrownBy(() -> templateService.updateTemplate(testUser, templateId, updateRequest))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Template with name 'Existing Template' already exists");

    verify(templateRepository, never()).save(any(Template.class));
  }

  @Test
  void deleteTemplate_Success() {
    // Arrange
    UUID templateId = testTemplate.getId();
    when(templateRepository.findById(templateId)).thenReturn(Optional.of(testTemplate));

    // Act
    templateService.deleteTemplate(testUser, templateId);

    // Assert
    verify(templateRepository).delete(testTemplate);
  }

  @Test
  void deleteTemplate_NotFound() {
    // Arrange
    UUID templateId = UUID.randomUUID();
    when(templateRepository.findById(templateId)).thenReturn(Optional.empty());

    // Act & Assert
    assertThatThrownBy(() -> templateService.deleteTemplate(testUser, templateId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Template not found");

    verify(templateRepository, never()).delete(any(Template.class));
  }

  @Test
  void setDefaultTemplate_Success() {
    // Arrange
    UUID templateId = testTemplate.getId();
    Template currentDefaultTemplate = createTestTemplate("Current Default", "business", true);

    when(templateRepository.findById(templateId)).thenReturn(Optional.of(testTemplate));
    when(templateRepository.findByUserAndIsDefaultTrue(testUser))
        .thenReturn(Optional.of(currentDefaultTemplate));
    when(templateRepository.save(any(Template.class))).thenReturn(testTemplate);

    // Act
    TemplateResponse result = templateService.setDefaultTemplate(testUser, templateId);

    // Assert
    assertThat(result.getIsDefault()).isTrue();
    assertThat(testTemplate.getIsDefault()).isTrue();
    assertThat(currentDefaultTemplate.getIsDefault()).isFalse();

    verify(templateRepository, times(2)).save(any(Template.class));
  }

  @Test
  void duplicateTemplate_Success() {
    // Arrange
    UUID originalId = testTemplate.getId();
    String newName = "Duplicated Template";

    when(templateRepository.findById(originalId)).thenReturn(Optional.of(testTemplate));
    when(templateRepository.existsByUserAndName(testUser, newName)).thenReturn(false);
    when(templateRepository.save(any(Template.class)))
        .thenAnswer(
            invocation -> {
              Template template = invocation.getArgument(0);
              template.setId(UUID.randomUUID());
              return template;
            });

    // Act
    TemplateResponse result = templateService.duplicateTemplate(testUser, originalId, newName);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.getName()).isEqualTo(newName);
    assertThat(result.getDescription()).isEqualTo(testTemplate.getDescription());
    assertThat(result.getContent()).isEqualTo(testTemplate.getContent());
    assertThat(result.getCategory()).isEqualTo(testTemplate.getCategory());
    assertThat(result.getIsDefault()).isFalse();
    assertThat(result.getUsageCount()).isZero();

    ArgumentCaptor<Template> templateCaptor = ArgumentCaptor.forClass(Template.class);
    verify(templateRepository).save(templateCaptor.capture());

    Template duplicatedTemplate = templateCaptor.getValue();
    assertThat(duplicatedTemplate.getName()).isEqualTo(newName);
    assertThat(duplicatedTemplate.getUsageCount()).isZero();
    assertThat(duplicatedTemplate.getIsDefault()).isFalse();
  }

  @Test
  void duplicateTemplate_DefaultName() {
    // Arrange
    UUID originalId = testTemplate.getId();
    String expectedName = testTemplate.getName() + " (Copy)";

    when(templateRepository.findById(originalId)).thenReturn(Optional.of(testTemplate));
    when(templateRepository.existsByUserAndName(testUser, expectedName)).thenReturn(false);
    when(templateRepository.save(any(Template.class)))
        .thenAnswer(
            invocation -> {
              Template template = invocation.getArgument(0);
              template.setId(UUID.randomUUID());
              return template;
            });

    // Act
    TemplateResponse result = templateService.duplicateTemplate(testUser, originalId, null);

    // Assert
    assertThat(result.getName()).isEqualTo(expectedName);
  }

  @Test
  void duplicateTemplate_GenerateUniqueName() {
    // Arrange
    UUID originalId = testTemplate.getId();
    String newName = "Duplicated Template";
    String uniqueName = "Duplicated Template (1)";

    when(templateRepository.findById(originalId)).thenReturn(Optional.of(testTemplate));
    when(templateRepository.existsByUserAndName(testUser, newName)).thenReturn(true);
    when(templateRepository.existsByUserAndName(testUser, uniqueName)).thenReturn(false);
    when(templateRepository.save(any(Template.class)))
        .thenAnswer(
            invocation -> {
              Template template = invocation.getArgument(0);
              template.setId(UUID.randomUUID());
              return template;
            });

    // Act
    TemplateResponse result = templateService.duplicateTemplate(testUser, originalId, newName);

    // Assert
    assertThat(result.getName()).isEqualTo(uniqueName);
  }

  @Test
  void incrementUsageCount_Success() {
    // Arrange
    UUID templateId = testTemplate.getId();
    int originalUsageCount = testTemplate.getUsageCount();

    when(templateRepository.findById(templateId)).thenReturn(Optional.of(testTemplate));
    when(templateRepository.save(any(Template.class))).thenReturn(testTemplate);

    // Act
    templateService.incrementUsageCount(templateId);

    // Assert
    assertThat(testTemplate.getUsageCount()).isEqualTo(originalUsageCount + 1);
    assertThat(testTemplate.getUpdatedAt()).isNotNull();

    verify(templateRepository).save(testTemplate);
  }

  @Test
  void incrementUsageCount_TemplateNotFound() {
    // Arrange
    UUID templateId = UUID.randomUUID();
    when(templateRepository.findById(templateId)).thenReturn(Optional.empty());

    // Act
    templateService.incrementUsageCount(templateId);

    // Assert
    verify(templateRepository, never()).save(any(Template.class));
  }

  // Helper methods

  private Template createTestTemplate(String name, String category, boolean isDefault) {
    Template template = new Template();
    template.setId(UUID.randomUUID());
    template.setUser(testUser);
    template.setName(name);
    template.setDescription("Test description for " + name);
    template.setContent("Test content for " + name);
    template.setCategory(category);
    template.setIsDefault(isDefault);
    template.setUsageCount(0);
    template.setCreatedAt(now.minusSeconds(3600));
    template.setUpdatedAt(now);
    return template;
  }
}
