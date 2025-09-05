package com.huskyapply.gateway.repository;

import com.huskyapply.gateway.model.User;
import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Repository interface for User entity operations.
 *
 * <p>Provides standard CRUD operations through ReactiveCrudRepository and custom query methods for
 * user management and authentication workflows.
 */
@Repository
public interface UserRepository extends ReactiveCrudRepository<User, UUID> {

  /**
   * Finds a user by their email address.
   *
   * <p>This method is essential for authentication as it allows the system to look up users by
   * their email (which serves as the username).
   *
   * @param email the email address to search for
   * @return Mono containing the user if found, empty otherwise
   */
  Mono<User> findByEmail(String email);
}
