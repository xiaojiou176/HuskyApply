#!/bin/bash

# HuskyApply Health Check Script
# Verifies that all services are running and accessible

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
BRAIN_URL="${BRAIN_URL:-http://localhost:8000}"
FRONTEND_URL="${FRONTEND_URL:-http://localhost:3000}"
POSTGRES_HOST="${POSTGRES_HOST:-localhost}"
POSTGRES_PORT="${POSTGRES_PORT:-5432}"
RABBITMQ_HOST="${RABBITMQ_HOST:-localhost}"
RABBITMQ_PORT="${RABBITMQ_PORT:-15672}"
REDIS_HOST="${REDIS_HOST:-localhost}"
REDIS_PORT="${REDIS_PORT:-6379}"

# Health check timeout (seconds)
TIMEOUT=10

# Counters
PASSED=0
FAILED=0

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[✓]${NC} $1"
    PASSED=$((PASSED + 1))
}

log_warning() {
    echo -e "${YELLOW}[⚠]${NC} $1"
}

log_error() {
    echo -e "${RED}[✗]${NC} $1"
    FAILED=$((FAILED + 1))
}

# Health check functions
check_http_endpoint() {
    local name="$1"
    local url="$2"
    local expected_status="${3:-200}"
    
    log_info "Checking $name at $url"
    
    if command -v curl &> /dev/null; then
        response=$(curl -s -o /dev/null -w "%{http_code}" --max-time $TIMEOUT "$url" || echo "000")
        
        if [ "$response" = "$expected_status" ]; then
            log_success "$name is healthy (HTTP $response)"
        else
            log_error "$name returned HTTP $response (expected $expected_status)"
        fi
    else
        log_error "curl not available - cannot check $name"
    fi
}

check_tcp_port() {
    local name="$1"
    local host="$2"
    local port="$3"
    
    log_info "Checking $name TCP connection at $host:$port"
    
    if command -v nc &> /dev/null; then
        if nc -z -w $TIMEOUT "$host" "$port" 2>/dev/null; then
            log_success "$name TCP connection successful"
        else
            log_error "$name TCP connection failed"
        fi
    elif command -v telnet &> /dev/null; then
        if timeout $TIMEOUT bash -c "echo >/dev/tcp/$host/$port" 2>/dev/null; then
            log_success "$name TCP connection successful"
        else
            log_error "$name TCP connection failed"
        fi
    else
        log_error "nc or telnet not available - cannot check $name TCP connection"
    fi
}

check_postgres() {
    log_info "Checking PostgreSQL database"
    
    if command -v psql &> /dev/null; then
        if PGPASSWORD="${POSTGRES_PASSWORD:-husky}" psql -h "$POSTGRES_HOST" -p "$POSTGRES_PORT" -U "${POSTGRES_USER:-husky}" -d "${POSTGRES_DB:-huskyapply}" -c "SELECT 1;" &>/dev/null; then
            log_success "PostgreSQL database connection successful"
        else
            log_error "PostgreSQL database connection failed"
        fi
    else
        # Fallback to TCP check
        check_tcp_port "PostgreSQL" "$POSTGRES_HOST" "$POSTGRES_PORT"
    fi
}

check_redis() {
    log_info "Checking Redis cache"
    
    if command -v redis-cli &> /dev/null; then
        if redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" ping | grep -q "PONG"; then
            log_success "Redis cache connection successful"
        else
            log_error "Redis cache connection failed"
        fi
    else
        # Fallback to TCP check
        check_tcp_port "Redis" "$REDIS_HOST" "$REDIS_PORT"
    fi
}

check_rabbitmq() {
    log_info "Checking RabbitMQ message broker"
    
    # Check management interface
    check_http_endpoint "RabbitMQ Management" "$RABBITMQ_HOST:$RABBITMQ_PORT" "200"
    
    # Also check AMQP port
    check_tcp_port "RabbitMQ AMQP" "$RABBITMQ_HOST" "5672"
}

check_gateway_detailed() {
    log_info "Performing detailed Gateway health check"
    
    # Basic health check
    check_http_endpoint "Gateway Health" "$GATEWAY_URL/actuator/health" "200"
    
    # Check specific endpoints
    check_http_endpoint "Gateway Info" "$GATEWAY_URL/actuator/info" "200"
    check_http_endpoint "Gateway Metrics" "$GATEWAY_URL/actuator/prometheus" "200"
    
    # Check API version endpoint (should return 404 or redirect)
    check_http_endpoint "Gateway API" "$GATEWAY_URL/api/v1" "404"
}

check_brain_detailed() {
    log_info "Performing detailed Brain health check"
    
    # Health check endpoint
    check_http_endpoint "Brain Health" "$BRAIN_URL/healthz" "200"
    
    # Metrics endpoint
    check_http_endpoint "Brain Metrics" "$BRAIN_URL/metrics" "200"
}

check_frontend_detailed() {
    log_info "Performing detailed Frontend health check"
    
    # Check if frontend is serving content
    check_http_endpoint "Frontend" "$FRONTEND_URL" "200"
    
    # Check for specific assets
    check_http_endpoint "Frontend CSS" "$FRONTEND_URL/styles.css" "200"
    check_http_endpoint "Frontend JS" "$FRONTEND_URL/app.js" "200"
}

