#!/bin/bash
set -euo pipefail

# Migration script for gRPC implementation
# Supports phased rollout with zero-downtime deployment

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Default values
ENVIRONMENT="local"
PHASE="validate"
TRAFFIC_PERCENTAGE=0
DRY_RUN=false
VERBOSE=false

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

usage() {
    cat << EOF
Usage: $0 [OPTIONS]

Migrate HuskyApply from HTTP/RabbitMQ to gRPC communication with zero-downtime deployment.

OPTIONS:
    -e, --environment ENV    Environment (local, staging, production) [default: local]
    -p, --phase PHASE        Migration phase to execute [default: validate]
    -t, --traffic PERCENT    Traffic percentage to route through gRPC (0-100) [default: 0]
    -n, --dry-run           Show what would be done without executing
    -v, --verbose           Enable verbose output
    -h, --help              Show this help message

MIGRATION PHASES:
    validate               Validate migration readiness
    generate-proto         Generate protobuf code
    setup-servers          Start gRPC servers
    configure-security     Configure mTLS certificates
    setup-monitoring       Configure metrics and tracing
    start-rollout         Begin traffic splitting (5% -> 25% -> 50% -> 75% -> 100%)
    complete              Complete migration and cleanup

EXAMPLES:
    $0 --phase validate --environment staging
    $0 --phase start-rollout --traffic 25 --environment production
    $0 --phase complete --environment production --verbose

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

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -e|--environment)
            ENVIRONMENT="$2"
            shift 2
            ;;
        -p|--phase)
            PHASE="$2"
            shift 2
            ;;
        -t|--traffic)
            TRAFFIC_PERCENTAGE="$2"
            shift 2
            ;;
        -n|--dry-run)
            DRY_RUN=true
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

# Validate environment
case $ENVIRONMENT in
    local|staging|production)
        ;;
    *)
        log_error "Invalid environment: $ENVIRONMENT"
        exit 1
        ;;
esac

# Validate traffic percentage
if [[ $TRAFFIC_PERCENTAGE -lt 0 || $TRAFFIC_PERCENTAGE -gt 100 ]]; then
    log_error "Traffic percentage must be between 0 and 100"
    exit 1
fi

# Set environment-specific configurations
case $ENVIRONMENT in
    local)
        GATEWAY_URL="http://localhost:8080"
        BRAIN_URL="http://localhost:8000"
        ;;
    staging)
        GATEWAY_URL="https://staging-api.huskyapply.com"
        BRAIN_URL="http://brain-staging:8000"
        ;;
    production)
        GATEWAY_URL="https://api.huskyapply.com"
        BRAIN_URL="http://brain-production:8000"
        ;;
esac

execute_command() {
    local cmd="$1"
    local description="${2:-}"
    
    if [[ $VERBOSE == true ]]; then
        log "Executing: $cmd"
    fi
    
    if [[ $description ]]; then
        log "$description"
    fi
    
    if [[ $DRY_RUN == true ]]; then
        log_warning "[DRY RUN] Would execute: $cmd"
        return 0
    fi
    
    if eval "$cmd"; then
        log_success "${description:-Command} completed successfully"
        return 0
    else
        log_error "${description:-Command} failed"
        return 1
    fi
}

call_api() {
    local method="$1"
    local endpoint="$2"
    local data="${3:-}"
    local expected_status="${4:-200}"
    
    local curl_cmd="curl -s -X $method"
    
    if [[ $data ]]; then
        curl_cmd="$curl_cmd -H 'Content-Type: application/json' -d '$data'"
    fi
    
    curl_cmd="$curl_cmd -w '%{http_code}' '$GATEWAY_URL$endpoint'"
    
    if [[ $DRY_RUN == true ]]; then
        log_warning "[DRY RUN] Would call API: $method $endpoint"
        return 0
    fi
    
    local response
    response=$(eval "$curl_cmd")
    local status_code="${response: -3}"
    local body="${response%???}"
    
    if [[ $status_code == "$expected_status" ]]; then
        if [[ $VERBOSE == true && $body ]]; then
            log "API Response: $body"
        fi
        return 0
    else
        log_error "API call failed. Status: $status_code, Body: $body"
        return 1
    fi
}

