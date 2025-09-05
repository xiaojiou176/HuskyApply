package com.huskyapply.gateway.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark repository methods that require write operations and must be routed to the
 * master database. This ensures data consistency and immediate availability of written data.
 *
 * <p>Usage: - Apply to repository methods that modify data (INSERT, UPDATE, DELETE) - Will
 * automatically route to master database - Ensures ACID compliance and transaction integrity
 *
 * <p>Example: {@code @WriteOnly public Mono<Job> save(Job job) { // This will be executed on master
 * database } }
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface WriteOnly {

  /**
   * Transaction isolation level for write operations. Default is READ_COMMITTED for performance
   * while maintaining consistency.
   */
  TransactionIsolation isolation() default TransactionIsolation.READ_COMMITTED;

  /** Maximum timeout in seconds for write operations. Default is 30 seconds. */
  long timeoutSeconds() default 30;

  /**
   * Whether to force immediate replication to read replicas. When true, write operation waits for
   * synchronous replication. Default is false for performance.
   */
  boolean forceSyncReplication() default false;

  enum TransactionIsolation {
    READ_UNCOMMITTED,
    READ_COMMITTED,
    REPEATABLE_READ,
    SERIALIZABLE
  }
}
