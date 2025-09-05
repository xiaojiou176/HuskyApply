#!/bin/bash

# HuskyApply Smoke Tests
# Quick functional tests to verify deployment health

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
ENVIRONMENT="${1:-staging}"
BASE_URL=""
TIMEOUT="${TIMEOUT:-30}"
VERBOSE="${VERBOSE:-false}"

# Set base URL based on environment
case "$ENVIRONMENT" in
    staging)
        BASE_URL="https://staging.huskyapply.com"
        ;;
    production)
        BASE_URL="https://api.huskyapply.com"
        ;;
    local)
        BASE_URL="http://localhost:8080"
        ;;
    *)
        echo -e "${RED}Error: Unknown environment '$ENVIRONMENT'${NC}"
        echo "Supported environments: staging, production, local"
        exit 1
        ;;
esac

# Test results
TESTS_RUN=0
TESTS_PASSED=0
TESTS_FAILED=0
FAILED_TESTS=()

# Helper functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[PASS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[FAIL]${NC} $1"
}

log_verbose() {
    if [[ "$VERBOSE" == "true" ]]; then
        echo -e "${BLUE}[DEBUG]${NC} $1"
    fi
}

# Execute HTTP request with timeout
http_request() {
    local method="$1"
    local endpoint="$2"
    local expected_status="${3:-200}"
    local headers="${4:-}"
    local data="${5:-}"
    
    local curl_cmd="curl -s -m $TIMEOUT"
    
    # Add headers if provided
    if [[ -n "$headers" ]]; then
        curl_cmd="$curl_cmd $headers"
    fi
    
    # Add data if provided
    if [[ -n "$data" ]]; then
        curl_cmd="$curl_cmd -d '$data'"
    fi
    
    # Execute request and capture response
    local response
    local status_code
    
    if response=$(eval "$curl_cmd -w '%{http_code}' -X $method '$BASE_URL$endpoint'"); then
        status_code="${response: -3}"
        response="${response%???}"
        
        log_verbose "HTTP $method $endpoint - Status: $status_code"
        
        if [[ "$status_code" == "$expected_status" ]]; then
            echo "$response"
            return 0
        else
            log_verbose "Expected status $expected_status, got $status_code"
            return 1
        fi
    else
        log_verbose "Request failed: $method $endpoint"
        return 1
    fi
}

# Test function wrapper
run_test() {
    local test_name="$1"
    local test_function="$2"
    
    TESTS_RUN=$((TESTS_RUN + 1))
    
    if $test_function; then
        log_success "$test_name"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        log_error "$test_name"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        FAILED_TESTS+=("$test_name")
    fi
}

# ==================== HEALTH CHECK TESTS ====================

test_health_endpoint() {
    local response
    if response=$(http_request "GET" "/actuator/health" "200"); then
        if echo "$response" | grep -q '"status":"UP"'; then
            return 0
        fi
    fi
    return 1
}

test_info_endpoint() {
    http_request "GET" "/actuator/info" "200" > /dev/null
}

test_metrics_endpoint() {
    http_request "GET" "/actuator/prometheus" "200" > /dev/null
}

# ==================== AUTHENTICATION TESTS ====================

test_auth_endpoints_exist() {
    # Test registration endpoint exists (should return validation error)
    http_request "POST" "/api/v1/auth/register" "400" "-H 'Content-Type: application/json'" > /dev/null
    
    # Test login endpoint exists (should return validation error)
    http_request "POST" "/api/v1/auth/login" "400" "-H 'Content-Type: application/json'" > /dev/null
}

test_protected_endpoint_without_auth() {
    # Should return 401 Unauthorized
    http_request "GET" "/api/v1/dashboard/stats" "401" > /dev/null
}

# ==================== API ENDPOINT TESTS ====================

test_api_endpoints_exist() {
    # Test that API endpoints exist (should return 401 without auth)
    local endpoints=(
        "/api/v1/applications"
        "/api/v1/dashboard/stats"
        "/api/v1/templates"
        "/api/v1/subscriptions/plans"
    )
    
    for endpoint in "${endpoints[@]}"; do
        if ! http_request "GET" "$endpoint" "401" > /dev/null; then
            return 1
        fi
    done
    
    return 0
}

