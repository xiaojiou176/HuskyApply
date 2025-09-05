#!/bin/bash

set -euo pipefail

# Docker Build and Runtime Monitoring Script for HuskyApply
# Provides comprehensive monitoring of Docker build performance and runtime metrics

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
BOLD='\033[1m'
NC='\033[0m' # No Color

# Configuration
MONITOR_INTERVAL=${MONITOR_INTERVAL:-5}
ALERT_CPU_THRESHOLD=${ALERT_CPU_THRESHOLD:-80}
ALERT_MEM_THRESHOLD=${ALERT_MEM_THRESHOLD:-80}
LOG_FILE="$PROJECT_ROOT/docker-monitoring.log"

log() {
    echo -e "${BLUE}[$(date +'%Y-%m-%d %H:%M:%S')]${NC} $1"
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] $1" >> "$LOG_FILE"
}

success() {
    echo -e "${GREEN}✓${NC} $1"
}

warning() {
    echo -e "${YELLOW}⚠${NC} $1"
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] WARNING: $1" >> "$LOG_FILE"
}

error() {
    echo -e "${RED}✗${NC} $1"
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] ERROR: $1" >> "$LOG_FILE"
}

# Function to monitor build performance
monitor_build() {
    local service=$1
    local start_time=$(date +%s)
    
    log "Starting build monitoring for $service..."
    
    # Monitor build process
    local build_log="/tmp/huskyapply-${service}-build.log"
    
    # Start build with timing and logging
    (
        cd "$PROJECT_ROOT"
        time docker buildx build \
            --progress=plain \
            --tag "huskyapply/$service:monitor" \
            "$service/" 2>&1 | tee "$build_log"
    ) &
    
    local build_pid=$!
    
    # Monitor system resources during build
    while kill -0 $build_pid 2>/dev/null; do
        local cpu_usage=$(top -l 1 | grep "CPU usage" | awk '{print $3}' | sed 's/%//')
        local mem_usage=$(top -l 1 | grep "PhysMem" | awk '{print $2}' | sed 's/M.*//')
        
        echo "Build Progress - CPU: ${cpu_usage}%, Memory: ${mem_usage}M"
        sleep 2
    done
    
    wait $build_pid
    local build_exit_code=$?
    
    local end_time=$(date +%s)
    local build_duration=$((end_time - start_time))
    
    if [ $build_exit_code -eq 0 ]; then
        success "$service build completed in ${build_duration}s"
        analyze_build_log "$service" "$build_log" "$build_duration"
    else
        error "$service build failed after ${build_duration}s"
    fi
    
    return $build_exit_code
}

# Function to analyze build logs for optimization insights
analyze_build_log() {
    local service=$1
    local log_file=$2
    local duration=$3
    
    log "Analyzing build performance for $service..."
    
    # Extract key metrics from build log
    local cache_hits=$(grep -c "CACHED" "$log_file" || echo "0")
    local total_steps=$(grep -c "Step [0-9]" "$log_file" || echo "0")
    local layer_download_time=$(grep "sha256:" "$log_file" | wc -l || echo "0")
    
    echo -e "\n${BOLD}=== Build Performance Analysis: $service ===${NC}"
    echo "Total build time: ${duration}s"
    echo "Total steps: $total_steps"
    echo "Cache hits: $cache_hits"
    echo "Layer downloads: $layer_download_time"
    
    if [ "$total_steps" -gt 0 ]; then
        local cache_hit_ratio=$((cache_hits * 100 / total_steps))
        echo "Cache hit ratio: ${cache_hit_ratio}%"
        
        if [ "$cache_hit_ratio" -lt 50 ]; then
            warning "Low cache hit ratio detected. Consider optimizing Dockerfile layer ordering."
        else
            success "Good cache utilization: ${cache_hit_ratio}%"
        fi
    fi
    
    # Check for common optimization opportunities
    if grep -q "RUN apt-get update" "$log_file"; then
        warning "Consider combining apt-get update and install commands for better caching"
    fi
    
    if grep -q "COPY . ." "$log_file"; then
        warning "Consider using more specific COPY commands to improve cache utilization"
    fi
    
    echo ""
}

