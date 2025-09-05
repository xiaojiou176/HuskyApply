#!/bin/bash
set -euo pipefail

# Comprehensive testing script for gRPC migration
# Tests all aspects of the migration including performance, reliability, and fallback mechanisms

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Default values
ENVIRONMENT="local"
TEST_SUITE="all"
CONCURRENT_REQUESTS=10
TEST_DURATION=60
VERBOSE=false
GENERATE_REPORT=false

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

usage() {
    cat << EOF
Usage: $0 [OPTIONS]

Comprehensive testing suite for gRPC migration validation.

OPTIONS:
    -e, --environment ENV       Environment (local, staging, production) [default: local]
    -s, --suite SUITE          Test suite to run [default: all]
    -c, --concurrent N         Number of concurrent requests [default: 10]
    -d, --duration SECONDS     Test duration in seconds [default: 60]
    -r, --report              Generate detailed test report
    -v, --verbose             Enable verbose output
    -h, --help                Show this help message

TEST SUITES:
    all                       Run all test suites
    health                    Basic health checks
    feature-flags             Feature flag testing
    performance               Performance and load testing
    fallback                  Fallback mechanism testing
    security                  mTLS security testing
    migration                 Migration workflow testing

EXAMPLES:
    $0 --suite health --environment staging
    $0 --suite performance --concurrent 50 --duration 120
    $0 --suite all --report --verbose

EOF
}

log() {
    echo -e "${BLUE}[$(date +'%Y-%m-%d %H:%M:%S')] $*${NC}"
}

log_success() {
    echo -e "${GREEN}[$(date +'%Y-%m-%d %H:%M:%S')] ✓ $*${NC}"
}

log_warning() {
    echo -e "${YELLOW}[$(date +'%Y-%m-%d %H:%M:%S')] ⚠ $*${NC}"
}

log_error() {
    echo -e "${RED}[$(date +'%Y-%m-%d %H:%M:%S')] ✗ $*${NC}"
}

log_info() {
    echo -e "${CYAN}[$(date +'%Y-%m-%d %H:%M:%S')] ℹ $*${NC}"
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -e|--environment)
            ENVIRONMENT="$2"
            shift 2
            ;;
        -s|--suite)
            TEST_SUITE="$2"
            shift 2
            ;;
        -c|--concurrent)
            CONCURRENT_REQUESTS="$2"
            shift 2
            ;;
        -d|--duration)
            TEST_DURATION="$2"
            shift 2
            ;;
        -r|--report)
            GENERATE_REPORT=true
            shift
            ;;
        -v|--verbose)
            VERBOSE=true
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            log_error "Unknown option: $1"
            usage
            exit 1
            ;;
    esac
done

# Set environment-specific configurations
case $ENVIRONMENT in
    local)
        GATEWAY_URL="http://localhost:8080"
        BRAIN_URL="http://localhost:8000"
        GATEWAY_GRPC="localhost:9090"
        BRAIN_GRPC="localhost:9090"
        ;;
    staging)
        GATEWAY_URL="https://staging-api.huskyapply.com"
        BRAIN_URL="http://brain-staging:8000"
        GATEWAY_GRPC="staging-gateway:9090"
        BRAIN_GRPC="brain-staging:9090"
        ;;
    production)
        GATEWAY_URL="https://api.huskyapply.com"
        BRAIN_URL="http://brain-production:8000"
        GATEWAY_GRPC="production-gateway:9090"
        BRAIN_GRPC="brain-production:9090"
        ;;
esac

# Test results storage
TEST_RESULTS_DIR="$PROJECT_ROOT/test-results/grpc-migration-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$TEST_RESULTS_DIR"

# Initialize test report
REPORT_FILE="$TEST_RESULTS_DIR/test-report.json"
cat > "$REPORT_FILE" << EOF
{
  "test_run": {
    "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
    "environment": "$ENVIRONMENT",
    "suite": "$TEST_SUITE",
    "concurrent_requests": $CONCURRENT_REQUESTS,
    "test_duration": $TEST_DURATION
  },
  "results": {}
}
EOF

update_report() {
    local suite="$1"
    local status="$2"
    local message="$3"
    local metrics="${4:-{}}"
    
    # Use jq to update the report if available, otherwise append to log
    if command -v jq >/dev/null 2>&1; then
        jq --arg suite "$suite" --arg status "$status" --arg message "$message" --argjson metrics "$metrics" \
           '.results[$suite] = {"status": $status, "message": $message, "metrics": $metrics, "timestamp": (now | strftime("%Y-%m-%dT%H:%M:%SZ"))}' \
           "$REPORT_FILE" > "${REPORT_FILE}.tmp" && mv "${REPORT_FILE}.tmp" "$REPORT_FILE"
    else
        echo "[$suite] $status: $message" >> "$TEST_RESULTS_DIR/test-log.txt"
    fi
}

