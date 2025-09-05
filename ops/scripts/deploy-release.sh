#!/bin/bash

# HuskyApply Production Release Deployment Script
# Handles blue-green deployments with rollback capability

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
NAMESPACE="${NAMESPACE:-huskyapply}"
REGISTRY="${REGISTRY:-ghcr.io/huskyapply/huskyapply}"
RELEASE_VERSION="${1:-}"
DEPLOYMENT_TIMEOUT="${DEPLOYMENT_TIMEOUT:-600}"
HEALTH_CHECK_TIMEOUT="${HEALTH_CHECK_TIMEOUT:-300}"
ROLLBACK_TIMEOUT="${ROLLBACK_TIMEOUT:-180}"

# Validate inputs
if [[ -z "$RELEASE_VERSION" ]]; then
    echo -e "${RED}Error: Release version not specified${NC}"
    echo "Usage: $0 <version>"
    echo "Example: $0 2.1.0"
    exit 1
fi

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
    
    # Check kubectl
    if ! command -v kubectl &> /dev/null; then
        log_error "kubectl not found. Please install kubectl."
        exit 1
    fi
    
    # Check helm
    if ! command -v helm &> /dev/null; then
        log_error "helm not found. Please install helm."
        exit 1
    fi
    
    # Check cluster connectivity
    if ! kubectl cluster-info &> /dev/null; then
        log_error "Cannot connect to Kubernetes cluster. Please check your kubeconfig."
        exit 1
    fi
    
    # Check namespace exists
    if ! kubectl get namespace "$NAMESPACE" &> /dev/null; then
        log_error "Namespace '$NAMESPACE' does not exist."
        exit 1
    fi
    
    log_success "Prerequisites check passed"
}

# Backup current deployment state
backup_current_state() {
    log_info "Backing up current deployment state..."
    
    BACKUP_DIR="backups/$(date +%Y%m%d-%H%M%S)-v${RELEASE_VERSION}"
    mkdir -p "$BACKUP_DIR"
    
    # Backup deployments
    kubectl get deployments -n "$NAMESPACE" -o yaml > "$BACKUP_DIR/deployments.yaml"
    kubectl get services -n "$NAMESPACE" -o yaml > "$BACKUP_DIR/services.yaml"
    kubectl get configmaps -n "$NAMESPACE" -o yaml > "$BACKUP_DIR/configmaps.yaml"
    kubectl get secrets -n "$NAMESPACE" -o yaml > "$BACKUP_DIR/secrets.yaml"
    kubectl get ingress -n "$NAMESPACE" -o yaml > "$BACKUP_DIR/ingress.yaml" || true
    
    # Backup Helm releases
    helm list -n "$NAMESPACE" -o yaml > "$BACKUP_DIR/helm-releases.yaml"
    
    log_success "Backup created at $BACKUP_DIR"
    echo "$BACKUP_DIR" > .last-backup-path
}

# Validate release artifacts
validate_release_artifacts() {
    log_info "Validating release artifacts..."
    
    # Check Docker images exist
    local images=("$REGISTRY-gateway:$RELEASE_VERSION" "$REGISTRY-brain:$RELEASE_VERSION")
    
    for image in "${images[@]}"; do
        if ! docker manifest inspect "$image" &> /dev/null; then
            log_error "Docker image $image not found"
            exit 1
        fi
    done
    
    # Verify Helm charts
    if [[ ! -f "infra/k8s/helm/gateway/Chart.yaml" ]] || [[ ! -f "infra/k8s/helm/brain/Chart.yaml" ]]; then
        log_error "Helm charts not found"
        exit 1
    fi
    
    log_success "Release artifacts validated"
}

# Pre-deployment health check
pre_deployment_health_check() {
    log_info "Running pre-deployment health check..."
    
    # Check current service health
    if ! ./scripts/health-check.sh --environment=production --timeout=60; then
        log_warning "Current deployment has health issues"
        read -p "Continue with deployment? (y/N): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            log_info "Deployment cancelled by user"
            exit 0
        fi
    fi
    
    # Check cluster resources
    local cpu_usage
    local memory_usage
    cpu_usage=$(kubectl top nodes --no-headers | awk '{sum+=$3} END {print sum/NR}' | cut -d'%' -f1)
    memory_usage=$(kubectl top nodes --no-headers | awk '{sum+=$5} END {print sum/NR}' | cut -d'%' -f1)
    
    if (( $(echo "$cpu_usage > 80" | bc -l) )); then
        log_warning "High CPU usage detected: ${cpu_usage}%"
    fi
    
    if (( $(echo "$memory_usage > 80" | bc -l) )); then
        log_warning "High memory usage detected: ${memory_usage}%"
    fi
    
    log_success "Pre-deployment health check completed"
}