test_public_subscription_plans() {
    # Subscription plans should be publicly accessible
    local response
    if response=$(http_request "GET" "/api/v1/subscriptions/plans" "200"); then
        if echo "$response" | grep -q "name"; then
            return 0
        fi
    fi
    return 1
}

# ==================== INTEGRATION TESTS ====================

test_brain_service_integration() {
    # Test that Gateway can communicate with Brain service
    # This is indirect - we check if the health endpoint reports Brain connectivity
    local response
    if response=$(http_request "GET" "/actuator/health" "200"); then
        # Check if all components are UP (this would include Brain connectivity)
        if echo "$response" | grep -q '"status":"UP"'; then
            return 0
        fi
    fi
    return 1
}

test_database_connectivity() {
    # Check database connectivity through health endpoint
    local response
    if response=$(http_request "GET" "/actuator/health" "200"); then
        if echo "$response" | grep -q '"db"'; then
            return 0
        fi
    fi
    return 1
}

test_rabbitmq_connectivity() {
    # Check RabbitMQ connectivity through health endpoint
    local response
    if response=$(http_request "GET" "/actuator/health" "200"); then
        if echo "$response" | grep -q '"rabbit"'; then
            return 0
        fi
    fi
    return 1
}

test_redis_connectivity() {
    # Check Redis connectivity through health endpoint
    local response
    if response=$(http_request "GET" "/actuator/health" "200"); then
        if echo "$response" | grep -q '"redis"'; then
            return 0
        fi
    fi
    return 1
}

# ==================== PERFORMANCE TESTS ====================

test_response_time() {
    local start_time
    local end_time
    local duration
    
    start_time=$(date +%s%N)
    
    if http_request "GET" "/actuator/health" "200" > /dev/null; then
        end_time=$(date +%s%N)
        duration=$(( (end_time - start_time) / 1000000 )) # Convert to milliseconds
        
        log_verbose "Health endpoint response time: ${duration}ms"
        
        # Response should be under 5 seconds
        if [[ $duration -lt 5000 ]]; then
            return 0
        else
            log_verbose "Response time too slow: ${duration}ms"
            return 1
        fi
    fi
    return 1
}

test_concurrent_requests() {
    # Test that the service can handle multiple concurrent requests
    local pids=()
    local success_count=0
    
    # Launch 5 concurrent requests
    for i in {1..5}; do
        (
            if http_request "GET" "/actuator/health" "200" > /dev/null 2>&1; then
                exit 0
            else
                exit 1
            fi
        ) &
        pids+=($!)
    done
    
    # Wait for all requests to complete
    for pid in "${pids[@]}"; do
        if wait "$pid"; then
            success_count=$((success_count + 1))
        fi
    done
    
    # At least 4 out of 5 should succeed
    if [[ $success_count -ge 4 ]]; then
        log_verbose "Concurrent requests: $success_count/5 succeeded"
        return 0
    else
        log_verbose "Concurrent requests: only $success_count/5 succeeded"
        return 1
    fi
}

# ==================== SECURITY TESTS ====================

test_security_headers() {
    local response
    if response=$(curl -s -I -m "$TIMEOUT" "$BASE_URL/actuator/health"); then
        local required_headers=(
            "X-Frame-Options"
            "X-Content-Type-Options"
            "X-XSS-Protection"
            "Strict-Transport-Security"
        )
        
        for header in "${required_headers[@]}"; do
            if ! echo "$response" | grep -qi "$header"; then
                log_verbose "Missing security header: $header"
                return 1
            fi
        done
        
        return 0
    fi
    return 1
}

test_cors_configuration() {
    # Test CORS headers are present
    local response
    if response=$(curl -s -I -m "$TIMEOUT" -H "Origin: https://app.huskyapply.com" "$BASE_URL/api/v1/subscriptions/plans"); then
        if echo "$response" | grep -qi "Access-Control-Allow-Origin"; then
            return 0
        fi
    fi
    return 1
}