call_api() {
    local method="$1"
    local endpoint="$2"
    local data="${3:-}"
    local expected_status="${4:-200}"
    
    local curl_cmd="curl -s -m 10"
    
    if [[ $data ]]; then
        curl_cmd="$curl_cmd -H 'Content-Type: application/json' -d '$data'"
    fi
    
    curl_cmd="$curl_cmd -w '%{http_code},%{time_total},%{time_connect}' '$GATEWAY_URL$endpoint'"
    
    local response
    response=$(eval "$curl_cmd" 2>/dev/null) || return 1
    local metrics="${response##*,}"
    local status_code="${response%,*}"
    status_code="${status_code##*,}"
    local body="${response%,*,*}"
    
    if [[ $status_code == "$expected_status" ]]; then
        if [[ $VERBOSE == true ]]; then
            log_info "API Response ($status_code): $body"
        fi
        echo "$metrics" # Return timing metrics
        return 0
    else
        if [[ $VERBOSE == true ]]; then
            log_error "API call failed. Status: $status_code, Body: $body"
        fi
        return 1
    fi
}

test_health_checks() {
    log "Running health check tests..."
    local failed=0
    local total=0
    
    # Test Gateway health
    ((total++))
    if call_api "GET" "/actuator/health" "" "200" >/dev/null; then
        log_success "Gateway health check passed"
    else
        log_error "Gateway health check failed"
        ((failed++))
    fi
    
    # Test Brain health
    ((total++))
    if curl -s -m 5 "$BRAIN_URL/healthz" | grep -q "healthy"; then
        log_success "Brain health check passed"
    else
        log_error "Brain health check failed"
        ((failed++))
    fi
    
    # Test migration status endpoint
    ((total++))
    if call_api "GET" "/api/v1/migration/status" "" "200" >/dev/null; then
        log_success "Migration status endpoint accessible"
    else
        log_error "Migration status endpoint failed"
        ((failed++))
    fi
    
    local success_rate=$(( (total - failed) * 100 / total ))
    update_report "health" "$([ $failed -eq 0 ] && echo "PASS" || echo "FAIL")" \
                  "$failed/$total tests failed" \
                  "{\"success_rate\": $success_rate, \"failed_tests\": $failed, \"total_tests\": $total}"
}

test_feature_flags() {
    log "Running feature flag tests..."
    local failed=0
    local total=0
    
    # Test feature flag listing
    ((total++))
    if call_api "GET" "/api/v1/migration/features" "" "200" >/dev/null; then
        log_success "Feature flags listing works"
    else
        log_error "Feature flags listing failed"
        ((failed++))
    fi
    
    # Test enabling a feature flag
    ((total++))
    if call_api "POST" "/api/v1/migration/features/grpc.job.submission?enabled=true" "" "200" >/dev/null; then
        log_success "Feature flag enablement works"
    else
        log_error "Feature flag enablement failed"
        ((failed++))
    fi
    
    # Test disabling a feature flag
    ((total++))
    if call_api "POST" "/api/v1/migration/features/grpc.job.submission?enabled=false" "" "200" >/dev/null; then
        log_success "Feature flag disablement works"
    else
        log_error "Feature flag disablement failed"
        ((failed++))
    fi
    
    local success_rate=$(( (total - failed) * 100 / total ))
    update_report "feature-flags" "$([ $failed -eq 0 ] && echo "PASS" || echo "FAIL")" \
                  "$failed/$total tests failed" \
                  "{\"success_rate\": $success_rate}"
}

