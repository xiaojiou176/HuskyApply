# Multi-stage Dockerfile for HuskyApply Brain Service

# Dependencies Stage - Cache Python dependencies
FROM python:3.11-alpine AS deps

# Install UV for fast Python package management
RUN pip install --no-cache-dir uv

WORKDIR /deps

# Copy dependency files first for optimal Docker layer caching
COPY pyproject.toml ./

# Create virtual environment and install dependencies in separate layer
RUN uv venv /opt/venv && \
    /opt/venv/bin/uv pip install --no-cache . && \
    # Create development dependencies layer  
    /opt/venv/bin/uv pip install --no-cache ".[dev,test]"

# Build Stage - Prepare application
FROM python:3.11-alpine AS builder

# Install system dependencies needed for runtime
RUN apk add --no-cache \
    curl \
    dumb-init \
    && apk upgrade --no-cache

# Copy virtual environment from deps stage
COPY --from=deps /opt/venv /opt/venv

# Create non-root user for security
RUN addgroup -g 1000 brain && \
    adduser -u 1000 -G brain -s /bin/sh -D brain

WORKDIR /app

# Copy application code with proper ownership
COPY --chown=brain:brain . .

# Production Stage - Minimal runtime
FROM python:3.11-alpine AS production

# Install only runtime system dependencies and security updates
RUN apk add --no-cache \
    curl \
    dumb-init \
    && apk upgrade --no-cache \
    && rm -rf /var/cache/apk/*

# Create non-root user
RUN addgroup -g 1000 brain && \
    adduser -u 1000 -G brain -s /bin/sh -D brain

# Copy virtual environment and application from builder
COPY --from=builder --chown=brain:brain /opt/venv /opt/venv
COPY --from=builder --chown=brain:brain /app /app

WORKDIR /app

# Make sure we use venv
ENV PATH="/opt/venv/bin:$PATH" \
    PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONPATH=/app

# Switch to non-root user
USER brain

# Health check with improved configuration
HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:8000/healthz || exit 1

# Expose the application port
EXPOSE 8000

# Use dumb-init for proper signal handling
ENTRYPOINT ["dumb-init", "--"]
CMD ["python", "main.py"]

# Development Stage - With development tools and hot reload
FROM builder AS development

# Install additional development tools in virtual environment
RUN /opt/venv/bin/pip install --no-cache-dir \
    watchfiles \
    pytest-xdist

# Set environment for development
ENV PATH="/opt/venv/bin:$PATH" \
    PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONPATH=/app

# Switch to non-root user
USER brain

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=10s --retries=3 \
    CMD curl -f http://localhost:8000/healthz || exit 1

# Expose the application port
EXPOSE 8000

# Development command with hot reload and optimized uvicorn settings
ENTRYPOINT ["dumb-init", "--"]
CMD ["uvicorn", "main:app", \
     "--host", "0.0.0.0", \
     "--port", "8000", \
     "--reload", \
     "--reload-dir", ".", \
     "--log-level", "info", \
     "--access-log"]