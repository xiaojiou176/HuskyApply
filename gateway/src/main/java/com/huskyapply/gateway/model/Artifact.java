package com.huskyapply.gateway.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "artifacts")
public class Artifact {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private UUID id;

  @Column(name = "job_id", insertable = false, updatable = false)
  private UUID jobId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "job_id", nullable = false)
  private Job job;

  @Column(name = "content_type")
  private String contentType;

  @Column(name = "generated_text", columnDefinition = "TEXT")
  private String generatedText;

  @Column(name = "word_count")
  private Integer wordCount;

  @Column(name = "extracted_skills", columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private JsonNode extractedSkills;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "updated_at")
  private Instant updatedAt;

  public Artifact() {}

  public Artifact(
      UUID id,
      Job job,
      String contentType,
      String generatedText,
      Integer wordCount,
      JsonNode extractedSkills,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.job = job;
    this.jobId = job != null ? job.getId() : null;
    this.contentType = contentType;
    this.generatedText = generatedText;
    this.wordCount = wordCount;
    this.extractedSkills = extractedSkills;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  @PrePersist
  protected void onCreate() {
    createdAt = Instant.now();
    updatedAt = Instant.now();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = Instant.now();
  }

  public static ArtifactBuilder builder() {
    return new ArtifactBuilder();
  }

  // Getters and Setters
  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getJobId() {
    return jobId;
  }

  public Job getJob() {
    return job;
  }

  public void setJob(Job job) {
    this.job = job;
    if (job != null) {
      this.jobId = job.getId();
    }
  }

  public String getContentType() {
    return contentType;
  }

  public void setContentType(String contentType) {
    this.contentType = contentType;
  }

  public String getGeneratedText() {
    return generatedText;
  }

  public void setGeneratedText(String generatedText) {
    this.generatedText = generatedText;
  }

  public Integer getWordCount() {
    return wordCount;
  }

  public void setWordCount(Integer wordCount) {
    this.wordCount = wordCount;
  }

  public JsonNode getExtractedSkills() {
    return extractedSkills;
  }

  public void setExtractedSkills(JsonNode extractedSkills) {
    this.extractedSkills = extractedSkills;
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
    Artifact artifact = (Artifact) o;
    return Objects.equals(id, artifact.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  public static class ArtifactBuilder {
    private UUID id;
    private Job job;
    private String contentType;
    private String generatedText;
    private Integer wordCount;
    private JsonNode extractedSkills;
    private Instant createdAt;
    private Instant updatedAt;

    public ArtifactBuilder id(UUID id) {
      this.id = id;
      return this;
    }

    public ArtifactBuilder job(Job job) {
      this.job = job;
      return this;
    }

    public ArtifactBuilder contentType(String contentType) {
      this.contentType = contentType;
      return this;
    }

    public ArtifactBuilder generatedText(String generatedText) {
      this.generatedText = generatedText;
      return this;
    }

    public ArtifactBuilder wordCount(Integer wordCount) {
      this.wordCount = wordCount;
      return this;
    }

    public ArtifactBuilder extractedSkills(JsonNode extractedSkills) {
      this.extractedSkills = extractedSkills;
      return this;
    }

    public ArtifactBuilder createdAt(Instant createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public ArtifactBuilder updatedAt(Instant updatedAt) {
      this.updatedAt = updatedAt;
      return this;
    }

    public Artifact build() {
      return new Artifact(
          id, job, contentType, generatedText, wordCount, extractedSkills, createdAt, updatedAt);
    }
  }
}
