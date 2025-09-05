#!/bin/bash

# HuskyApply Performance Testing Suite
# Comprehensive testing script with different load profiles

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
BASE_URL="${BASE_URL:-http://localhost:8080}"
REPORTS_DIR="reports/performance"
TEST_DATA_DIR="test-data"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)

# Helper functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check prerequisites
check_prerequisites() {
    log_info "Checking prerequisites..."
    
    # Check if k6 is installed
    if ! command -v k6 &> /dev/null; then
        log_error "k6 not found. Please install k6:"
        echo "  macOS: brew install k6"
        echo "  Linux: sudo apt-get install k6"
        echo "  Windows: choco install k6"
        exit 1
    fi
    
    # Check if API is accessible
    if ! curl -f -s "$BASE_URL/actuator/health" > /dev/null; then
        log_error "API health check failed. Is the service running at $BASE_URL?"
        exit 1
    fi
    
    # Create directories
    mkdir -p "$REPORTS_DIR"
    mkdir -p "$TEST_DATA_DIR"
    
    log_success "Prerequisites check passed"
}

# Setup test data
setup_test_data() {
    log_info "Setting up test data..."
    
    # Create test users (if they don't exist)
    local test_users=(
        "loadtest1@huskyapply.com:LoadTest123!"
        "loadtest2@huskyapply.com:LoadTest123!"
        "loadtest3@huskyapply.com:LoadTest123!"
        "loadtest4@huskyapply.com:LoadTest123!"
        "loadtest5@huskyapply.com:LoadTest123!"
    )
    
    for user_data in "${test_users[@]}"; do
        IFS=':' read -r email password <<< "$user_data"
        
        # Try to register user (ignore if already exists)
        curl -s -X POST "$BASE_URL/api/v1/auth/register" \
            -H "Content-Type: application/json" \
            -d "{\"email\":\"$email\",\"password\":\"$password\"}" \
            > /dev/null 2>&1 || true
    done
    
    log_success "Test data setup completed"
}

# Run smoke test
run_smoke_test() {
    log_info "Running smoke test (2 VUs, 1 minute)..."
    
    k6 run \
        --out json="$REPORTS_DIR/smoke-test-$TIMESTAMP.json" \
        --env BASE_URL="$BASE_URL" \
        --env SCENARIO=smoke \
        scripts/load-test.js
    
    log_success "Smoke test completed"
}

# Run load test
run_load_test() {
    log_info "Running load test (up to 20 VUs, 16 minutes)..."
    
    k6 run \
        --out json="$REPORTS_DIR/load-test-$TIMESTAMP.json" \
        --env BASE_URL="$BASE_URL" \
        --env SCENARIO=load \
        scripts/load-test.js
    
    log_success "Load test completed"
}

# Run stress test
run_stress_test() {
    log_info "Running stress test (up to 40 VUs, 29 minutes)..."
    
    k6 run \
        --out json="$REPORTS_DIR/stress-test-$TIMESTAMP.json" \
        --env BASE_URL="$BASE_URL" \
        --env SCENARIO=stress \
        scripts/load-test.js
    
    log_success "Stress test completed"
}

# Run spike test
run_spike_test() {
    log_info "Running spike test (up to 100 VUs, 6.5 minutes)..."
    
    k6 run \
        --out json="$REPORTS_DIR/spike-test-$TIMESTAMP.json" \
        --env BASE_URL="$BASE_URL" \
        --env SCENARIO=spike \
        scripts/load-test.js
    
    log_success "Spike test completed"
}

# Run endurance test
run_endurance_test() {
    log_info "Running endurance test (10 VUs, 60 minutes)..."
    
    k6 run \
        --out json="$REPORTS_DIR/endurance-test-$TIMESTAMP.json" \
        --env BASE_URL="$BASE_URL" \
        --stage "10m:10" \
        --stage "40m:10" \
        --stage "10m:0" \
        scripts/load-test.js
    
    log_success "Endurance test completed"
}

