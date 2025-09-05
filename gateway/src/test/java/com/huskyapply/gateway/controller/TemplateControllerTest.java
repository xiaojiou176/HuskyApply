package com.huskyapply.gateway.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huskyapply.gateway.dto.TemplateRequest;
import com.huskyapply.gateway.dto.TemplateResponse;
import com.huskyapply.gateway.model.User;
import com.huskyapply.gateway.service.TemplateService;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
public class TemplateControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private TemplateService templateService;

  @Autowired private ObjectMapper objectMapper;

  private TemplateRequest templateRequest;
  private TemplateResponse templateResponse;
  private UUID templateId;

  @BeforeEach
  public void setUp() {
    templateId = UUID.randomUUID();

    templateRequest =
        new TemplateRequest(
            "Test Template",
            "This is a test template content with {{company}} and {{position}} placeholders.",
            "Cover Letter",
            "General",
            false);

    templateResponse =
        new TemplateResponse(
            templateId.toString(),
            "Test Template",
            "This is a test template content with {{company}} and {{position}} placeholders.",
            "Cover Letter",
            "General",
            false,
            0,
            Instant.now(),
            Instant.now());
  }

  @Test
  @WithMockUser
  public void createTemplate_WithValidRequest_ReturnsCreatedTemplate() throws Exception {
    // Arrange
    when(templateService.createTemplate(any(User.class), any(TemplateRequest.class)))
        .thenReturn(templateResponse);

    // Act & Assert
    mockMvc
        .perform(
            post("/api/v1/templates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(templateRequest)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Test Template"))
        .andExpect(jsonPath("$.contentType").value("Cover Letter"))
        .andExpect(jsonPath("$.category").value("General"))
        .andExpect(jsonPath("$.isDefault").value(false));
  }

  @Test
  @WithMockUser
  public void getTemplate_WithValidId_ReturnsTemplate() throws Exception {
    // Arrange
    when(templateService.getTemplate(any(User.class), eq(templateId))).thenReturn(templateResponse);

    // Act & Assert
    mockMvc
        .perform(
            get("/api/v1/templates/{templateId}", templateId)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Test Template"))
        .andExpect(jsonPath("$.category").value("General"));
  }

  @Test
  @WithMockUser
  public void getTemplates_ReturnsTemplatesPaged() throws Exception {
    // Arrange
    Page<TemplateResponse> templatesPage =
        new PageImpl<>(Arrays.asList(templateResponse), PageRequest.of(0, 10), 1);
    when(templateService.getTemplates(any(User.class), any(), any(Pageable.class)))
        .thenReturn(templatesPage);

    // Act & Assert
    mockMvc
        .perform(
            get("/api/v1/templates")
                .param("page", "0")
                .param("size", "10")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content[0].name").value("Test Template"))
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  @WithMockUser
  public void getTemplates_WithCategory_ReturnsFilteredTemplates() throws Exception {
    // Arrange
    Page<TemplateResponse> templatesPage =
        new PageImpl<>(Arrays.asList(templateResponse), PageRequest.of(0, 10), 1);
    when(templateService.getTemplates(any(User.class), eq("General"), any(Pageable.class)))
        .thenReturn(templatesPage);

    // Act & Assert
    mockMvc
        .perform(
            get("/api/v1/templates")
                .param("category", "General")
                .param("page", "0")
                .param("size", "10")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].category").value("General"));
  }

  @Test
  @WithMockUser
  public void updateTemplate_WithValidRequest_ReturnsUpdatedTemplate() throws Exception {
    // Arrange
    TemplateRequest updateRequest =
        new TemplateRequest(
            "Updated Template", "Updated content", "Cover Letter", "Professional", true);
    TemplateResponse updatedResponse =
        new TemplateResponse(
            templateId.toString(),
            "Updated Template",
            "Updated content",
            "Cover Letter",
            "Professional",
            true,
            1,
            Instant.now(),
            Instant.now());

    when(templateService.updateTemplate(
            any(User.class), eq(templateId), any(TemplateRequest.class)))
        .thenReturn(updatedResponse);

    // Act & Assert
    mockMvc
        .perform(
            put("/api/v1/templates/{templateId}", templateId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Updated Template"))
        .andExpect(jsonPath("$.category").value("Professional"))
        .andExpect(jsonPath("$.isDefault").value(true));
  }

  @Test
  @WithMockUser
  public void deleteTemplate_WithValidId_ReturnsNoContent() throws Exception {
    // Arrange
    doNothing().when(templateService).deleteTemplate(any(User.class), eq(templateId));

    // Act & Assert
    mockMvc
        .perform(
            delete("/api/v1/templates/{templateId}", templateId)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isNoContent());
  }

  @Test
  @WithMockUser
  public void setDefaultTemplate_WithValidId_ReturnsUpdatedTemplate() throws Exception {
    // Arrange
    TemplateResponse defaultResponse =
        new TemplateResponse(
            templateId.toString(),
            "Test Template",
            "This is a test template content with {{company}} and {{position}} placeholders.",
            "Cover Letter",
            "General",
            true,
            0,
            Instant.now(),
            Instant.now());

    when(templateService.setDefaultTemplate(any(User.class), eq(templateId)))
        .thenReturn(defaultResponse);

    // Act & Assert
    mockMvc
        .perform(
            post("/api/v1/templates/{templateId}/default", templateId)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isDefault").value(true));
  }

  @Test
  @WithMockUser
  public void duplicateTemplate_WithValidId_ReturnsNewTemplate() throws Exception {
    // Arrange
    UUID newTemplateId = UUID.randomUUID();
    TemplateResponse duplicatedResponse =
        new TemplateResponse(
            newTemplateId.toString(),
            "Test Template (Copy)",
            "This is a test template content with {{company}} and {{position}} placeholders.",
            "Cover Letter",
            "General",
            false,
            0,
            Instant.now(),
            Instant.now());

    when(templateService.duplicateTemplate(
            any(User.class), eq(templateId), eq("Test Template (Copy)")))
        .thenReturn(duplicatedResponse);

    // Act & Assert
    mockMvc
        .perform(
            post("/api/v1/templates/{templateId}/duplicate", templateId)
                .param("newName", "Test Template (Copy)")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Test Template (Copy)"));
  }

  @Test
  public void createTemplate_WithoutAuthentication_ReturnsUnauthorized() throws Exception {
    // Act & Assert
    mockMvc
        .perform(
            post("/api/v1/templates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(templateRequest)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser
  public void createTemplate_WithInvalidRequest_ReturnsBadRequest() throws Exception {
    // Arrange
    TemplateRequest invalidRequest =
        new TemplateRequest(
            "", // Invalid: empty name
            "content",
            "Cover Letter",
            "General",
            false);

    // Act & Assert
    mockMvc
        .perform(
            post("/api/v1/templates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
        .andExpect(status().isBadRequest());
  }
}