validate_migration() {
    log "Validating migration readiness..."
    
    # Check if services are running
    if ! call_api "GET" "/actuator/health"; then
        log_error "Gateway service is not healthy"
        return 1
    fi
    
    # Validate migration prerequisites
    if ! call_api "GET" "/api/v1/migration/validate"; then
        log_error "Migration validation failed"
        return 1
    fi
    
    # Check protobuf generation
    if [[ -f "$PROJECT_ROOT/gateway/src/main/java/com/huskyapply/grpc/jobprocessing/v1/JobProcessingServiceGrpc.java" ]]; then
        log_success "Gateway protobuf classes found"
    else
        log_warning "Gateway protobuf classes not found"
    fi
    
    if [[ -f "$PROJECT_ROOT/brain/generated/job_processing_pb2.py" ]]; then
        log_success "Brain protobuf classes found"
    else
        log_warning "Brain protobuf classes not found"
    fi
    
    log_success "Migration validation completed"
}

generate_protobuf() {
    log "Generating protobuf code..."
    
    # Generate Java protobuf classes
    execute_command "cd '$PROJECT_ROOT/gateway' && mvn protobuf:compile" "Generating Java protobuf classes"
    
    # Generate Python protobuf classes
    execute_command "cd '$PROJECT_ROOT/brain' && python -m grpc_tools.protoc -I../infra/proto --python_out=generated --grpc_python_out=generated ../infra/proto/*.proto" "Generating Python protobuf classes"
    
    # Mark phase as completed
    if call_api "POST" "/api/v1/migration/phases/protobuf_generation/complete"; then
        log_success "Protobuf generation phase marked as completed"
    fi
}

setup_grpc_servers() {
    log "Setting up gRPC servers..."
    
    # Start gRPC server in Gateway
    execute_command "cd '$PROJECT_ROOT/infra' && docker-compose restart gateway" "Restarting Gateway with gRPC support"
    
    # Start gRPC server in Brain
    execute_command "cd '$PROJECT_ROOT/infra' && docker-compose restart brain" "Restarting Brain with gRPC support"
    
    # Wait for services to be healthy
    sleep 10
    
    # Verify gRPC servers are running
    if call_api "GET" "/actuator/health"; then
        log_success "Gateway gRPC server is healthy"
    else
        log_error "Gateway gRPC server health check failed"
        return 1
    fi
    
    # Mark phase as completed
    if call_api "POST" "/api/v1/migration/phases/grpc_server_setup/complete"; then
        log_success "gRPC server setup phase marked as completed"
    fi
}

configure_security() {
    log "Configuring mTLS security..."
    
    # Generate development certificates if in local environment
    if [[ $ENVIRONMENT == "local" ]]; then
        execute_command "$SCRIPT_DIR/generate-certs.sh" "Generating development certificates"
    fi
    
    # Enable mTLS feature flag
    if call_api "POST" "/api/v1/migration/features/mtls.internal.auth?enabled=true"; then
        log_success "mTLS feature flag enabled"
    fi
    
    # Mark phase as completed
    if call_api "POST" "/api/v1/migration/phases/security_configuration/complete"; then
        log_success "Security configuration phase marked as completed"
    fi
}

setup_monitoring() {
    log "Setting up monitoring and metrics..."
    
    # Enable metrics collection feature flag
    if call_api "POST" "/api/v1/migration/features/grpc.metrics.collection?enabled=true"; then
        log_success "gRPC metrics collection enabled"
    fi
    
    # Update Prometheus configuration
    if [[ -f "$PROJECT_ROOT/infra/monitoring/prometheus/prometheus.yml" ]]; then
        log "Prometheus configuration already exists"
    else
        log_warning "Prometheus configuration not found"
    fi
    
    # Mark phase as completed
    if call_api "POST" "/api/v1/migration/phases/monitoring_integration/complete"; then
        log_success "Monitoring integration phase marked as completed"
    fi
}