# ==================== MONITORING TESTS ====================

test_prometheus_metrics() {
    local response
    if response=$(http_request "GET" "/actuator/prometheus" "200"); then
        # Check for some expected metrics
        local expected_metrics=(
            "jvm_memory_used_bytes"
            "http_server_requests_seconds"
            "system_cpu_usage"
        )
        
        for metric in "${expected_metrics[@]}"; do
            if ! echo "$response" | grep -q "$metric"; then
                log_verbose "Missing metric: $metric"
                return 1
            fi
        done
        
        return 0
    fi
    return 1
}

# ==================== MAIN EXECUTION ====================

main() {
    log_info "Starting smoke tests for $ENVIRONMENT environment"
    log_info "Base URL: $BASE_URL"
    log_info "Timeout: ${TIMEOUT}s"
    echo

    # Health Check Tests
    log_info "Running health check tests..."
    run_test "Health endpoint responds with UP status" test_health_endpoint
    run_test "Info endpoint is accessible" test_info_endpoint
    run_test "Metrics endpoint is accessible" test_metrics_endpoint
    echo

    # Authentication Tests
    log_info "Running authentication tests..."
    run_test "Auth endpoints exist and validate input" test_auth_endpoints_exist
    run_test "Protected endpoints require authentication" test_protected_endpoint_without_auth
    echo

    # API Tests
    log_info "Running API tests..."
    run_test "API endpoints exist and require auth" test_api_endpoints_exist
    run_test "Public subscription plans endpoint works" test_public_subscription_plans
    echo

    # Integration Tests
    log_info "Running integration tests..."
    run_test "Brain service integration" test_brain_service_integration
    run_test "Database connectivity" test_database_connectivity
    run_test "RabbitMQ connectivity" test_rabbitmq_connectivity
    run_test "Redis connectivity" test_redis_connectivity
    echo

    # Performance Tests
    log_info "Running performance tests..."
    run_test "Response time is acceptable" test_response_time
    run_test "Service handles concurrent requests" test_concurrent_requests
    echo

    # Security Tests
    log_info "Running security tests..."
    run_test "Security headers are present" test_security_headers
    run_test "CORS configuration is correct" test_cors_configuration
    echo

    # Monitoring Tests
    log_info "Running monitoring tests..."
    run_test "Prometheus metrics are available" test_prometheus_metrics
    echo

    # Summary
    log_info "Smoke test summary:"
    log_info "• Tests run: $TESTS_RUN"
    log_success "• Passed: $TESTS_PASSED"
    
    if [[ $TESTS_FAILED -gt 0 ]]; then
        log_error "• Failed: $TESTS_FAILED"
        log_error "Failed tests:"
        for test in "${FAILED_TESTS[@]}"; do
            log_error "  - $test"
        done
        echo
        log_error "Smoke tests FAILED!"
        return 1
    else
        echo
        log_success "All smoke tests PASSED! 🎉"
        return 0
    fi
}

# Handle command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -v|--verbose)
            VERBOSE="true"
            shift
            ;;
        -t|--timeout)
            TIMEOUT="$2"
            shift 2
            ;;
        --base-url)
            BASE_URL="$2"
            shift 2
            ;;
        -h|--help)
            echo "Usage: $0 [ENVIRONMENT] [OPTIONS]"
            echo
            echo "Environments:"
            echo "  staging     - Test staging environment (default)"
            echo "  production  - Test production environment"
            echo "  local       - Test local development environment"
            echo
            echo "Options:"
            echo "  -v, --verbose     Enable verbose output"
            echo "  -t, --timeout N   Set request timeout (default: 30s)"
            echo "  --base-url URL    Override base URL"
            echo "  -h, --help        Show this help message"
            echo
            echo "Examples:"
            echo "  $0 production"
            echo "  $0 staging --verbose"
            echo "  $0 local --timeout 60"
            exit 0
            ;;
        *)
            if [[ -z "${1:-}" ]]; then
                break
            fi
            ENVIRONMENT="$1"
            shift
            ;;
    esac
done

# Execute main function
main
exit $?