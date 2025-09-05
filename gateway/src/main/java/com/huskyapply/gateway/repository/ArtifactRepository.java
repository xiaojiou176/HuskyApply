package com.huskyapply.gateway.repository;

import com.huskyapply.gateway.model.Artifact;
import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface ArtifactRepository extends ReactiveCrudRepository<Artifact, UUID> {

  /**
   * Finds an artifact by the associated job ID.
   *
   * @param jobId the UUID of the job
   * @return Mono containing the artifact if found, empty otherwise
   */
  Mono<Artifact> findByJobId(UUID jobId);
}
