"""
Optimization Configuration

This module provides configuration management for all AI optimization features
including caching, model selection, streaming, and cost management settings.

Configuration is loaded from environment variables with sensible defaults.
"""

import os
from dataclasses import dataclass
from typing import Dict, List, Optional

from semantic_cache import CacheConfig
from ai_optimizer import OptimizationProfile
from streaming_handler import StreamingConfig, StreamingMode


@dataclass
class AIOptimizationConfig:
    """Comprehensive configuration for AI optimization features."""
    
    # Caching Configuration
    enable_semantic_caching: bool = True
    cache_similarity_threshold: float = 0.85
    cache_max_size: int = 10000
    cache_ttl_seconds: int = 7 * 24 * 3600  # 7 days
    cache_redis_host: str = "localhost"
    cache_redis_port: int = 6379
    cache_redis_db: int = 1
    cache_redis_password: Optional[str] = None
    cache_warmup_enabled: bool = True
    
    # Model Configuration
    preferred_model_provider: str = "openai"
    fallback_model_provider: str = "openai" 
    enable_model_preloading: bool = True
    circuit_breaker_threshold: int = 5
    circuit_breaker_timeout: int = 60
    
    # Streaming Configuration
    enable_streaming: bool = True
    streaming_mode: str = "adaptive"  # "disabled", "partial", "progressive", "adaptive"
    streaming_chunk_size: int = 50
    streaming_min_delay_ms: int = 50
    streaming_max_delay_ms: int = 200
    streaming_partial_threshold: int = 100
    
    # Cost Optimization
    default_optimization_profile: str = "balanced"
    max_cost_per_request: float = 1.0
    cost_tracking_enabled: bool = True
    budget_alerts_enabled: bool = True
    daily_budget_limit_usd: float = 100.0
    
    # Quality Settings
    min_quality_threshold: float = 0.75
    enable_quality_monitoring: bool = True
    quality_sampling_rate: float = 0.1
    
    # Performance Settings
    request_timeout_seconds: int = 30
    max_concurrent_requests: int = 50
    enable_batch_processing: bool = False
    batch_size: int = 10
    
    # Analytics and Monitoring
    enable_detailed_analytics: bool = True
    metrics_retention_days: int = 30
    enable_performance_alerts: bool = True
    alert_cost_threshold_usd: float = 10.0
    alert_latency_threshold_ms: int = 5000
    
    @classmethod
    def from_environment(cls) -> 'AIOptimizationConfig':
        """Create configuration from environment variables."""
        return cls(
            # Caching
            enable_semantic_caching=_get_bool_env("AI_CACHE_ENABLED", True),
            cache_similarity_threshold=_get_float_env("AI_CACHE_SIMILARITY_THRESHOLD", 0.85),
            cache_max_size=_get_int_env("AI_CACHE_MAX_SIZE", 10000),
            cache_ttl_seconds=_get_int_env("AI_CACHE_TTL_SECONDS", 7 * 24 * 3600),
            cache_redis_host=os.getenv("AI_CACHE_REDIS_HOST", "localhost"),
            cache_redis_port=_get_int_env("AI_CACHE_REDIS_PORT", 6379),
            cache_redis_db=_get_int_env("AI_CACHE_REDIS_DB", 1),
            cache_redis_password=os.getenv("AI_CACHE_REDIS_PASSWORD"),
            cache_warmup_enabled=_get_bool_env("AI_CACHE_WARMUP_ENABLED", True),
            
            # Models
            preferred_model_provider=os.getenv("AI_PREFERRED_PROVIDER", "openai"),
            fallback_model_provider=os.getenv("AI_FALLBACK_PROVIDER", "openai"),
            enable_model_preloading=_get_bool_env("AI_PRELOAD_MODELS", True),
            circuit_breaker_threshold=_get_int_env("AI_CIRCUIT_BREAKER_THRESHOLD", 5),
            circuit_breaker_timeout=_get_int_env("AI_CIRCUIT_BREAKER_TIMEOUT", 60),
            
            # Streaming
            enable_streaming=_get_bool_env("AI_STREAMING_ENABLED", True),
            streaming_mode=os.getenv("AI_STREAMING_MODE", "adaptive"),
            streaming_chunk_size=_get_int_env("AI_STREAMING_CHUNK_SIZE", 50),
            streaming_min_delay_ms=_get_int_env("AI_STREAMING_MIN_DELAY_MS", 50),
            streaming_max_delay_ms=_get_int_env("AI_STREAMING_MAX_DELAY_MS", 200),
            streaming_partial_threshold=_get_int_env("AI_STREAMING_PARTIAL_THRESHOLD", 100),
            
            # Cost
            default_optimization_profile=os.getenv("AI_OPTIMIZATION_PROFILE", "balanced"),
            max_cost_per_request=_get_float_env("AI_MAX_COST_PER_REQUEST", 1.0),
            cost_tracking_enabled=_get_bool_env("AI_COST_TRACKING_ENABLED", True),
            budget_alerts_enabled=_get_bool_env("AI_BUDGET_ALERTS_ENABLED", True),
            daily_budget_limit_usd=_get_float_env("AI_DAILY_BUDGET_LIMIT", 100.0),
            
            # Quality
            min_quality_threshold=_get_float_env("AI_MIN_QUALITY_THRESHOLD", 0.75),
            enable_quality_monitoring=_get_bool_env("AI_QUALITY_MONITORING_ENABLED", True),
            quality_sampling_rate=_get_float_env("AI_QUALITY_SAMPLING_RATE", 0.1),
            
            # Performance
            request_timeout_seconds=_get_int_env("AI_REQUEST_TIMEOUT", 30),
            max_concurrent_requests=_get_int_env("AI_MAX_CONCURRENT_REQUESTS", 50),
            enable_batch_processing=_get_bool_env("AI_BATCH_PROCESSING_ENABLED", False),
            batch_size=_get_int_env("AI_BATCH_SIZE", 10),
            
            # Analytics
            enable_detailed_analytics=_get_bool_env("AI_DETAILED_ANALYTICS_ENABLED", True),
            metrics_retention_days=_get_int_env("AI_METRICS_RETENTION_DAYS", 30),
            enable_performance_alerts=_get_bool_env("AI_PERFORMANCE_ALERTS_ENABLED", True),
            alert_cost_threshold_usd=_get_float_env("AI_ALERT_COST_THRESHOLD", 10.0),
            alert_latency_threshold_ms=_get_int_env("AI_ALERT_LATENCY_THRESHOLD_MS", 5000)
        )
    
    def to_cache_config(self) -> CacheConfig:
        """Convert to cache configuration."""
        return CacheConfig(
            similarity_threshold=self.cache_similarity_threshold,
            max_cache_size=self.cache_max_size,
            ttl_seconds=self.cache_ttl_seconds,
            redis_host=self.cache_redis_host,
            redis_port=self.cache_redis_port,
            redis_db=self.cache_redis_db,
            redis_password=self.cache_redis_password,
            cache_warming_enabled=self.cache_warmup_enabled,
            min_quality_score=self.min_quality_threshold
        )
    
    def to_streaming_config(self) -> StreamingConfig:
        """Convert to streaming configuration."""
        mode_map = {
            "disabled": StreamingMode.DISABLED,
            "partial": StreamingMode.PARTIAL,
            "progressive": StreamingMode.PROGRESSIVE,
            "adaptive": StreamingMode.ADAPTIVE
        }
        
        return StreamingConfig(
            mode=mode_map.get(self.streaming_mode, StreamingMode.ADAPTIVE),
            chunk_size=self.streaming_chunk_size,
            min_chunk_delay_ms=self.streaming_min_delay_ms,
            max_chunk_delay_ms=self.streaming_max_delay_ms,
            partial_threshold=self.streaming_partial_threshold,
            quality_threshold=self.min_quality_threshold,
            enable_gateway_streaming=True,
            enable_fallback=True
        )
    
    def get_optimization_profiles(self) -> Dict[str, OptimizationProfile]:
        """Get optimization profiles with current settings."""
        return {
            "quality_focused": OptimizationProfile(
                name="quality_focused",
                prefer_quality=True,
                prefer_speed=False,
                prefer_cost=False,
                max_cost_per_request=self.max_cost_per_request * 2.0,
                timeout_seconds=self.request_timeout_seconds + 15,
                enable_streaming=False,
                min_quality_threshold=0.90
            ),
            "balanced": OptimizationProfile(
                name="balanced",
                prefer_quality=True,
                prefer_speed=True,
                prefer_cost=True,
                max_cost_per_request=self.max_cost_per_request,
                timeout_seconds=self.request_timeout_seconds,
                enable_streaming=self.enable_streaming,
                min_quality_threshold=self.min_quality_threshold
            ),
            "cost_optimized": OptimizationProfile(
                name="cost_optimized",
                prefer_quality=False,
                prefer_speed=False,
                prefer_cost=True,
                max_cost_per_request=self.max_cost_per_request * 0.5,
                timeout_seconds=self.request_timeout_seconds - 10,
                enable_streaming=self.enable_streaming,
                min_quality_threshold=max(0.65, self.min_quality_threshold - 0.1)
            ),
            "speed_optimized": OptimizationProfile(
                name="speed_optimized",
                prefer_quality=False,
                prefer_speed=True,
                prefer_cost=False,
                max_cost_per_request=self.max_cost_per_request * 1.5,
                timeout_seconds=self.request_timeout_seconds - 15,
                enable_streaming=True,
                min_quality_threshold=max(0.70, self.min_quality_threshold - 0.05)
            )
        }
    
    def get_model_configurations(self) -> Dict[str, Dict]:
        """Get model-specific configurations."""
        return {
            "openai": {
                "api_key_env": "OPENAI_API_KEY",
                "default_model": "gpt-4o",
                "fallback_model": "gpt-3.5-turbo",
                "max_retries": 3,
                "timeout": self.request_timeout_seconds
            },
            "anthropic": {
                "api_key_env": "ANTHROPIC_API_KEY",
                "default_model": "claude-3-5-sonnet-20241022",
                "fallback_model": "claude-3-haiku-20240307",
                "max_retries": 3,
                "timeout": self.request_timeout_seconds
            }
        }
    
    def validate(self) -> List[str]:
        """Validate configuration and return list of issues."""
        issues = []
        
        # Validate thresholds
        if not 0.0 <= self.cache_similarity_threshold <= 1.0:
            issues.append("cache_similarity_threshold must be between 0.0 and 1.0")
        
        if not 0.0 <= self.min_quality_threshold <= 1.0:
            issues.append("min_quality_threshold must be between 0.0 and 1.0")
        
        if self.max_cost_per_request <= 0:
            issues.append("max_cost_per_request must be positive")
        
        if self.daily_budget_limit_usd <= 0:
            issues.append("daily_budget_limit_usd must be positive")
        
        # Validate streaming mode
        valid_modes = ["disabled", "partial", "progressive", "adaptive"]
        if self.streaming_mode not in valid_modes:
            issues.append(f"streaming_mode must be one of: {valid_modes}")
        
        # Validate optimization profile
        valid_profiles = ["quality_focused", "balanced", "cost_optimized", "speed_optimized"]
        if self.default_optimization_profile not in valid_profiles:
            issues.append(f"default_optimization_profile must be one of: {valid_profiles}")
        
        # Validate API keys if providers are enabled
        required_keys = []
        if self.preferred_model_provider == "openai":
            required_keys.append("OPENAI_API_KEY")
        if self.preferred_model_provider == "anthropic":
            required_keys.append("ANTHROPIC_API_KEY")
        if self.fallback_model_provider == "anthropic":
            required_keys.append("ANTHROPIC_API_KEY")
        
        for key in required_keys:
            if not os.getenv(key):
                issues.append(f"Missing required environment variable: {key}")
        
        # Validate Redis connection if caching is enabled
        if self.enable_semantic_caching:
            if not self.cache_redis_host:
                issues.append("cache_redis_host is required when caching is enabled")
            if not 1 <= self.cache_redis_port <= 65535:
                issues.append("cache_redis_port must be between 1 and 65535")
        
        return issues


def _get_bool_env(key: str, default: bool) -> bool:
    """Get boolean from environment variable."""
    value = os.getenv(key, str(default)).lower()
    return value in ("true", "1", "yes", "on")


def _get_int_env(key: str, default: int) -> int:
    """Get integer from environment variable."""
    try:
        return int(os.getenv(key, str(default)))
    except ValueError:
        return default


def _get_float_env(key: str, default: float) -> float:
    """Get float from environment variable."""
    try:
        return float(os.getenv(key, str(default)))
    except ValueError:
        return default


# Global configuration instance
_optimization_config: Optional[AIOptimizationConfig] = None


def get_optimization_config() -> AIOptimizationConfig:
    """Get the global optimization configuration instance."""
    global _optimization_config
    if _optimization_config is None:
        _optimization_config = AIOptimizationConfig.from_environment()
    return _optimization_config


def reload_config() -> AIOptimizationConfig:
    """Reload configuration from environment."""
    global _optimization_config
    _optimization_config = AIOptimizationConfig.from_environment()
    return _optimization_config


def validate_config() -> List[str]:
    """Validate current configuration."""
    config = get_optimization_config()
    return config.validate()