# Generate performance report
generate_report() {
    log_info "Generating performance report..."
    
    local report_file="$REPORTS_DIR/performance-summary-$TIMESTAMP.md"
    
    cat > "$report_file" << EOF
# HuskyApply Performance Test Report

**Test Date:** $(date)
**Base URL:** $BASE_URL
**Test Environment:** $(uname -s) $(uname -r)

## Test Results Summary

EOF

    # Process JSON reports and extract key metrics
    for json_file in "$REPORTS_DIR"/*-$TIMESTAMP.json; do
        if [[ -f "$json_file" ]]; then
            local test_type=$(basename "$json_file" | cut -d'-' -f1-2)
            
            echo "### $test_type Results" >> "$report_file"
            echo "" >> "$report_file"
            
            # Extract key metrics using jq if available
            if command -v jq &> /dev/null; then
                local avg_duration=$(jq -r '.metrics.http_req_duration.values.avg // "N/A"' "$json_file")
                local p95_duration=$(jq -r '.metrics.http_req_duration.values."p(95)" // "N/A"' "$json_file")
                local error_rate=$(jq -r '.metrics.http_req_failed.values.rate // "N/A"' "$json_file")
                local total_requests=$(jq -r '.metrics.http_reqs.values.count // "N/A"' "$json_file")
                
                cat >> "$report_file" << EOF
- **Average Response Time:** ${avg_duration}ms
- **95th Percentile Response Time:** ${p95_duration}ms
- **Error Rate:** ${error_rate}%
- **Total Requests:** $total_requests

EOF
            else
                echo "- Report: $json_file" >> "$report_file"
                echo "" >> "$report_file"
            fi
        fi
    done
    
    cat >> "$report_file" << EOF
## Recommendations

Based on the test results:

1. **Response Time:** Target < 2000ms for 95th percentile
2. **Error Rate:** Should be < 5% under normal load  
3. **Throughput:** Monitor requests per second capacity
4. **Resource Usage:** Check CPU, memory, and database connections

## Files Generated

- Performance reports: \`$REPORTS_DIR/\`
- Test data: \`$TEST_DATA_DIR/\`
- Summary report: \`$report_file\`

EOF

    log_success "Performance report generated: $report_file"
}

# Cleanup function
cleanup() {
    log_info "Cleaning up old test reports..."
    
    # Keep only last 10 test reports
    find "$REPORTS_DIR" -name "*.json" -type f | sort -r | tail -n +11 | xargs rm -f
    find "$REPORTS_DIR" -name "*.html" -type f | sort -r | tail -n +11 | xargs rm -f
    
    log_success "Cleanup completed"
}

# Main execution
main() {
    echo "🚀 HuskyApply Performance Testing Suite"
    echo "========================================"
    echo ""

    check_prerequisites
    setup_test_data

    case "${1:-all}" in
        smoke)
            run_smoke_test
            ;;
        load)
            run_load_test
            ;;
        stress)
            run_stress_test
            ;;
        spike)
            run_spike_test
            ;;
        endurance)
            run_endurance_test
            ;;
        all)
            log_info "Running complete performance test suite..."
            run_smoke_test
            sleep 30  # Brief pause between tests
            run_load_test
            sleep 30
            run_spike_test
            ;;
        cleanup)
            cleanup
            return 0
            ;;
        *)
            echo "Usage: $0 {smoke|load|stress|spike|endurance|all|cleanup}"
            echo ""
            echo "Test Types:"
            echo "  smoke      - Quick validation (2 VUs, 1 min)"
            echo "  load       - Normal load simulation (up to 20 VUs, 16 min)"
            echo "  stress     - Stress testing (up to 40 VUs, 29 min)"
            echo "  spike      - Spike testing (up to 100 VUs, 6.5 min)"
            echo "  endurance  - Long duration test (10 VUs, 60 min)"
            echo "  all        - Run smoke, load, and spike tests"
            echo "  cleanup    - Remove old test reports"
            echo ""
            echo "Environment Variables:"
            echo "  BASE_URL   - API base URL (default: http://localhost:8080)"
            exit 1
            ;;
    esac

    generate_report
    
    log_success "🎉 Performance testing completed!"
    echo ""
    echo "📊 View results in: $REPORTS_DIR/"
    echo "📋 Summary report: $REPORTS_DIR/performance-summary-$TIMESTAMP.md"
}

# Handle script interruption
trap 'log_warning "Test interrupted by user"; exit 130' INT

# Execute main function with all arguments
main "$@"