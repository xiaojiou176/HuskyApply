package com.huskyapply.gateway.repository;

import com.huskyapply.gateway.model.Template;
import com.huskyapply.gateway.model.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for Template entities.
 *
 * <p>Provides data access methods for cover letter templates with user-specific queries and
 * category filtering.
 */
@Repository
public interface TemplateRepository extends JpaRepository<Template, UUID> {

  /** Find all templates for a specific user. */
  List<Template> findByUserOrderByUpdatedAtDesc(User user);

  /** Find templates by user and category. */
  List<Template> findByUserAndCategoryOrderByUpdatedAtDesc(User user, String category);

  /** Find user's default template. */
  Optional<Template> findByUserAndIsDefaultTrue(User user);

  /** Find templates by user with pagination. */
  Page<Template> findByUserOrderByUpdatedAtDesc(User user, Pageable pageable);

  /** Find templates by user and category with pagination. */
  Page<Template> findByUserAndCategoryOrderByUpdatedAtDesc(
      User user, String category, Pageable pageable);

  /** Find most popular templates for a user (by usage count). */
  List<Template> findTop5ByUserOrderByUsageCountDescUpdatedAtDesc(User user);

  /** Find all distinct categories for a user's templates. */
  @Query(
      "SELECT DISTINCT t.category FROM Template t WHERE t.user = :user AND t.category IS NOT NULL ORDER BY t.category")
  List<String> findDistinctCategoriesByUser(@Param("user") User user);

  /** Count templates by user. */
  long countByUser(User user);

  /** Count templates by user and category. */
  long countByUserAndCategory(User user, String category);

  /** Check if user has a template with given name. */
  boolean existsByUserAndName(User user, String name);

  /** Find template by user and name. */
  Optional<Template> findByUserAndName(User user, String name);
}
