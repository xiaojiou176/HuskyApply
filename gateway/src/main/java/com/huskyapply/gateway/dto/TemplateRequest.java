package com.huskyapply.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class TemplateRequest {

  @NotBlank(message = "Template name is required")
  @Size(max = 100, message = "Template name must not exceed 100 characters")
  private String name;

  @Size(max = 500, message = "Description must not exceed 500 characters")
  private String description;

  @NotBlank(message = "Template content is required")
  @Size(max = 10000, message = "Template content must not exceed 10000 characters")
  private String content;

  @Size(max = 50, message = "Category must not exceed 50 characters")
  private String category;

  private Boolean isDefault = false;

  public TemplateRequest() {}

  public TemplateRequest(
      String name, String description, String content, String category, Boolean isDefault) {
    this.name = name;
    this.description = description;
    this.content = content;
    this.category = category;
    this.isDefault = isDefault;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public Boolean getIsDefault() {
    return isDefault;
  }

  public void setIsDefault(Boolean isDefault) {
    this.isDefault = isDefault;
  }
}
