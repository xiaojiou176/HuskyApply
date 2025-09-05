package com.huskyapply.gateway.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entity representing a reusable cover letter template.
 *
 * <p>Templates allow users to create reusable cover letter structures that can be customized for
 * different job applications.
 */
@Entity
@Table(name = "templates")
public class Template {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "description")
  private String description;

  @Column(name = "content", nullable = false)
  private String content;

  @Column(name = "category")
  private String category;

  @Column(name = "is_default")
  private Boolean isDefault = false;

  @Column(name = "usage_count")
  private Integer usageCount = 0;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public Template() {}

  public Template(
      UUID id,
      User user,
      String name,
      String description,
      String content,
      String category,
      Boolean isDefault,
      Integer usageCount,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.user = user;
    this.name = name;
    this.description = description;
    this.content = content;
    this.category = category;
    this.isDefault = isDefault;
    this.usageCount = usageCount;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public static TemplateBuilder builder() {
    return new TemplateBuilder();
  }

  // Getters and Setters
  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
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

  public Integer getUsageCount() {
    return usageCount;
  }

  public void setUsageCount(Integer usageCount) {
    this.usageCount = usageCount;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Template template = (Template) o;
    return Objects.equals(id, template.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @PrePersist
  protected void onCreate() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
    if (updatedAt == null) {
      updatedAt = Instant.now();
    }
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = Instant.now();
  }

  /** Increment usage count when template is used. */
  public void incrementUsage() {
    this.usageCount = (this.usageCount == null ? 0 : this.usageCount) + 1;
  }

  public static class TemplateBuilder {
    private UUID id;
    private User user;
    private String name;
    private String description;
    private String content;
    private String category;
    private Boolean isDefault = false;
    private Integer usageCount = 0;
    private Instant createdAt;
    private Instant updatedAt;

    public TemplateBuilder id(UUID id) {
      this.id = id;
      return this;
    }

    public TemplateBuilder user(User user) {
      this.user = user;
      return this;
    }

    public TemplateBuilder name(String name) {
      this.name = name;
      return this;
    }

    public TemplateBuilder description(String description) {
      this.description = description;
      return this;
    }

    public TemplateBuilder content(String content) {
      this.content = content;
      return this;
    }

    public TemplateBuilder category(String category) {
      this.category = category;
      return this;
    }

    public TemplateBuilder isDefault(Boolean isDefault) {
      this.isDefault = isDefault;
      return this;
    }

    public TemplateBuilder usageCount(Integer usageCount) {
      this.usageCount = usageCount;
      return this;
    }

    public TemplateBuilder createdAt(Instant createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public TemplateBuilder updatedAt(Instant updatedAt) {
      this.updatedAt = updatedAt;
      return this;
    }

    public Template build() {
      return new Template(
          id,
          user,
          name,
          description,
          content,
          category,
          isDefault,
          usageCount,
          createdAt,
          updatedAt);
    }
  }
}
