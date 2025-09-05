package com.huskyapply.gateway.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("artifacts")
public class Artifact {

  @Id private UUID id;

  @Column("job_id")
  private UUID jobId;

  @Column("content_type")
  private String contentType;

  @Column("generated_text")
  private String generatedText;

  @Column("word_count")
  private Integer wordCount;

  @Column("extracted_skills")
  private JsonNode extractedSkills;

  @Column("created_at")
  private Instant createdAt;

  @Column("updated_at")
  private Instant updatedAt;

  public Artifact() {}

  public Artifact(
      UUID id,
      UUID jobId,
      String contentType,
      String generatedText,
      Integer wordCount,
      JsonNode extractedSkills,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.jobId = jobId;
    this.contentType = contentType;
    this.generatedText = generatedText;
    this.wordCount = wordCount;
    this.extractedSkills = extractedSkills;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
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

  public void setJobId(UUID jobId) {
    this.jobId = jobId;
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
    private UUID jobId;
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

    public ArtifactBuilder jobId(UUID jobId) {
      this.jobId = jobId;
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
          id, jobId, contentType, generatedText, wordCount, extractedSkills, createdAt, updatedAt);
    }
  }
}