# Deploy Gateway service
deploy_gateway() {
    log_info "Deploying Gateway service v$RELEASE_VERSION..."
    
    # Update Helm values
    helm upgrade --install gateway infra/k8s/helm/gateway \
        --namespace "$NAMESPACE" \
        --set image.tag="$RELEASE_VERSION" \
        --set deployment.strategy.type="RollingUpdate" \
        --set deployment.strategy.rollingUpdate.maxUnavailable="25%" \
        --set deployment.strategy.rollingUpdate.maxSurge="25%" \
        --timeout="${DEPLOYMENT_TIMEOUT}s" \
        --wait \
        --wait-for-jobs
    
    # Verify deployment
    kubectl rollout status deployment/gateway -n "$NAMESPACE" --timeout="${DEPLOYMENT_TIMEOUT}s"
    
    log_success "Gateway deployment completed"
}

# Deploy Brain service
deploy_brain() {
    log_info "Deploying Brain service v$RELEASE_VERSION..."
    
    # Update Helm values
    helm upgrade --install brain infra/k8s/helm/brain \
        --namespace "$NAMESPACE" \
        --set image.tag="$RELEASE_VERSION" \
        --set deployment.strategy.type="RollingUpdate" \
        --set deployment.strategy.rollingUpdate.maxUnavailable="25%" \
        --set deployment.strategy.rollingUpdate.maxSurge="25%" \
        --timeout="${DEPLOYMENT_TIMEOUT}s" \
        --wait \
        --wait-for-jobs
    
    # Verify deployment
    kubectl rollout status deployment/brain -n "$NAMESPACE" --timeout="${DEPLOYMENT_TIMEOUT}s"
    
    log_success "Brain deployment completed"
}

# Update infrastructure components
update_infrastructure() {
    log_info "Updating infrastructure components..."
    
    # Update ConfigMaps with version info
    kubectl create configmap app-version \
        --from-literal=version="$RELEASE_VERSION" \
        --from-literal=build-time="$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
        --from-literal=git-commit="${GITHUB_SHA:-unknown}" \
        -n "$NAMESPACE" \
        --dry-run=client -o yaml | kubectl apply -f -
    
    # Update ingress if needed
    if [[ -f "infra/k8s/ingress-production.yaml" ]]; then
        kubectl apply -f infra/k8s/ingress-production.yaml -n "$NAMESPACE"
    fi
    
    log_success "Infrastructure components updated"
}

# Post-deployment health check
post_deployment_health_check() {
    log_info "Running post-deployment health check..."
    
    local max_attempts=30
    local attempt=1
    local health_url="https://api.huskyapply.com/actuator/health"
    
    while [[ $attempt -le $max_attempts ]]; do
        log_info "Health check attempt $attempt/$max_attempts..."
        
        if curl -sf "$health_url" &> /dev/null; then
            log_success "Health check passed"
            return 0
        fi
        
        sleep 10
        ((attempt++))
    done
    
    log_error "Post-deployment health check failed"
    return 1
}

# Run smoke tests
run_smoke_tests() {
    log_info "Running smoke tests..."
    
    if [[ -f "scripts/smoke-tests.sh" ]]; then
        if ./scripts/smoke-tests.sh production; then
            log_success "Smoke tests passed"
        else
            log_error "Smoke tests failed"
            return 1
        fi
    else
        log_warning "Smoke tests script not found, skipping"
    fi
}

# Rollback deployment
rollback_deployment() {
    log_error "Initiating rollback..."
    
    local backup_path
    if [[ -f ".last-backup-path" ]]; then
        backup_path=$(cat .last-backup-path)
        log_info "Rolling back to backup: $backup_path"
        
        # Rollback Helm releases
        helm rollback gateway -n "$NAMESPACE"
        helm rollback brain -n "$NAMESPACE"
        
        # Wait for rollback to complete
        kubectl rollout status deployment/gateway -n "$NAMESPACE" --timeout="${ROLLBACK_TIMEOUT}s"
        kubectl rollout status deployment/brain -n "$NAMESPACE" --timeout="${ROLLBACK_TIMEOUT}s"
        
        log_success "Rollback completed"
    else
        log_error "No backup found for rollback"
        return 1
    fi
}

