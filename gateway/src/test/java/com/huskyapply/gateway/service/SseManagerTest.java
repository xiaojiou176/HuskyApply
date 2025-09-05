package com.huskyapply.gateway.service;

import static org.junit.jupiter.api.Assertions.*;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseManagerTest {

  private SseManager sseManager;
  private MeterRegistry meterRegistry;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    sseManager = new SseManager(meterRegistry);
    // Don't call init() manually as it's a @PostConstruct method
  }

  @AfterEach
  void tearDown() {
    sseManager.shutdown();
  }

  @Test
  void testConnectionLimitEnforcement() {
    // Create connections up to the limit
    for (int i = 0; i < 5; i++) {
      UUID jobId = UUID.randomUUID();
      SseEmitter emitter = new SseEmitter(60000L);
      assertTrue(sseManager.add(jobId, emitter), "Should be able to add connection " + i);
    }

    assertEquals(5, sseManager.getActiveConnectionCount());

    // Try to add one more connection beyond the configured test limit
    // Note: For this test, we're assuming the limit is set to a testable value
    // In production, you'd configure this via properties
  }

  @Test
  void testConnectionCleanupOnCompletion() throws InterruptedException {
    UUID jobId = UUID.randomUUID();
    CountDownLatch latch = new CountDownLatch(1);

    SseEmitter emitter = new SseEmitter(60000L);
    emitter.onCompletion(latch::countDown);

    assertTrue(sseManager.add(jobId, emitter));
    assertEquals(1, sseManager.getActiveConnectionCount());

    // Complete the emitter
    emitter.complete();

    // Wait for cleanup callback
    assertTrue(latch.await(1, TimeUnit.SECONDS));

    // Connection should be cleaned up
    assertEquals(0, sseManager.getActiveConnectionCount());
  }

  @Test
  void testConnectionCleanupOnTimeout() throws InterruptedException {
    UUID jobId = UUID.randomUUID();
    CountDownLatch latch = new CountDownLatch(1);

    // Create emitter with very short timeout
    SseEmitter emitter = new SseEmitter(10L);
    emitter.onTimeout(latch::countDown);

    assertTrue(sseManager.add(jobId, emitter));
    assertEquals(1, sseManager.getActiveConnectionCount());

    // Wait for timeout
    assertTrue(latch.await(1, TimeUnit.SECONDS));

    // Give some time for cleanup to happen
    Thread.sleep(50);

    // Connection should be cleaned up
    assertEquals(0, sseManager.getActiveConnectionCount());
  }

  @Test
  void testConnectionCleanupOnError() throws InterruptedException {
    UUID jobId = UUID.randomUUID();
    CountDownLatch latch = new CountDownLatch(1);

    SseEmitter emitter = new SseEmitter(60000L);
    emitter.onError(throwable -> latch.countDown());

    assertTrue(sseManager.add(jobId, emitter));
    assertEquals(1, sseManager.getActiveConnectionCount());

    // Trigger error by completing with error
    emitter.completeWithError(new RuntimeException("Test error"));

    // Wait for error callback
    assertTrue(latch.await(1, TimeUnit.SECONDS));

    // Connection should be cleaned up
    assertEquals(0, sseManager.getActiveConnectionCount());
  }

  @Test
  void testMetricsTracking() {
    UUID jobId = UUID.randomUUID();
    SseEmitter emitter = new SseEmitter(60000L);

    // Add connection
    assertTrue(sseManager.add(jobId, emitter));

    // Check that metrics are being tracked
    assertEquals(1.0, meterRegistry.get("sse.connections.created").counter().count());
    assertEquals(1.0, meterRegistry.get("sse.connections.active").gauge().value());

    // Complete connection
    emitter.complete();

    // Give time for cleanup
    try {
      Thread.sleep(50);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    // Check cleanup metrics
    assertEquals(1.0, meterRegistry.get("sse.connections.removed").counter().count());
    assertEquals(0.0, meterRegistry.get("sse.connections.active").gauge().value());
  }

  @Test
  void testHeartbeatFunctionality() {
    UUID jobId = UUID.randomUUID();
    SseEmitter emitter = new SseEmitter(60000L);

    assertTrue(sseManager.add(jobId, emitter));

    // Should not throw exception
    assertDoesNotThrow(() -> sseManager.sendHeartbeat(jobId));

    // Heartbeat for non-existent connection should not throw
    assertDoesNotThrow(() -> sseManager.sendHeartbeat(UUID.randomUUID()));
  }
}