# Function to monitor runtime performance
monitor_runtime() {
    log "Starting runtime performance monitoring..."
    
    # Check if containers are running
    local containers=$(docker ps --filter "label=com.docker.compose.project=infra" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" 2>/dev/null)
    
    if [ -z "$containers" ]; then
        error "No HuskyApply containers found running"
        return 1
    fi
    
    echo -e "\n${BOLD}=== Runtime Container Status ===${NC}"
    echo "$containers"
    echo ""
    
    # Monitor each HuskyApply service container
    for service in huskyapply-gateway huskyapply-brain; do
        if docker ps --filter "name=$service" --format "{{.Names}}" | grep -q "$service"; then
            monitor_container "$service"
        fi
    done
}

# Function to monitor individual container performance
monitor_container() {
    local container=$1
    
    log "Monitoring $container performance..."
    
    # Get container stats
    local stats=$(docker stats "$container" --no-stream --format "table {{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}\t{{.NetIO}}\t{{.BlockIO}}")
    
    echo -e "\n${BOLD}=== $container Performance ===${NC}"
    echo "CPU%    MEM USAGE/LIMIT    MEM%    NET I/O    BLOCK I/O"
    echo "$stats" | tail -n +2
    
    # Extract numeric values for alerting
    local cpu_percent=$(echo "$stats" | tail -n +2 | awk '{print $1}' | sed 's/%//')
    local mem_percent=$(echo "$stats" | tail -n +2 | awk '{print $3}' | sed 's/%//')
    
    # Remove any potential decimal points and round down
    cpu_percent=$(echo "$cpu_percent" | cut -d. -f1)
    mem_percent=$(echo "$mem_percent" | cut -d. -f1)
    
    # Check thresholds
    if [ "${cpu_percent:-0}" -gt "$ALERT_CPU_THRESHOLD" ]; then
        warning "$container CPU usage (${cpu_percent}%) exceeds threshold ($ALERT_CPU_THRESHOLD%)"
    fi
    
    if [ "${mem_percent:-0}" -gt "$ALERT_MEM_THRESHOLD" ]; then
        warning "$container Memory usage (${mem_percent}%) exceeds threshold ($ALERT_MEM_THRESHOLD%)"
    fi
    
    # Check container health
    local health_status=$(docker inspect "$container" --format='{{.State.Health.Status}}' 2>/dev/null || echo "none")
    if [ "$health_status" = "unhealthy" ]; then
        error "$container is reporting unhealthy status"
    elif [ "$health_status" = "healthy" ]; then
        success "$container health check passed"
    fi
    
    # Get recent logs for error detection
    local error_count=$(docker logs "$container" --since 1m 2>&1 | grep -i error | wc -l || echo "0")
    if [ "$error_count" -gt 0 ]; then
        warning "$container has $error_count recent error log entries"
    fi
    
    echo ""
}

# Function to monitor image sizes over time
monitor_image_sizes() {
    log "Monitoring Docker image sizes..."
    
    local size_log="$PROJECT_ROOT/docker-image-sizes.log"
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    
    echo "=== Image Sizes at $timestamp ===" >> "$size_log"
    
    echo -e "\n${BOLD}=== Current Image Sizes ===${NC}"
    printf "%-25s %-15s %-15s %-20s\n" "REPOSITORY" "TAG" "SIZE" "CREATED"
    echo "-----------------------------------------------------------------------"
    
    # Monitor HuskyApply images
    docker images --filter "reference=huskyapply/*" --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}\t{{.CreatedSince}}" | tail -n +2 | while IFS=$'\t' read -r repo tag size created; do
        printf "%-25s %-15s %-15s %-20s\n" "$repo" "$tag" "$size" "$created"
        echo "$timestamp,$repo,$tag,$size,$created" >> "$size_log"
    done
    
    echo ""
    
    # Analyze size trends if we have historical data
    if [ -f "$size_log" ] && [ $(wc -l < "$size_log") -gt 10 ]; then
        analyze_size_trends "$size_log"
    fi
}

# Function to analyze image size trends
analyze_size_trends() {
    local size_log=$1
    
    log "Analyzing image size trends..."
    
    # Get the latest and previous entries for each image
    for service in gateway brain; do
        local current_size=$(grep "huskyapply/$service,latest" "$size_log" | tail -1 | cut -d, -f4)
        local previous_size=$(grep "huskyapply/$service,latest" "$size_log" | tail -2 | head -1 | cut -d, -f4)
        
        if [ -n "$current_size" ] && [ -n "$previous_size" ] && [ "$current_size" != "$previous_size" ]; then
            warning "$service image size changed from $previous_size to $current_size"
        fi
    done
}