start_rollout() {
    log "Starting gRPC traffic rollout..."
    
    # Enable gRPC globally
    if call_api "POST" "/api/v1/migration/grpc/toggle?enabled=true"; then
        log_success "gRPC communication enabled globally"
    fi
    
    # Set initial traffic percentage
    if call_api "POST" "/api/v1/migration/grpc/traffic?percentage=$TRAFFIC_PERCENTAGE"; then
        log_success "gRPC traffic set to $TRAFFIC_PERCENTAGE%"
    fi
    
    # Enable feature flags based on traffic percentage
    if [[ $TRAFFIC_PERCENTAGE -ge 25 ]]; then
        call_api "POST" "/api/v1/migration/features/grpc.job.submission?enabled=true"
        log_success "gRPC job submission enabled"
    fi
    
    if [[ $TRAFFIC_PERCENTAGE -ge 50 ]]; then
        call_api "POST" "/api/v1/migration/features/grpc.status.updates?enabled=true" 
        log_success "gRPC status updates enabled"
    fi
    
    if [[ $TRAFFIC_PERCENTAGE -ge 75 ]]; then
        call_api "POST" "/api/v1/migration/features/grpc.batch.processing?enabled=true"
        log_success "gRPC batch processing enabled"
    fi
    
    # Monitor for 30 seconds
    log "Monitoring system health for 30 seconds..."
    for i in {1..6}; do
        if call_api "GET" "/api/v1/migration/status" "" "200" > /dev/null; then
            log "Health check $i/6 passed"
        else
            log_error "Health check $i/6 failed - consider rollback"
            return 1
        fi
        sleep 5
    done
    
    log_success "Traffic rollout completed successfully"
}

complete_migration() {
    log "Completing gRPC migration..."
    
    # Set traffic to 100%
    if call_api "POST" "/api/v1/migration/grpc/traffic?percentage=100"; then
        log_success "All traffic routed through gRPC"
    fi
    
    # Enable all gRPC features
    call_api "POST" "/api/v1/migration/features/grpc.job.submission?enabled=true"
    call_api "POST" "/api/v1/migration/features/grpc.status.updates?enabled=true"
    call_api "POST" "/api/v1/migration/features/grpc.batch.processing?enabled=true"
    call_api "POST" "/api/v1/migration/features/grpc.metrics.collection?enabled=true"
    
    # Mark final phase as completed
    if call_api "POST" "/api/v1/migration/phases/full_migration/complete"; then
        log_success "Full migration phase marked as completed"
    fi
    
    log_success "gRPC migration completed successfully!"
}

emergency_rollback() {
    log_warning "Executing emergency rollback..."
    
    if call_api "POST" "/api/v1/migration/emergency-rollback"; then
        log_success "Emergency rollback completed"
    else
        log_error "Emergency rollback failed"
        return 1
    fi
}

main() {
    log "Starting gRPC migration - Environment: $ENVIRONMENT, Phase: $PHASE"
    
    if [[ $DRY_RUN == true ]]; then
        log_warning "Running in DRY RUN mode - no changes will be made"
    fi
    
    case $PHASE in
        validate)
            validate_migration
            ;;
        generate-proto)
            generate_protobuf
            ;;
        setup-servers)
            setup_grpc_servers
            ;;
        configure-security)
            configure_security
            ;;
        setup-monitoring)
            setup_monitoring
            ;;
        start-rollout)
            start_rollout
            ;;
        complete)
            complete_migration
            ;;
        rollback)
            emergency_rollback
            ;;
        full-migration)
            # Execute all phases in sequence
            validate_migration && \
            generate_protobuf && \
            setup_grpc_servers && \
            configure_security && \
            setup_monitoring && \
            start_rollout && \
            complete_migration
            ;;
        *)
            log_error "Unknown phase: $PHASE"
            usage
            exit 1
            ;;
    esac
    
    log_success "Migration phase '$PHASE' completed successfully"
}

# Handle script interruption
trap 'log_error "Script interrupted"; exit 1' INT TERM

# Execute main function
main