"""
Enhanced Streaming Configuration

This module provides configuration settings for the advanced streaming capabilities
including WebSocket support, compression, adaptive optimization, and performance tuning.
"""

import os
from dataclasses import dataclass
from typing import Dict, Any, Optional
from enum import Enum


class StreamingMode(Enum):
    """Streaming modes for different performance characteristics."""
    DISABLED = "disabled"
    BASIC = "basic"                    # Simple token-by-token streaming
    ADAPTIVE = "adaptive"              # Intelligent adaptive streaming (default)
    HIGH_THROUGHPUT = "high_throughput"  # Optimized for maximum throughput
    LOW_LATENCY = "low_latency"        # Optimized for minimum latency
    BALANCED = "balanced"              # Balanced throughput and latency


@dataclass
class WebSocketConfig:
    """WebSocket-specific configuration."""
    enabled: bool = True
    max_connections: int = 1000
    connection_timeout_seconds: int = 300  # 5 minutes
    ping_interval_seconds: int = 30
    pong_timeout_seconds: int = 10
    max_message_size_bytes: int = 1024 * 1024  # 1MB
    compression_enabled: bool = True
    heartbeat_enabled: bool = True


@dataclass
class CompressionConfig:
    """Compression configuration for streaming data."""
    enabled: bool = True
    threshold_bytes: int = 500  # Compress if data > 500 bytes
    compression_level: int = 6  # gzip compression level (1-9)
    min_compression_ratio: float = 0.8  # Only use if saves at least 20%
    algorithms: list = None  # Available: ['gzip', 'deflate', 'brotli']
    
    def __post_init__(self):
        if self.algorithms is None:
            self.algorithms = ['gzip']


@dataclass 
class AdaptiveConfig:
    """Adaptive optimization configuration."""
    enabled: bool = True
    learning_rate: float = 0.1
    history_window_size: int = 100
    performance_sample_size: int = 20
    latency_target_ms: int = 100
    quality_threshold: float = 0.8
    adaptation_interval_seconds: int = 30
    auto_tuning_enabled: bool = True


@dataclass
class CircuitBreakerConfig:
    """Circuit breaker configuration for fault tolerance."""
    enabled: bool = True
    failure_threshold: int = 5
    recovery_timeout_seconds: int = 30
    half_open_max_attempts: int = 3
    health_check_interval_seconds: int = 10
    failure_rate_threshold: float = 0.5  # 50% failure rate


@dataclass
class PerformanceConfig:
    """Performance tuning configuration."""
    target_first_chunk_ms: int = 500  # Sub-500ms first token
    max_concurrent_streams: int = 1000
    chunk_buffer_size: int = 1024
    thread_pool_size: int = 10
    max_memory_usage_mb: int = 512
    garbage_collection_interval_seconds: int = 60
    metrics_collection_enabled: bool = True
    detailed_timing_enabled: bool = False  # Detailed timing (performance impact)


@dataclass
class MonitoringConfig:
    """Monitoring and observability configuration."""
    prometheus_metrics_enabled: bool = True
    prometheus_port: int = 9090
    health_check_endpoint: str = "/health"
    metrics_endpoint: str = "/metrics" 
    streaming_analytics_enabled: bool = True
    performance_logging_enabled: bool = True
    debug_logging_enabled: bool = False
    trace_sampling_rate: float = 0.1  # 10% of requests