# Function to generate comprehensive monitoring report
generate_monitoring_report() {
    local report_file="$PROJECT_ROOT/docker-monitoring-report.md"
    
    log "Generating monitoring report..."
    
    cat > "$report_file" << EOF
# Docker Monitoring Report

Generated: $(date)

## System Overview

EOF
    
    # Add system information
    echo "**Docker Version**: $(docker --version)" >> "$report_file"
    echo "**Docker Compose Version**: $(docker-compose --version)" >> "$report_file"
    echo "**Host OS**: $(uname -a)" >> "$report_file"
    echo "" >> "$report_file"
    
    # Add container status
    echo "## Container Status" >> "$report_file"
    echo "" >> "$report_file"
    echo '```' >> "$report_file"
    docker ps --filter "label=com.docker.compose.project=infra" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" >> "$report_file" 2>/dev/null || echo "No containers running" >> "$report_file"
    echo '```' >> "$report_file"
    echo "" >> "$report_file"
    
    # Add image information
    echo "## Current Images" >> "$report_file"
    echo "" >> "$report_file"
    echo '```' >> "$report_file"
    docker images --filter "reference=huskyapply/*" --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}\t{{.CreatedSince}}" >> "$report_file"
    echo '```' >> "$report_file"
    echo "" >> "$report_file"
    
    # Add recent log analysis
    echo "## Recent Issues" >> "$report_file"
    echo "" >> "$report_file"
    
    if [ -f "$LOG_FILE" ]; then
        local error_count=$(grep -c "ERROR" "$LOG_FILE" || echo "0")
        local warning_count=$(grep -c "WARNING" "$LOG_FILE" || echo "0")
        
        echo "- **Errors in last session**: $error_count" >> "$report_file"
        echo "- **Warnings in last session**: $warning_count" >> "$report_file"
        
        if [ "$error_count" -gt 0 ]; then
            echo "" >> "$report_file"
            echo "### Recent Errors" >> "$report_file"
            echo '```' >> "$report_file"
            grep "ERROR" "$LOG_FILE" | tail -5 >> "$report_file"
            echo '```' >> "$report_file"
        fi
    fi
    
    echo "" >> "$report_file"
    echo "## Recommendations" >> "$report_file"
    echo "" >> "$report_file"
    echo "- Monitor container resource usage regularly" >> "$report_file"
    echo "- Keep image sizes optimized with regular cleanup" >> "$report_file"
    echo "- Review error logs for recurring issues" >> "$report_file"
    echo "- Use BuildKit for improved build performance" >> "$report_file"
    
    success "Monitoring report generated: $report_file"
}

# Function for continuous monitoring
continuous_monitor() {
    log "Starting continuous monitoring (Ctrl+C to stop)..."
    
    while true; do
        clear
        echo -e "${BOLD}HuskyApply Docker Monitoring Dashboard${NC}"
        echo "========================================"
        echo ""
        
        monitor_runtime
        monitor_image_sizes
        
        echo -e "\nNext update in ${MONITOR_INTERVAL}s... (Ctrl+C to stop)"
        sleep "$MONITOR_INTERVAL"
    done
}

# Function to show usage
usage() {
    cat << EOF
Docker Monitoring Script for HuskyApply

Usage: $0 [COMMAND] [OPTIONS]

Commands:
    monitor         Continuous runtime monitoring (default)
    build SERVICE   Monitor build process for specific service
    runtime         One-time runtime performance check
    images          Check current image sizes and trends  
    report          Generate comprehensive monitoring report
    analyze LOG     Analyze specific build log file

Options:
    --interval SEC      Set monitoring interval (default: 5s)
    --cpu-threshold %   CPU alert threshold (default: 80%)
    --mem-threshold %   Memory alert threshold (default: 80%)

Examples:
    $0 monitor
    $0 build gateway
    $0 runtime
    $0 images
    $0 report

Environment Variables:
    MONITOR_INTERVAL        Monitoring interval in seconds (default: 5)
    ALERT_CPU_THRESHOLD    CPU usage alert threshold (default: 80)
    ALERT_MEM_THRESHOLD    Memory usage alert threshold (default: 80)

EOF
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --interval)
            MONITOR_INTERVAL="$2"
            shift 2
            ;;
        --cpu-threshold)
            ALERT_CPU_THRESHOLD="$2"
            shift 2
            ;;
        --mem-threshold)
            ALERT_MEM_THRESHOLD="$2"
            shift 2
            ;;
        *)
            break
            ;;
    esac
done

# Execute command
case "${1:-monitor}" in
    monitor)
        continuous_monitor
        ;;
    build)
        if [ -z "${2:-}" ]; then
            error "Please specify a service to monitor (gateway or brain)"
        fi
        monitor_build "$2"
        ;;
    runtime)
        monitor_runtime
        ;;
    images)
        monitor_image_sizes
        ;;
    report)
        generate_monitoring_report
        ;;
    analyze)
        if [ -z "${2:-}" ] || [ ! -f "$2" ]; then
            error "Please provide a valid log file to analyze"
        fi
        analyze_build_log "manual" "$2" "0"
        ;;
    --help|-h)
        usage
        ;;
    *)
        error "Unknown command: $1. Use --help for usage information."
        ;;
esac