package com.huskyapply.gateway.repository;

import com.huskyapply.gateway.model.Artifact;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive Artifact Repository using R2DBC for non-blocking database operations
 *
 * <p>This repository handles AI-generated artifacts (cover letters, resumes, etc.) using reactive
 * streams for optimal performance.
 *
 * <p>Key features: - Non-blocking artifact retrieval - Content-based search capabilities -
 * Efficient JSONB querying for skills/metadata - Reactive batch operations
 */
@Repository
public interface ReactiveArtifactRepository extends ReactiveCrudRepository<Artifact, UUID> {

  /** Find artifact by job ID (reactive) */
  Mono<Artifact> findByJobId(UUID jobId);

  /** Find all artifacts for a specific user's jobs (reactive) */
  @Query(
      "SELECT a.* FROM artifacts a JOIN jobs j ON a.job_id = j.id WHERE j.user_id = :userId ORDER BY a.created_at DESC")
  Flux<Artifact> findByUserId(UUID userId);

  /** Find artifacts by content type (reactive) */
  Flux<Artifact> findByContentType(String contentType);

  /** Search artifacts by text content (reactive full-text search) */
  @Query(
      "SELECT * FROM artifacts WHERE to_tsvector('english', generated_text) @@ plainto_tsquery('english', :searchText)")
  Flux<Artifact> searchByText(String searchText);

  /** Find artifacts containing specific skills (JSONB query) */
  @Query("SELECT * FROM artifacts WHERE extracted_skills ?? :skill")
  Flux<Artifact> findBySkill(String skill);

  /** Find artifacts with minimum word count (reactive filtering) */
  Flux<Artifact> findByWordCountGreaterThan(Integer minWordCount);

  /** Get artifact statistics for a user (reactive aggregation) */
  @Query(
      "SELECT content_type, COUNT(*) as count, AVG(word_count) as avg_words FROM artifacts a JOIN jobs j ON a.job_id = j.id WHERE j.user_id = :userId GROUP BY content_type")
  Flux<ArtifactStats> getArtifactStatsByUser(UUID userId);

  /** Find recent artifacts within time period (reactive) */
  @Query(
      "SELECT a.* FROM artifacts a JOIN jobs j ON a.job_id = j.id WHERE j.user_id = :userId AND a.created_at >= NOW() - INTERVAL ':days days' ORDER BY a.created_at DESC")
  Flux<Artifact> findRecentArtifacts(UUID userId, int days);

  /** Count artifacts by content type for a user (reactive counting) */
  @Query(
      "SELECT COUNT(*) FROM artifacts a JOIN jobs j ON a.job_id = j.id WHERE j.user_id = :userId AND a.content_type = :contentType")
  Mono<Long> countByUserIdAndContentType(UUID userId, String contentType);

  /** Delete artifacts older than specified days (reactive cleanup) */
  @Query("DELETE FROM artifacts WHERE created_at < NOW() - INTERVAL ':days days'")
  Mono<Long> deleteOldArtifacts(int days);

  /** Find artifacts similar to given skills (JSONB similarity) */
  @Query(
      "SELECT *, similarity(extracted_skills::text, :skillsJson::text) as similarity_score "
          + "FROM artifacts WHERE similarity(extracted_skills::text, :skillsJson::text) > 0.3 "
          + "ORDER BY similarity_score DESC")
  Flux<Artifact> findSimilarArtifacts(String skillsJson);

  /** Update artifact content atomically (reactive) */
  @Query(
      "UPDATE artifacts SET generated_text = :text, word_count = :wordCount, updated_at = NOW() WHERE id = :artifactId")
  Mono<Void> updateContent(UUID artifactId, String text, Integer wordCount);

  /** Batch insert artifacts (reactive bulk operation) */
  @Query(
      "INSERT INTO artifacts (id, job_id, content_type, generated_text, word_count, extracted_skills, created_at, updated_at) "
          + "VALUES (:id, :jobId, :contentType, :generatedText, :wordCount, :extractedSkills, NOW(), NOW())")
  Mono<Void> insertArtifact(
      UUID id,
      UUID jobId,
      String contentType,
      String generatedText,
      Integer wordCount,
      String extractedSkills);

  /** Interface for artifact statistics aggregation */
  interface ArtifactStats {
    String getContentType();

    Long getCount();

    Double getAvgWords();
  }
}