@dataclass
class EnhancedStreamingConfig:
    """Complete enhanced streaming configuration."""
    
    # Core streaming settings
    mode: StreamingMode = StreamingMode.ADAPTIVE
    enabled: bool = True
    
    # Component configurations
    websocket: WebSocketConfig = None
    compression: CompressionConfig = None  
    adaptive: AdaptiveConfig = None
    circuit_breaker: CircuitBreakerConfig = None
    performance: PerformanceConfig = None
    monitoring: MonitoringConfig = None
    
    # Legacy compatibility settings
    chunk_size: int = 50
    min_chunk_delay_ms: int = 50
    max_chunk_delay_ms: int = 200
    partial_threshold: int = 100
    quality_threshold: float = 0.8
    enable_gateway_streaming: bool = True
    enable_fallback: bool = True
    buffer_size: int = 1024
    
    def __post_init__(self):
        """Initialize sub-configurations if not provided."""
        if self.websocket is None:
            self.websocket = WebSocketConfig()
        if self.compression is None:
            self.compression = CompressionConfig()
        if self.adaptive is None:
            self.adaptive = AdaptiveConfig()
        if self.circuit_breaker is None:
            self.circuit_breaker = CircuitBreakerConfig()
        if self.performance is None:
            self.performance = PerformanceConfig()
        if self.monitoring is None:
            self.monitoring = MonitoringConfig()
    
    @classmethod
    def from_environment(cls) -> 'EnhancedStreamingConfig':
        """Create configuration from environment variables."""
        
        # Parse streaming mode
        mode_str = os.getenv('STREAMING_MODE', 'adaptive').lower()
        try:
            mode = StreamingMode(mode_str)
        except ValueError:
            mode = StreamingMode.ADAPTIVE
        
        # Core settings
        enabled = os.getenv('STREAMING_ENABLED', 'true').lower() == 'true'
        
        # WebSocket configuration
        websocket_config = WebSocketConfig(
            enabled=os.getenv('WEBSOCKET_ENABLED', 'true').lower() == 'true',
            max_connections=int(os.getenv('WEBSOCKET_MAX_CONNECTIONS', '1000')),
            connection_timeout_seconds=int(os.getenv('WEBSOCKET_TIMEOUT_SECONDS', '300')),
            ping_interval_seconds=int(os.getenv('WEBSOCKET_PING_INTERVAL', '30')),
            compression_enabled=os.getenv('WEBSOCKET_COMPRESSION', 'true').lower() == 'true'
        )
        
        # Compression configuration  
        compression_config = CompressionConfig(
            enabled=os.getenv('COMPRESSION_ENABLED', 'true').lower() == 'true',
            threshold_bytes=int(os.getenv('COMPRESSION_THRESHOLD_BYTES', '500')),
            compression_level=int(os.getenv('COMPRESSION_LEVEL', '6')),
            min_compression_ratio=float(os.getenv('MIN_COMPRESSION_RATIO', '0.8'))
        )
        
        # Adaptive configuration
        adaptive_config = AdaptiveConfig(
            enabled=os.getenv('ADAPTIVE_STREAMING_ENABLED', 'true').lower() == 'true',
            learning_rate=float(os.getenv('ADAPTIVE_LEARNING_RATE', '0.1')),
            latency_target_ms=int(os.getenv('LATENCY_TARGET_MS', '100')),
            quality_threshold=float(os.getenv('ADAPTIVE_QUALITY_THRESHOLD', '0.8'))
        )
        
        # Circuit breaker configuration
        circuit_breaker_config = CircuitBreakerConfig(
            enabled=os.getenv('CIRCUIT_BREAKER_ENABLED', 'true').lower() == 'true',
            failure_threshold=int(os.getenv('CIRCUIT_BREAKER_FAILURE_THRESHOLD', '5')),
            recovery_timeout_seconds=int(os.getenv('CIRCUIT_BREAKER_RECOVERY_TIMEOUT', '30'))
        )
        
        # Performance configuration
        performance_config = PerformanceConfig(
            target_first_chunk_ms=int(os.getenv('TARGET_FIRST_CHUNK_MS', '500')),
            max_concurrent_streams=int(os.getenv('MAX_CONCURRENT_STREAMS', '1000')),
            thread_pool_size=int(os.getenv('STREAMING_THREAD_POOL_SIZE', '10')),
            max_memory_usage_mb=int(os.getenv('STREAMING_MAX_MEMORY_MB', '512'))
        )
        
        # Monitoring configuration
        monitoring_config = MonitoringConfig(
            prometheus_metrics_enabled=os.getenv('PROMETHEUS_METRICS_ENABLED', 'true').lower() == 'true',
            prometheus_port=int(os.getenv('PROMETHEUS_PORT', '9090')),
            streaming_analytics_enabled=os.getenv('STREAMING_ANALYTICS_ENABLED', 'true').lower() == 'true',
            debug_logging_enabled=os.getenv('DEBUG_LOGGING_ENABLED', 'false').lower() == 'true'
        )
        
        return cls(
            mode=mode,
            enabled=enabled,
            websocket=websocket_config,
            compression=compression_config,
            adaptive=adaptive_config,
            circuit_breaker=circuit_breaker_config,
            performance=performance_config,
            monitoring=monitoring_config
        )
    
    @classmethod
    def for_development(cls) -> 'EnhancedStreamingConfig':
        """Create development-friendly configuration."""
        return cls(
            mode=StreamingMode.ADAPTIVE,
            websocket=WebSocketConfig(
                max_connections=100,
                compression_enabled=False  # Disable for easier debugging
            ),
            compression=CompressionConfig(
                enabled=False  # Disable for easier debugging
            ),
            adaptive=AdaptiveConfig(
                enabled=True,
                learning_rate=0.2  # Faster learning in development
            ),
            performance=PerformanceConfig(
                target_first_chunk_ms=200,  # Faster target for development
                max_concurrent_streams=50,
                thread_pool_size=5
            ),
            monitoring=MonitoringConfig(
                debug_logging_enabled=True,
                detailed_timing_enabled=True
            )
        )
    
    @classmethod  
    def for_production(cls) -> 'EnhancedStreamingConfig':
        """Create production-optimized configuration."""
        return cls(
            mode=StreamingMode.BALANCED,
            websocket=WebSocketConfig(
                max_connections=5000,
                compression_enabled=True
            ),
            compression=CompressionConfig(
                enabled=True,
                threshold_bytes=300,  # Aggressive compression
                compression_level=6
            ),
            adaptive=AdaptiveConfig(
                enabled=True,
                learning_rate=0.05,  # Conservative learning
                auto_tuning_enabled=True
            ),
            circuit_breaker=CircuitBreakerConfig(
                enabled=True,
                failure_threshold=3,  # More sensitive
                recovery_timeout_seconds=60
            ),
            performance=PerformanceConfig(
                target_first_chunk_ms=300,  # Balanced target
                max_concurrent_streams=2000,
                thread_pool_size=20,
                max_memory_usage_mb=2048
            ),
            monitoring=MonitoringConfig(
                prometheus_metrics_enabled=True,
                streaming_analytics_enabled=True,
                debug_logging_enabled=False
            )
        )
    
    @classmethod
    def for_high_performance(cls) -> 'EnhancedStreamingConfig':
        """Create high-performance configuration for maximum throughput."""
        return cls(
            mode=StreamingMode.HIGH_THROUGHPUT,
            websocket=WebSocketConfig(
                max_connections=10000,
                compression_enabled=True,
                ping_interval_seconds=60  # Less frequent pings
            ),
            compression=CompressionConfig(
                enabled=True,
                threshold_bytes=200,
                compression_level=4  # Faster compression
            ),
            adaptive=AdaptiveConfig(
                enabled=True,
                learning_rate=0.1,
                adaptation_interval_seconds=15  # More frequent adaptation
            ),
            performance=PerformanceConfig(
                target_first_chunk_ms=100,  # Very fast first chunk
                max_concurrent_streams=5000,
                thread_pool_size=50,
                max_memory_usage_mb=4096,
                chunk_buffer_size=2048
            ),
            monitoring=MonitoringConfig(
                detailed_timing_enabled=False,  # Reduce overhead
                trace_sampling_rate=0.01  # 1% sampling
            )
        )
    
    def to_dict(self) -> Dict[str, Any]:
        """Convert configuration to dictionary."""
        return {
            'mode': self.mode.value,
            'enabled': self.enabled,
            'websocket': {
                'enabled': self.websocket.enabled,
                'max_connections': self.websocket.max_connections,
                'connection_timeout_seconds': self.websocket.connection_timeout_seconds,
                'compression_enabled': self.websocket.compression_enabled
            },
            'compression': {
                'enabled': self.compression.enabled,
                'threshold_bytes': self.compression.threshold_bytes,
                'compression_level': self.compression.compression_level
            },
            'adaptive': {
                'enabled': self.adaptive.enabled,
                'learning_rate': self.adaptive.learning_rate,
                'latency_target_ms': self.adaptive.latency_target_ms,
                'quality_threshold': self.adaptive.quality_threshold
            },
            'circuit_breaker': {
                'enabled': self.circuit_breaker.enabled,
                'failure_threshold': self.circuit_breaker.failure_threshold,
                'recovery_timeout_seconds': self.circuit_breaker.recovery_timeout_seconds
            },
            'performance': {
                'target_first_chunk_ms': self.performance.target_first_chunk_ms,
                'max_concurrent_streams': self.performance.max_concurrent_streams,
                'thread_pool_size': self.performance.thread_pool_size
            },
            'monitoring': {
                'prometheus_metrics_enabled': self.monitoring.prometheus_metrics_enabled,
                'streaming_analytics_enabled': self.monitoring.streaming_analytics_enabled,
                'debug_logging_enabled': self.monitoring.debug_logging_enabled
            }
        }
    
    def validate(self) -> bool:
        """Validate configuration settings."""
        try:
            # Validate ranges
            assert 1 <= self.performance.target_first_chunk_ms <= 5000, "Invalid first chunk target"
            assert 1 <= self.performance.max_concurrent_streams <= 50000, "Invalid concurrent streams limit"
            assert 1 <= self.websocket.max_connections <= 100000, "Invalid WebSocket connection limit"
            assert 0.0 < self.adaptive.learning_rate <= 1.0, "Invalid learning rate"
            assert 1 <= self.compression.compression_level <= 9, "Invalid compression level"
            assert 1 <= self.circuit_breaker.failure_threshold <= 100, "Invalid failure threshold"
            
            return True
            
        except AssertionError as e:
            raise ValueError(f"Configuration validation failed: {e}")


# Default configurations for different environments
DEFAULT_CONFIG = EnhancedStreamingConfig.from_environment()
DEVELOPMENT_CONFIG = EnhancedStreamingConfig.for_development()
PRODUCTION_CONFIG = EnhancedStreamingConfig.for_production()
HIGH_PERFORMANCE_CONFIG = EnhancedStreamingConfig.for_high_performance()


def get_config(environment: str = None) -> EnhancedStreamingConfig:
    """Get configuration for specified environment."""
    if environment is None:
        environment = os.getenv('ENVIRONMENT', 'development')
    
    config_map = {
        'development': DEVELOPMENT_CONFIG,
        'dev': DEVELOPMENT_CONFIG,
        'production': PRODUCTION_CONFIG,
        'prod': PRODUCTION_CONFIG,
        'high_performance': HIGH_PERFORMANCE_CONFIG,
        'perf': HIGH_PERFORMANCE_CONFIG,
        'default': DEFAULT_CONFIG
    }
    
    return config_map.get(environment.lower(), DEFAULT_CONFIG)