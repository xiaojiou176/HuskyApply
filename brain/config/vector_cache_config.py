"""
Vector Database Cache Configuration for Production Deployment

This configuration enables the vector database semantic caching system
for maximum cost optimization and performance improvements.
"""

import os
from pathlib import Path
from semantic_cache import CacheConfig
from vector_database import VectorDBConfig

# Production vector database configuration
VECTOR_DB_CONFIG = VectorDBConfig(
    db_path=os.getenv("VECTOR_DB_PATH", "./data/production_vector_cache"),
    collection_name=os.getenv("VECTOR_COLLECTION_NAME", "huskyapply_semantic_cache"),
    embedding_model=os.getenv("EMBEDDING_MODEL", "all-MiniLM-L6-v2"),
    similarity_threshold=float(os.getenv("SIMILARITY_THRESHOLD", "0.85")),
    max_results=int(os.getenv("MAX_SEARCH_RESULTS", "10")),
    enable_persistence=os.getenv("ENABLE_VECTOR_PERSISTENCE", "true").lower() == "true",
    enable_clustering=os.getenv("ENABLE_VECTOR_CLUSTERING", "true").lower() == "true",
    backup_interval_hours=int(os.getenv("VECTOR_BACKUP_INTERVAL", "24"))
)

# Enhanced semantic cache configuration with vector database
ENHANCED_CACHE_CONFIG = CacheConfig(
    similarity_threshold=float(os.getenv("CACHE_SIMILARITY_THRESHOLD", "0.85")),
    max_cache_size=int(os.getenv("MAX_CACHE_SIZE", "50000")),
    ttl_seconds=int(os.getenv("CACHE_TTL_SECONDS", str(30 * 24 * 3600))),  # 30 days
    embedding_model=os.getenv("EMBEDDING_MODEL", "all-MiniLM-L6-v2"),
    redis_host=os.getenv("REDIS_HOST", "localhost"),
    redis_port=int(os.getenv("REDIS_PORT", "6379")),
    redis_db=int(os.getenv("REDIS_CACHE_DB", "1")),
    redis_password=os.getenv("REDIS_PASSWORD"),
    cache_warming_enabled=os.getenv("ENABLE_CACHE_WARMING", "true").lower() == "true",
    min_quality_score=float(os.getenv("MIN_QUALITY_SCORE", "0.7")),
    company_partition_enabled=os.getenv("ENABLE_COMPANY_PARTITIONS", "true").lower() == "true",
    enable_cache_analytics=os.getenv("ENABLE_CACHE_ANALYTICS", "true").lower() == "true",
    # Vector database integration
    enable_vector_db=os.getenv("ENABLE_VECTOR_DATABASE", "true").lower() == "true",
    vector_db_path=os.getenv("VECTOR_DB_PATH", "./data/production_vector_cache")
)

# Cache warming configuration for popular job types
CACHE_WARMING_COMPANIES = [
    "Google", "Microsoft", "Amazon", "Apple", "Meta", "Netflix", "Tesla", 
    "Uber", "Airbnb", "Stripe", "Salesforce", "Adobe", "IBM", "Oracle",
    "NVIDIA", "Intel", "Cisco", "VMware", "ServiceNow", "Snowflake",
    "Databricks", "MongoDB", "Atlassian", "Slack", "Zoom", "DocuSign",
    "CrowdStrike", "Okta", "Twilio", "Square", "PayPal", "eBay",
    "LinkedIn", "Twitter", "Snap", "Pinterest", "Reddit", "Discord",
    "Spotify", "TikTok", "ByteDance", "Shopify", "Coinbase", "Robinhood"
]

CACHE_WARMING_ROLES = [
    "Software Engineer", "Senior Software Engineer", "Staff Software Engineer",
    "Principal Software Engineer", "Engineering Manager", "Senior Engineering Manager",
    "Data Scientist", "Senior Data Scientist", "Principal Data Scientist",
    "Machine Learning Engineer", "ML Engineer", "AI Engineer",
    "Product Manager", "Senior Product Manager", "Principal Product Manager",
    "DevOps Engineer", "Site Reliability Engineer", "Cloud Engineer",
    "Full Stack Developer", "Frontend Developer", "Backend Developer",
    "Mobile Developer", "iOS Developer", "Android Developer",
    "Security Engineer", "Cybersecurity Analyst", "InfoSec Engineer",
    "Data Engineer", "Analytics Engineer", "Business Intelligence Engineer",
    "Solutions Architect", "Cloud Architect", "Technical Architect",
    "QA Engineer", "Test Engineer", "Quality Assurance Engineer",
    "UX Designer", "UI Designer", "Product Designer",
    "Marketing Manager", "Growth Manager", "Digital Marketing Manager",
    "Sales Engineer", "Technical Sales", "Customer Success Manager",
    "Business Analyst", "Data Analyst", "Research Scientist"
]

