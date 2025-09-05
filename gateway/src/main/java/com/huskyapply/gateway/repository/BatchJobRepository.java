package com.huskyapply.gateway.repository;

import com.huskyapply.gateway.model.BatchJob;
import com.huskyapply.gateway.model.User;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BatchJobRepository extends JpaRepository<BatchJob, UUID> {

  List<BatchJob> findByUserOrderByCreatedAtDesc(User user);

  Page<BatchJob> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

  List<BatchJob> findByUserAndStatusOrderByCreatedAtDesc(User user, String status);

  Page<BatchJob> findByUserAndStatusOrderByCreatedAtDesc(
      User user, String status, Pageable pageable);

  List<BatchJob> findTop10ByUserOrderByCreatedAtDesc(User user);

  List<BatchJob> findByUserAndCreatedAtAfterOrderByCreatedAtDesc(User user, Instant after);

  long countByUser(User user);

  long countByUserAndStatus(User user, String status);

  long countByUserAndCreatedAtAfter(User user, Instant after);

  @Query(
      "SELECT b FROM BatchJob b WHERE b.user = :user AND "
          + "(LOWER(b.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR "
          + "LOWER(b.description) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) "
          + "ORDER BY b.createdAt DESC")
  List<BatchJob> searchByUserAndTerm(
      @Param("user") User user, @Param("searchTerm") String searchTerm);

  @Query(
      "SELECT b FROM BatchJob b WHERE b.status IN ('PENDING', 'PROCESSING') ORDER BY b.createdAt ASC")
  List<BatchJob> findActiveBatchJobs();

  @Query("SELECT COUNT(j) FROM Job j WHERE j.batchJob.id = :batchJobId")
  long countJobsInBatch(@Param("batchJobId") UUID batchJobId);

  @Query("SELECT COUNT(j) FROM Job j WHERE j.batchJob.id = :batchJobId AND j.status = :status")
  long countJobsInBatchByStatus(
      @Param("batchJobId") UUID batchJobId, @Param("status") String status);
}