check_system_resources() {
    log_info "Checking system resources"
    
    # Check available disk space (warn if less than 1GB free)
    if command -v df &> /dev/null; then
        available_space=$(df / | awk 'NR==2 {print $4}')
        if [ "$available_space" -lt 1048576 ]; then  # 1GB in KB
            log_warning "Low disk space: $(df -h / | awk 'NR==2 {print $4}') available"
        else
            log_success "Disk space sufficient: $(df -h / | awk 'NR==2 {print $4}') available"
        fi
    fi
    
    # Check available memory (warn if less than 500MB free)
    if command -v free &> /dev/null; then
        available_memory=$(free -m | awk 'NR==2{print $7}')
        if [ "$available_memory" -lt 500 ]; then
            log_warning "Low memory: ${available_memory}MB available"
        else
            log_success "Memory sufficient: ${available_memory}MB available"
        fi
    fi
    
    # Check load average
    if command -v uptime &> /dev/null; then
        load_avg=$(uptime | awk -F'load average:' '{print $2}' | cut -d, -f1 | xargs)
        log_info "System load average: $load_avg"
    fi
}

check_docker_containers() {
    log_info "Checking Docker containers (if applicable)"
    
    if command -v docker &> /dev/null && docker info &>/dev/null; then
        # Check if containers are running
        containers=("huskyapply-gateway" "huskyapply-brain" "huskyapply-postgres" "huskyapply-rabbitmq" "huskyapply-redis")
        
        for container in "${containers[@]}"; do
            if docker ps --format "table {{.Names}}" | grep -q "$container"; then
                status=$(docker ps --format "table {{.Names}}\t{{.Status}}" | grep "$container" | awk '{print $2}')
                log_success "Docker container $container is running ($status)"
            else
                log_warning "Docker container $container not found or not running"
            fi
        done
    else
        log_info "Docker not available or not accessible"
    fi
}

# Main execution
main() {
    echo "=================================="
    echo "🐺 HuskyApply Health Check"
    echo "=================================="
    echo "Timestamp: $(date)"
    echo "Environment: ${ENVIRONMENT:-development}"
    echo ""
    
    # Infrastructure checks
    log_info "🔧 Checking Infrastructure Services..."
    check_postgres
    check_redis
    check_rabbitmq
    echo ""
    
    # Application service checks
    log_info "🚀 Checking Application Services..."
    check_gateway_detailed
    check_brain_detailed
    check_frontend_detailed
    echo ""
    
    # System resource checks
    log_info "💻 Checking System Resources..."
    check_system_resources
    echo ""
    
    # Docker container checks (if applicable)
    check_docker_containers
    echo ""
    
    # Summary
    echo "=================================="
    echo "📊 Health Check Summary"
    echo "=================================="
    echo -e "Passed: ${GREEN}$PASSED${NC}"
    echo -e "Failed: ${RED}$FAILED${NC}"
    echo -e "Total:  $((PASSED + FAILED))"
    
    if [ $FAILED -eq 0 ]; then
        echo ""
        log_success "All health checks passed! 🎉"
        echo "System is ready for operation."
        exit 0
    else
        echo ""
        log_error "Some health checks failed! ❌"
        echo "Please review the failed checks above."
        exit 1
    fi
}

# Handle command line arguments
case "${1:-}" in
    --quick)
        # Quick check - only essential services
        echo "🐺 HuskyApply Quick Health Check"
        check_http_endpoint "Gateway" "$GATEWAY_URL/actuator/health" "200"
        check_http_endpoint "Brain" "$BRAIN_URL/healthz" "200"
        check_http_endpoint "Frontend" "$FRONTEND_URL" "200"
        ;;
    --infrastructure)
        # Infrastructure only
        echo "🔧 Infrastructure Health Check"
        check_postgres
        check_redis
        check_rabbitmq
        ;;
    --services)
        # Application services only
        echo "🚀 Application Services Health Check"
        check_gateway_detailed
        check_brain_detailed
        check_frontend_detailed
        ;;
    --help)
        echo "HuskyApply Health Check Script"
        echo ""
        echo "Usage: $0 [OPTION]"
        echo ""
        echo "Options:"
        echo "  --quick          Quick health check of main services"
        echo "  --infrastructure Check infrastructure services only"
        echo "  --services       Check application services only"
        echo "  --help           Show this help message"
        echo ""
        echo "Environment Variables:"
        echo "  GATEWAY_URL      Gateway service URL (default: http://localhost:8080)"
        echo "  BRAIN_URL        Brain service URL (default: http://localhost:8000)"
        echo "  FRONTEND_URL     Frontend URL (default: http://localhost:3000)"
        echo "  POSTGRES_HOST    PostgreSQL host (default: localhost)"
        echo "  RABBITMQ_HOST    RabbitMQ host (default: localhost)"
        echo "  REDIS_HOST       Redis host (default: localhost)"
        ;;
    *)
        # Full health check
        main
        ;;
esac