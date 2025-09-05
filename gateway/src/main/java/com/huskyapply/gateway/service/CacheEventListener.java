package com.huskyapply.gateway.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Cache Event Listener
 *
 * <p>Monitors cache events for debugging and performance analysis.
 */
@Service
public class CacheEventListener {

  private static final Logger logger = LoggerFactory.getLogger(CacheEventListener.class);

  public void onCacheHit(String cacheName, String key) {
    logger.trace("Cache HIT: {}/{}", cacheName, key);
  }

  public void onCacheMiss(String cacheName, String key) {
    logger.trace("Cache MISS: {}/{}", cacheName, key);
  }

  public void onCacheEviction(String cacheName, String key) {
    logger.debug("Cache EVICTION: {}/{}", cacheName, key);
  }
}