test_performance() {
    log "Running performance tests for $TEST_DURATION seconds with $CONCURRENT_REQUESTS concurrent requests..."
    
    # Create test payload
    local test_payload='{
        "jd_url": "https://example.com/job/12345",
        "resume_uri": "s3://test-bucket/resume.pdf",
        "model_provider": "openai"
    }'
    
    local results_file="$TEST_RESULTS_DIR/performance-results.txt"
    local pids=()
    
    # Start concurrent load test processes
    for ((i=1; i<=CONCURRENT_REQUESTS; i++)); do
        {
            local request_count=0
            local success_count=0
            local total_time=0
            local start_time=$(date +%s)
            
            while [[ $(($(date +%s) - start_time)) -lt $TEST_DURATION ]]; do
                ((request_count++))
                
                if metrics=$(call_api "POST" "/api/v1/applications" "$test_payload" "200" 2>/dev/null); then
                    ((success_count++))
                    local response_time="${metrics##*,}"
                    total_time=$(echo "$total_time + $response_time" | bc -l 2>/dev/null || echo "$total_time")
                fi
                
                sleep 0.1 # Small delay between requests
            done
            
            echo "$i,$request_count,$success_count,$total_time" >> "$results_file"
        } &
        pids+=($!)
    done
    
    # Wait for all processes to complete
    for pid in "${pids[@]}"; do
        wait "$pid" || true
    done
    
    # Calculate aggregate metrics
    if [[ -f "$results_file" ]]; then
        local total_requests=0
        local total_successful=0
        local avg_response_time=0
        
        while IFS=, read -r worker requests success time; do
            total_requests=$((total_requests + requests))
            total_successful=$((total_successful + success))
            if [[ -n "$time" && "$time" != "0" ]]; then
                avg_response_time=$(echo "$avg_response_time + $time" | bc -l 2>/dev/null || echo "$avg_response_time")
            fi
        done < "$results_file"
        
        local success_rate=$(( total_successful * 100 / total_requests ))
        local rps=$(( total_successful / TEST_DURATION ))
        
        if [[ $total_successful -gt 0 && -n "$avg_response_time" ]]; then
            avg_response_time=$(echo "scale=3; $avg_response_time / $total_successful" | bc -l 2>/dev/null || echo "0")
        else
            avg_response_time=0
        fi
        
        log_success "Performance test completed: $total_successful/$total_requests successful ($success_rate%)"
        log_info "Requests per second: $rps"
        log_info "Average response time: ${avg_response_time}s"
        
        update_report "performance" "PASS" \
                      "Performance test completed" \
                      "{\"total_requests\": $total_requests, \"successful_requests\": $total_successful, \"success_rate\": $success_rate, \"rps\": $rps, \"avg_response_time\": $avg_response_time}"
    else
        log_error "Performance test failed to generate results"
        update_report "performance" "FAIL" "No results generated" "{}"
    fi
}

test_fallback_mechanisms() {
    log "Testing fallback mechanisms..."
    local failed=0
    local total=0
    
    # Enable gRPC with low traffic percentage
    ((total++))
    if call_api "POST" "/api/v1/migration/grpc/toggle?enabled=true" "" "200" >/dev/null; then
        log_success "gRPC enablement works"
    else
        log_error "gRPC enablement failed"
        ((failed++))
    fi
    
    # Set traffic percentage
    ((total++))
    if call_api "POST" "/api/v1/migration/grpc/traffic?percentage=25" "" "200" >/dev/null; then
        log_success "Traffic percentage setting works"
    else
        log_error "Traffic percentage setting failed"
        ((failed++))
    fi
    
    # Test emergency rollback
    ((total++))
    if call_api "POST" "/api/v1/migration/emergency-rollback" "" "200" >/dev/null; then
        log_success "Emergency rollback works"
    else
        log_error "Emergency rollback failed"
        ((failed++))
    fi
    
    local success_rate=$(( (total - failed) * 100 / total ))
    update_report "fallback" "$([ $failed -eq 0 ] && echo "PASS" || echo "FAIL")" \
                  "$failed/$total tests failed" \
                  "{\"success_rate\": $success_rate}"
}

test_security() {
    log "Testing mTLS security configuration..."
    local failed=0
    local total=0
    
    # Check if certificates exist
    ((total++))
    if [[ -f "$PROJECT_ROOT/infra/certs/ca-cert.pem" ]]; then
        log_success "CA certificate found"
    else
        log_warning "CA certificate not found - generating certificates"
        if "$SCRIPT_DIR/generate-certs.sh"; then
            log_success "Certificates generated successfully"
        else
            log_error "Certificate generation failed"
            ((failed++))
        fi
    fi
    
    # Test mTLS feature flag
    ((total++))
    if call_api "POST" "/api/v1/migration/features/mtls.internal.auth?enabled=true" "" "200" >/dev/null; then
        log_success "mTLS feature flag works"
    else
        log_error "mTLS feature flag failed"
        ((failed++))
    fi
    
    # Validate certificate chain
    ((total++))
    if [[ -f "$PROJECT_ROOT/infra/certs/gateway-cert.pem" ]] && \
       openssl verify -CAfile "$PROJECT_ROOT/infra/certs/ca-cert.pem" \
                      "$PROJECT_ROOT/infra/certs/gateway-cert.pem" >/dev/null 2>&1; then
        log_success "Certificate chain validation passed"
    else
        log_error "Certificate chain validation failed"
        ((failed++))
    fi
    
    local success_rate=$(( (total - failed) * 100 / total ))
    update_report "security" "$([ $failed -eq 0 ] && echo "PASS" || echo "FAIL")" \
                  "$failed/$total tests failed" \
                  "{\"success_rate\": $success_rate}"
}