# Cleanup old deployments
cleanup_old_deployments() {
    log_info "Cleaning up old deployments..."
    
    # Remove old ReplicaSets (keep last 5)
    kubectl get replicasets -n "$NAMESPACE" --sort-by=.metadata.creationTimestamp | \
        head -n -5 | \
        awk 'NR>1 {print $1}' | \
        xargs -r kubectl delete replicaset -n "$NAMESPACE"
    
    # Cleanup old Docker images on nodes (if applicable)
    # This would typically be handled by a DaemonSet or external cleanup job
    
    log_success "Cleanup completed"
}

# Send deployment notifications
send_notifications() {
    local status="$1"
    local message="$2"
    
    log_info "Sending deployment notifications..."
    
    # Slack notification
    if [[ -n "${SLACK_WEBHOOK_URL:-}" ]]; then
        curl -X POST -H 'Content-type: application/json' \
            --data "{\"text\":\"🚀 HuskyApply Production Deployment\\n**Status:** $status\\n**Version:** $RELEASE_VERSION\\n**Message:** $message\"}" \
            "$SLACK_WEBHOOK_URL" || true
    fi
    
    # Discord notification
    if [[ -n "${DISCORD_WEBHOOK_URL:-}" ]]; then
        curl -X POST -H 'Content-type: application/json' \
            --data "{\"content\":\"🚀 **HuskyApply Production Deployment**\\n**Status:** $status\\n**Version:** $RELEASE_VERSION\\n**Message:** $message\"}" \
            "$DISCORD_WEBHOOK_URL" || true
    fi
}

# Main deployment function
main() {
    log_info "Starting HuskyApply production deployment v$RELEASE_VERSION"
    
    # Trap errors for rollback
    trap 'log_error "Deployment failed, initiating rollback..."; rollback_deployment; send_notifications "FAILED" "Deployment failed and was rolled back"; exit 1' ERR
    
    # Pre-deployment steps
    check_prerequisites
    backup_current_state
    validate_release_artifacts
    pre_deployment_health_check
    
    # Deployment steps
    update_infrastructure
    deploy_gateway
    deploy_brain
    
    # Post-deployment verification
    if ! post_deployment_health_check; then
        log_error "Post-deployment health check failed"
        rollback_deployment
        send_notifications "FAILED" "Health check failed, rolled back to previous version"
        exit 1
    fi
    
    if ! run_smoke_tests; then
        log_error "Smoke tests failed"
        rollback_deployment
        send_notifications "FAILED" "Smoke tests failed, rolled back to previous version"
        exit 1
    fi
    
    # Success cleanup
    cleanup_old_deployments
    send_notifications "SUCCESS" "Deployment completed successfully"
    
    log_success "HuskyApply v$RELEASE_VERSION deployed successfully to production! 🎉"
    log_info "Deployment summary:"
    log_info "• Version: $RELEASE_VERSION"
    log_info "• Gateway: $REGISTRY-gateway:$RELEASE_VERSION"
    log_info "• Brain: $REGISTRY-brain:$RELEASE_VERSION"
    log_info "• Backup location: $(cat .last-backup-path)"
    log_info "• Health check: https://api.huskyapply.com/actuator/health"
}

# Handle script arguments
case "${1:-deploy}" in
    deploy)
        if [[ -z "$RELEASE_VERSION" ]]; then
            log_error "Release version required for deployment"
            exit 1
        fi
        main
        ;;
    rollback)
        rollback_deployment
        ;;
    health-check)
        post_deployment_health_check
        ;;
    smoke-tests)
        run_smoke_tests
        ;;
    cleanup)
        cleanup_old_deployments
        ;;
    *)
        echo "Usage: $0 {deploy|rollback|health-check|smoke-tests|cleanup} [version]"
        echo
        echo "Commands:"
        echo "  deploy <version>    - Deploy specified version to production"
        echo "  rollback           - Rollback to previous version"
        echo "  health-check       - Run health check against current deployment"
        echo "  smoke-tests        - Run smoke tests against current deployment"
        echo "  cleanup           - Clean up old deployments"
        exit 1
        ;;
esac