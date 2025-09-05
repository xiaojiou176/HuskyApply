package com.huskyapply.gateway.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark repository methods that should be routed to read replicas. This enables
 * automatic read-write splitting by routing queries to read replicas for improved performance and
 * load distribution.
 *
 * <p>Usage: - Apply to repository methods that only read data - Will automatically route to read
 * replicas with load balancing - Falls back to master database if replicas are unavailable
 *
 * <p>Example: {@code @ReadOnly public Flux<Job> findByUserId(UUID userId) { // This will be
 * executed on read replica } }
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ReadOnly {

  /**
   * Whether to allow fallback to master database if all read replicas are unavailable. Default is
   * true for high availability.
   */
  boolean allowMasterFallback() default true;

  /**
   * Maximum staleness tolerance in seconds for read operations. If replication lag exceeds this
   * threshold, query will be routed to master. Default is 10 seconds.
   */
  long maxStalenessTolerance() default 10;

  /**
   * Priority level for read operations (HIGH, NORMAL, LOW). Higher priority queries get preference
   * during load balancing. Default is NORMAL.
   */
  Priority priority() default Priority.NORMAL;

  enum Priority {
    HIGH,
    NORMAL,
    LOW
  }
}