# Performance optimization settings
PERFORMANCE_CONFIG = {
    "max_concurrent_requests": int(os.getenv("MAX_CONCURRENT_CACHE_REQUESTS", "100")),
    "cache_request_timeout": int(os.getenv("CACHE_REQUEST_TIMEOUT", "30")),
    "vector_search_batch_size": int(os.getenv("VECTOR_SEARCH_BATCH_SIZE", "50")),
    "embedding_batch_size": int(os.getenv("EMBEDDING_BATCH_SIZE", "32")),
    "enable_async_caching": os.getenv("ENABLE_ASYNC_CACHING", "true").lower() == "true",
    "cache_preload_threshold": float(os.getenv("CACHE_PRELOAD_THRESHOLD", "0.8")),
}

# Cost optimization targets
COST_OPTIMIZATION_TARGETS = {
    "target_hit_ratio": float(os.getenv("TARGET_CACHE_HIT_RATIO", "0.7")),  # 70% hit ratio
    "target_cost_reduction": float(os.getenv("TARGET_COST_REDUCTION", "0.7")),  # 70% cost reduction
    "min_similarity_for_cost_savings": float(os.getenv("MIN_SIMILARITY_COST_SAVINGS", "0.8")),
    "high_value_request_threshold": float(os.getenv("HIGH_VALUE_REQUEST_THRESHOLD", "0.05")),  # $0.05+
}

# Monitoring and alerting configuration
MONITORING_CONFIG = {
    "enable_performance_metrics": os.getenv("ENABLE_CACHE_METRICS", "true").lower() == "true",
    "metrics_export_interval": int(os.getenv("METRICS_EXPORT_INTERVAL", "60")),  # seconds
    "cost_savings_alert_threshold": float(os.getenv("COST_SAVINGS_ALERT_THRESHOLD", "100.0")),  # $100 saved
    "hit_ratio_alert_threshold": float(os.getenv("HIT_RATIO_ALERT_THRESHOLD", "0.5")),  # 50% minimum
    "enable_cache_health_checks": os.getenv("ENABLE_CACHE_HEALTH_CHECKS", "true").lower() == "true",
}

def create_production_cache_config() -> CacheConfig:
    """Create production-optimized cache configuration."""
    # Ensure vector database directory exists
    vector_db_path = Path(ENHANCED_CACHE_CONFIG.vector_db_path)
    vector_db_path.mkdir(parents=True, exist_ok=True)
    
    return ENHANCED_CACHE_CONFIG

def validate_configuration() -> bool:
    """Validate the vector cache configuration for production readiness."""
    try:
        # Check required environment variables
        required_vars = ["OPENAI_API_KEY"]  # Minimal requirements
        missing_vars = [var for var in required_vars if not os.getenv(var)]
        
        if missing_vars:
            print(f"Missing required environment variables: {missing_vars}")
            return False
        
        # Validate similarity threshold
        if not 0.5 <= ENHANCED_CACHE_CONFIG.similarity_threshold <= 1.0:
            print(f"Invalid similarity threshold: {ENHANCED_CACHE_CONFIG.similarity_threshold}")
            return False
        
        # Validate cache size limits
        if ENHANCED_CACHE_CONFIG.max_cache_size <= 0:
            print(f"Invalid max cache size: {ENHANCED_CACHE_CONFIG.max_cache_size}")
            return False
        
        # Check vector database path accessibility
        try:
            vector_path = Path(ENHANCED_CACHE_CONFIG.vector_db_path)
            vector_path.mkdir(parents=True, exist_ok=True)
        except Exception as e:
            print(f"Cannot create vector database directory: {e}")
            return False
        
        print("Vector cache configuration validation passed ✓")
        return True
        
    except Exception as e:
        print(f"Configuration validation error: {e}")
        return False

# Export configuration for easy import
__all__ = [
    "VECTOR_DB_CONFIG",
    "ENHANCED_CACHE_CONFIG", 
    "CACHE_WARMING_COMPANIES",
    "CACHE_WARMING_ROLES",
    "PERFORMANCE_CONFIG",
    "COST_OPTIMIZATION_TARGETS",
    "MONITORING_CONFIG",
    "create_production_cache_config",
    "validate_configuration"
]