test_migration_workflow() {
    log "Testing complete migration workflow..."
    local failed=0
    local phases=("validate" "generate-proto" "setup-servers" "configure-security" "setup-monitoring")
    
    for phase in "${phases[@]}"; do
        log_info "Testing migration phase: $phase"
        
        if "$SCRIPT_DIR/migrate-grpc.sh" --phase "$phase" --environment "$ENVIRONMENT" --dry-run; then
            log_success "Migration phase $phase validation passed"
        else
            log_error "Migration phase $phase validation failed"
            ((failed++))
        fi
    done
    
    local total=${#phases[@]}
    local success_rate=$(( (total - failed) * 100 / total ))
    
    update_report "migration" "$([ $failed -eq 0 ] && echo "PASS" || echo "FAIL")" \
                  "$failed/$total phases failed" \
                  "{\"success_rate\": $success_rate, \"phases_tested\": $total}"
}

generate_final_report() {
    if [[ $GENERATE_REPORT == true ]]; then
        log "Generating comprehensive test report..."
        
        local html_report="$TEST_RESULTS_DIR/test-report.html"
        
        cat > "$html_report" << 'EOF'
<!DOCTYPE html>
<html>
<head>
    <title>gRPC Migration Test Report</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; }
        .header { background-color: #f0f0f0; padding: 20px; border-radius: 5px; }
        .section { margin: 20px 0; padding: 15px; border: 1px solid #ddd; border-radius: 5px; }
        .pass { background-color: #d4edda; color: #155724; }
        .fail { background-color: #f8d7da; color: #721c24; }
        .metrics { background-color: #f8f9fa; padding: 10px; border-radius: 3px; font-family: monospace; }
        table { width: 100%; border-collapse: collapse; margin: 10px 0; }
        th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
        th { background-color: #f2f2f2; }
    </style>
</head>
<body>
    <div class="header">
        <h1>gRPC Migration Test Report</h1>
EOF
        
        if command -v jq >/dev/null 2>&1; then
            jq -r '.test_run | "        <p><strong>Environment:</strong> \(.environment)</p>
        <p><strong>Test Suite:</strong> \(.suite)</p>
        <p><strong>Timestamp:</strong> \(.timestamp)</p>
        <p><strong>Duration:</strong> \(.test_duration) seconds</p>
        <p><strong>Concurrent Requests:</strong> \(.concurrent_requests)</p>"' "$REPORT_FILE" >> "$html_report"
        fi
        
        echo "    </div>" >> "$html_report"
        
        # Add test results sections
        if command -v jq >/dev/null 2>&1; then
            jq -r '.results | to_entries[] | 
                "<div class=\"section \(.value.status | ascii_downcase)\">
                <h3>\(.key | gsub("-"; " ") | gsub("\\b\\w"; "\(.[:1] | ascii_upcase)\(.[1:])")) Test</h3>
                <p><strong>Status:</strong> \(.value.status)</p>
                <p><strong>Message:</strong> \(.value.message)</p>
                <p><strong>Timestamp:</strong> \(.value.timestamp)</p>
                <div class=\"metrics\"><pre>\(.value.metrics | tostring)</pre></div>
                </div>"' "$REPORT_FILE" >> "$html_report"
        fi
        
        cat >> "$html_report" << 'EOF'
</body>
</html>
EOF
        
        log_success "Test report generated: $html_report"
        
        # Also generate a summary
        local summary_file="$TEST_RESULTS_DIR/summary.txt"
        echo "gRPC Migration Test Summary" > "$summary_file"
        echo "==========================" >> "$summary_file"
        echo "Environment: $ENVIRONMENT" >> "$summary_file"
        echo "Suite: $TEST_SUITE" >> "$summary_file"
        echo "Timestamp: $(date)" >> "$summary_file"
        echo "" >> "$summary_file"
        
        if command -v jq >/dev/null 2>&1; then
            jq -r '.results | to_entries[] | "\(.key): \(.value.status) - \(.value.message)"' "$REPORT_FILE" >> "$summary_file"
        fi
        
        log_success "Test summary generated: $summary_file"
    fi
}

main() {
    log "Starting gRPC migration test suite: $TEST_SUITE"
    log_info "Environment: $ENVIRONMENT"
    log_info "Test results directory: $TEST_RESULTS_DIR"
    
    case $TEST_SUITE in
        health)
            test_health_checks
            ;;
        feature-flags)
            test_feature_flags
            ;;
        performance)
            test_performance
            ;;
        fallback)
            test_fallback_mechanisms
            ;;
        security)
            test_security
            ;;
        migration)
            test_migration_workflow
            ;;
        all)
            test_health_checks
            test_feature_flags
            test_fallback_mechanisms
            test_security
            test_migration_workflow
            test_performance  # Run performance test last as it's the longest
            ;;
        *)
            log_error "Unknown test suite: $TEST_SUITE"
            usage
            exit 1
            ;;
    esac
    
    generate_final_report
    
    log_success "Test suite '$TEST_SUITE' completed"
    log_info "Results available in: $TEST_RESULTS_DIR"
}

# Handle script interruption
trap 'log_error "Test interrupted"; exit 1' INT TERM

# Execute main function
main