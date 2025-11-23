package com.huskyapply.gateway.repository;

import com.huskyapply.gateway.model.Artifact;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ArtifactRepository extends JpaRepository<Artifact, UUID> {

  /**
   * Finds an artifact by the associated job ID.
   *
   * @param jobId the UUID of the job
   * @return Optional containing the artifact if found, empty otherwise
   */
  Optional<Artifact> findByJobId(UUID jobId);
}
