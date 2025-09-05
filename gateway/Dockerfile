# Multi-stage build for Java Gateway Service

# Build Stage - Dependencies
FROM maven:3.9-eclipse-temurin-17-alpine AS deps

# Install dumb-init for proper signal handling
RUN apk add --no-cache dumb-init

WORKDIR /build

# Copy only dependency descriptor files first for optimal caching
COPY pom.xml .

# Download and cache dependencies separately from source code
# This layer will be cached as long as pom.xml doesn't change
RUN mvn dependency:go-offline -B

# Build Stage - Application
FROM deps AS builder

# Copy source code
COPY src ./src

# Build the application JAR (dependencies already cached)
RUN mvn package -DskipTests -B && \
    # Extract JAR layers for better layer caching in runtime
    mkdir -p /build/extracted && \
    cd /build/extracted && \
    java -Djarmode=layertools -jar /build/target/gateway-*.jar extract

# Runtime Stage - Optimized Alpine JRE
FROM eclipse-temurin:17-jre-alpine AS runtime

# Install dumb-init for proper signal handling and security updates
RUN apk add --no-cache dumb-init && \
    apk upgrade --no-cache

# Create non-root user for security
RUN addgroup -g 1000 gateway && \
    adduser -u 1000 -G gateway -s /bin/sh -D gateway

WORKDIR /app

# Copy extracted JAR layers in optimal order for caching
# Dependencies change less frequently than application code
COPY --from=builder --chown=gateway:gateway /build/extracted/dependencies/ ./
COPY --from=builder --chown=gateway:gateway /build/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=gateway:gateway /build/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=gateway:gateway /build/extracted/application/ ./

# Switch to non-root user
USER gateway

# Add health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Expose the application port
EXPOSE 8080

# Use dumb-init to handle signals properly and optimized JVM flags
ENTRYPOINT ["dumb-init", "--"]
CMD ["java", \
     "-XX:+UseContainerSupport", \
     "-XX:MaxRAMPercentage=75.0", \
     "-XX:+ExitOnOutOfMemoryError", \
     "-XX:+UnlockExperimentalVMOptions", \
     "-XX:+UseCGroupMemoryLimitForHeap", \
     "-Djava.security.egd=file:/dev/./urandom", \
     "-Dspring.profiles.active=docker", \
     "org.springframework.boot.loader.launch.JarLauncher"]