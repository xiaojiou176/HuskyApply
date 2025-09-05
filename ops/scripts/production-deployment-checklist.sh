#!/bin/bash

# HuskyApply Production Deployment Checklist
# Comprehensive pre-deployment validation and post-deployment verification

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color

# Configuration
DEPLOYMENT_ENV="${1:-production}"
REGION="${2:-us-east-1}"
NAMESPACE="${3:-huskyapply}"
TIMEOUT="${TIMEOUT:-300}"
LOG_FILE="deployment-$(date +%Y%m%d-%H%M%S).log"

# Status tracking
TOTAL_CHECKS=0
PASSED_CHECKS=0
FAILED_CHECKS=0
WARNINGS=0

# Helper functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1" | tee -a "$LOG_FILE"
}

log_success() {
    echo -e "${GREEN}[✅ PASS]${NC} $1" | tee -a "$LOG_FILE"
    ((PASSED_CHECKS++))
}

log_error() {
    echo -e "${RED}[❌ FAIL]${NC} $1" | tee -a "$LOG_FILE"
    ((FAILED_CHECKS++))
}

log_warning() {
    echo -e "${YELLOW}[⚠️  WARN]${NC} $1" | tee -a "$LOG_FILE"
    ((WARNINGS++))
}

log_section() {
    echo -e "\n${PURPLE}=== $1 ===${NC}" | tee -a "$LOG_FILE"
}

increment_check() {
    ((TOTAL_CHECKS++))
}

check_command() {
    local cmd="$1"
    local description="$2"
    
    increment_check
    if command -v "$cmd" >/dev/null 2>&1; then
        log_success "$description: $cmd is available"
    else
        log_error "$description: $cmd is not installed or not in PATH"
        return 1
    fi
}

check_env_var() {
    local var_name="$1"
    local description="$2"
    local required="${3:-true}"
    
    increment_check
    if [[ -n "${!var_name:-}" ]]; then
        log_success "$description: $var_name is set"
    else
        if [[ "$required" == "true" ]]; then
            log_error "$description: Required environment variable $var_name is not set"
            return 1
        else
            log_warning "$description: Optional environment variable $var_name is not set"
        fi
    fi
}

check_file_exists() {
    local file_path="$1"
    local description="$2"
    local required="${3:-true}"
    
    increment_check
    if [[ -f "$file_path" ]]; then
        log_success "$description: $file_path exists"
    else
        if [[ "$required" == "true" ]]; then
            log_error "$description: Required file $file_path does not exist"
            return 1
        else
            log_warning "$description: Optional file $file_path does not exist"
        fi
    fi
}

check_url() {
    local url="$1"
    local description="$2"
    local expected_status="${3:-200}"
    
    increment_check
    local status_code
    if status_code=$(curl -s -o /dev/null -w "%{http_code}" -m 10 "$url"); then
        if [[ "$status_code" == "$expected_status" ]]; then
            log_success "$description: $url returned $status_code"
        else
            log_error "$description: $url returned $status_code, expected $expected_status"
            return 1
        fi
    else
        log_error "$description: Failed to connect to $url"
        return 1
    fi
}

check_kubernetes_resource() {
    local resource_type="$1"
    local resource_name="$2"
    local description="$3"
    
    increment_check
    if kubectl get "$resource_type" "$resource_name" -n "$NAMESPACE" >/dev/null 2>&1; then
        log_success "$description: $resource_type/$resource_name exists in namespace $NAMESPACE"
    else
        log_error "$description: $resource_type/$resource_name not found in namespace $NAMESPACE"
        return 1
    fi
}

check_pod_status() {
    local label_selector="$1"
    local description="$2"
    local min_ready="${3:-1}"
    
    increment_check
    local ready_pods
    if ready_pods=$(kubectl get pods -n "$NAMESPACE" -l "$label_selector" --field-selector=status.phase=Running -o json | jq -r '.items | length'); then
        if [[ "$ready_pods" -ge "$min_ready" ]]; then
            log_success "$description: $ready_pods pods ready (minimum: $min_ready)"
        else
            log_error "$description: Only $ready_pods pods ready, minimum required: $min_ready"
            return 1
        fi
    else
        log_error "$description: Failed to check pod status for selector $label_selector"
        return 1
    fi
}

# Main deployment checklist
main() {
    log_info "Starting HuskyApply Production Deployment Checklist"
    log_info "Environment: $DEPLOYMENT_ENV"
    log_info "Region: $REGION"
    log_info "Namespace: $NAMESPACE"
    log_info "Log file: $LOG_FILE"
    echo

    # Pre-deployment checks
    log_section "1. Prerequisites and Tools Check"
    
    check_command "kubectl" "Kubernetes CLI"
    check_command "docker" "Docker CLI"
    check_command "helm" "Helm package manager"
    check_command "jq" "JSON processor"
    check_command "curl" "HTTP client"
    check_command "psql" "PostgreSQL client"
    
    # Environment Variables Check
    log_section "2. Environment Variables Check"
    
    # Required variables
    check_env_var "DB_PASSWORD" "Database password"
    check_env_var "RABBITMQ_PASSWORD" "RabbitMQ password"
    check_env_var "JWT_SECRET_KEY" "JWT secret key"
    check_env_var "INTERNAL_API_KEY" "Internal API key"
    check_env_var "OPENAI_API_KEY" "OpenAI API key"
    
    # AWS Configuration
    check_env_var "AWS_ACCESS_KEY_ID" "AWS access key"
    check_env_var "AWS_SECRET_ACCESS_KEY" "AWS secret key"
    check_env_var "S3_BUCKET_NAME" "S3 bucket name"
    
    # Optional variables
    check_env_var "ANTHROPIC_API_KEY" "Anthropic API key" "false"
    check_env_var "STRIPE_SECRET_KEY" "Stripe secret key" "false"
    check_env_var "REDIS_PASSWORD" "Redis password" "false"
    
    # Configuration Files Check
    log_section "3. Configuration Files Check"
    
    check_file_exists "gateway/src/main/resources/application-prod.properties" "Gateway production config"
    check_file_exists "brain/config/production.py" "Brain production config"
    check_file_exists "frontend/config/config.production.js" "Frontend production config"
    check_file_exists "infra/k8s/production/namespace.yaml" "Kubernetes namespace config" "false"
    check_file_exists "infra/k8s/production/secrets.yaml" "Kubernetes secrets config" "false"
    
    # Security Files Check
    check_file_exists "infra/k8s/production/network-policies.yaml" "Network policies config" "false"
    check_file_exists "infra/k8s/production/rbac.yaml" "RBAC config" "false"
    check_file_exists "infra/k8s/production/pod-security-policies.yaml" "Pod security policies" "false"
    
    # Docker Images Check
    log_section "4. Docker Images Build and Push Check"
    
    increment_check
    if docker images | grep -q "huskyapply-gateway.*latest"; then
        log_success "Gateway Docker image exists locally"
    else
        log_warning "Gateway Docker image not found locally - will be built during deployment"
    fi
    
    increment_check
    if docker images | grep -q "huskyapply-brain.*latest"; then
        log_success "Brain Docker image exists locally"
    else
        log_warning "Brain Docker image not found locally - will be built during deployment"
    fi
    
    # Kubernetes Cluster Check
    log_section "5. Kubernetes Cluster Connectivity"
    
    increment_check
    if kubectl cluster-info >/dev/null 2>&1; then
        log_success "Kubernetes cluster is accessible"
        
        # Get cluster info
        local cluster_info
        cluster_info=$(kubectl cluster-info | head -1)
        log_info "Cluster info: $cluster_info"
    else
        log_error "Cannot connect to Kubernetes cluster"
    fi
    
    # Check if namespace exists
    increment_check
    if kubectl get namespace "$NAMESPACE" >/dev/null 2>&1; then
        log_success "Namespace $NAMESPACE exists"
    else
        log_warning "Namespace $NAMESPACE does not exist - will be created during deployment"
    fi
    
    # Database Connectivity Check
    log_section "6. External Dependencies Check"
    
    # PostgreSQL check
    if [[ -n "${DB_HOST:-}" ]]; then
        increment_check
        if PGPASSWORD="${DB_PASSWORD}" psql -h "${DB_HOST}" -p "${DB_PORT:-5432}" -U "${DB_USERNAME:-huskyapply_user}" -d "${DB_NAME:-huskyapply}" -c "SELECT 1;" >/dev/null 2>&1; then
            log_success "PostgreSQL database is accessible"
        else
            log_error "Cannot connect to PostgreSQL database"
        fi
    else
        log_warning "DB_HOST not set - assuming database will be available during deployment"
    fi
    
    # RabbitMQ check
    if [[ -n "${RABBITMQ_HOST:-}" ]]; then
        check_url "http://${RABBITMQ_HOST}:15672" "RabbitMQ Management UI"
    else
        log_warning "RABBITMQ_HOST not set - assuming RabbitMQ will be available during deployment"
    fi
    
    # Redis check
    if [[ -n "${REDIS_HOST:-}" ]]; then
        increment_check
        if redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT:-6379}" ${REDIS_PASSWORD:+-a "$REDIS_PASSWORD"} ping >/dev/null 2>&1; then
            log_success "Redis is accessible"
        else
            log_error "Cannot connect to Redis"
        fi
    else
        log_warning "REDIS_HOST not set - assuming Redis will be available during deployment"
    fi
    
    # SSL/TLS Certificates Check
    log_section "7. SSL/TLS Certificates Check"
    
    if [[ -n "${SSL_CERT_PATH:-}" ]]; then
        check_file_exists "$SSL_CERT_PATH" "SSL certificate"
        check_file_exists "$SSL_KEY_PATH" "SSL private key"
        
        increment_check
        if openssl x509 -in "$SSL_CERT_PATH" -noout -checkend 2592000 >/dev/null 2>&1; then
            log_success "SSL certificate is valid and not expiring within 30 days"
        else
            log_error "SSL certificate is invalid or expiring within 30 days"
        fi
    else
        log_warning "SSL_CERT_PATH not set - assuming TLS termination at load balancer"
    fi
    
    # Monitoring and Observability Check
    log_section "8. Monitoring and Observability Check"
    
    check_file_exists "infra/monitoring/prometheus/prometheus.yml" "Prometheus configuration" "false"
    check_file_exists "infra/monitoring/grafana/dashboards/" "Grafana dashboards directory" "false"
    check_file_exists "infra/monitoring/alertmanager/alertmanager.yml" "AlertManager configuration" "false"
    
    # Backup and Recovery Check
    log_section "9. Backup and Recovery Check"
    
    check_file_exists "scripts/backup-restore.sh" "Backup/restore script"
    
    increment_check
    if [[ -n "${S3_BACKUP_BUCKET:-}" ]]; then
        log_success "S3 backup bucket is configured: $S3_BACKUP_BUCKET"
    else
        log_warning "S3_BACKUP_BUCKET not set - backup to S3 will not work"
    fi
    
    # Performance and Load Testing
    log_section "10. Performance Testing Setup"
    
    check_file_exists "scripts/load-test.js" "K6 load test script"
    check_file_exists "scripts/performance-test-suite.sh" "Performance test suite"
    
    increment_check
    if command -v k6 >/dev/null 2>&1; then
        log_success "K6 load testing tool is available"
    else
        log_warning "K6 not installed - performance testing will not be available"
    fi
    
    # Security Scanning
    log_section "11. Security Scanning"
    
    increment_check
    if command -v trivy >/dev/null 2>&1; then
        log_success "Trivy security scanner is available"
        
        # Scan Docker images if they exist
        if docker images | grep -q "huskyapply-gateway"; then
            log_info "Running Trivy scan on Gateway image..."
            if trivy image --exit-code 1 --severity HIGH,CRITICAL huskyapply-gateway:latest >/dev/null 2>&1; then
                log_success "Gateway image passed security scan"
            else
                log_error "Gateway image failed security scan (HIGH/CRITICAL vulnerabilities found)"
            fi
        fi
        
        if docker images | grep -q "huskyapply-brain"; then
            log_info "Running Trivy scan on Brain image..."
            if trivy image --exit-code 1 --severity HIGH,CRITICAL huskyapply-brain:latest >/dev/null 2>&1; then
                log_success "Brain image passed security scan"
            else
                log_error "Brain image failed security scan (HIGH/CRITICAL vulnerabilities found)"
            fi
        fi
    else
        log_warning "Trivy not installed - security scanning will not be performed"
    fi
    
    # CI/CD Pipeline Check
    log_section "12. CI/CD Pipeline Check"
    
    check_file_exists ".github/workflows/ci.yml" "CI workflow"
    check_file_exists ".github/workflows/cd.yml" "CD workflow" "false"
    check_file_exists ".github/workflows/performance-tests.yml" "Performance testing workflow"
    
    # Final Summary
    log_section "Deployment Checklist Summary"
    
    echo -e "\n${BLUE}📊 DEPLOYMENT CHECKLIST RESULTS${NC}" | tee -a "$LOG_FILE"
    echo -e "Total Checks: ${TOTAL_CHECKS}" | tee -a "$LOG_FILE"
    echo -e "${GREEN}Passed: ${PASSED_CHECKS}${NC}" | tee -a "$LOG_FILE"
    echo -e "${RED}Failed: ${FAILED_CHECKS}${NC}" | tee -a "$LOG_FILE"
    echo -e "${YELLOW}Warnings: ${WARNINGS}${NC}" | tee -a "$LOG_FILE"
    
    local pass_rate
    if [[ $TOTAL_CHECKS -gt 0 ]]; then
        pass_rate=$((PASSED_CHECKS * 100 / TOTAL_CHECKS))
        echo -e "Pass Rate: ${pass_rate}%" | tee -a "$LOG_FILE"
    fi
    
    echo | tee -a "$LOG_FILE"
    
    if [[ $FAILED_CHECKS -eq 0 ]]; then
        echo -e "${GREEN}🎉 DEPLOYMENT CHECKLIST PASSED!${NC}" | tee -a "$LOG_FILE"
        echo -e "${GREEN}✅ System is ready for production deployment${NC}" | tee -a "$LOG_FILE"
        
        if [[ $WARNINGS -gt 0 ]]; then
            echo -e "${YELLOW}⚠️  Please review ${WARNINGS} warnings before proceeding${NC}" | tee -a "$LOG_FILE"
        fi
        
        return 0
    else
        echo -e "${RED}❌ DEPLOYMENT CHECKLIST FAILED!${NC}" | tee -a "$LOG_FILE"
        echo -e "${RED}🚫 Please fix ${FAILED_CHECKS} critical issues before deployment${NC}" | tee -a "$LOG_FILE"
        
        echo -e "\n${RED}CRITICAL ISSUES TO FIX:${NC}" | tee -a "$LOG_FILE"
        echo -e "1. Review failed checks in the log above" | tee -a "$LOG_FILE"
        echo -e "2. Ensure all required environment variables are set" | tee -a "$LOG_FILE"
        echo -e "3. Verify external dependencies are accessible" | tee -a "$LOG_FILE"
        echo -e "4. Check configuration files are present and valid" | tee -a "$LOG_FILE"
        
        return 1
    fi
}

# Post-deployment verification
post_deployment_check() {
    log_section "Post-Deployment Verification"
    
    local base_url="${BASE_URL:-http://localhost:8080}"
    
    # Wait for pods to be ready
    log_info "Waiting for pods to be ready..."
    sleep 30
    
    # Check pod status
    check_pod_status "app=huskyapply-gateway" "Gateway pods"
    check_pod_status "app=huskyapply-brain" "Brain pods"
    
    # Health checks
    check_url "$base_url/actuator/health" "Gateway health check"
    check_url "${base_url/8080/8000}/healthz" "Brain health check"
    
    # API functionality checks
    increment_check
    if curl -s "$base_url/api/v1/subscriptions/plans" | jq -e '. | length > 0' >/dev/null 2>&1; then
        log_success "API endpoints are responding correctly"
    else
        log_error "API endpoints are not responding correctly"
    fi
    
    # Database connectivity
    increment_check
    local db_status
    if db_status=$(curl -s "$base_url/actuator/health" | jq -r '.components.db.status'); then
        if [[ "$db_status" == "UP" ]]; then
            log_success "Database connectivity is healthy"
        else
            log_error "Database connectivity is not healthy: $db_status"
        fi
    else
        log_error "Cannot check database health status"
    fi
    
    # Message queue connectivity
    increment_check
    local rabbit_status
    if rabbit_status=$(curl -s "$base_url/actuator/health" | jq -r '.components.rabbit.status'); then
        if [[ "$rabbit_status" == "UP" ]]; then
            log_success "RabbitMQ connectivity is healthy"
        else
            log_error "RabbitMQ connectivity is not healthy: $rabbit_status"
        fi
    else
        log_error "Cannot check RabbitMQ health status"
    fi
    
    log_info "Post-deployment verification completed"
}

# Usage information
usage() {
    echo "Usage: $0 [environment] [region] [namespace]"
    echo
    echo "Arguments:"
    echo "  environment    Target environment (default: production)"
    echo "  region         AWS region (default: us-east-1)"
    echo "  namespace      Kubernetes namespace (default: huskyapply)"
    echo
    echo "Environment Variables:"
    echo "  BASE_URL                Gateway base URL for post-deployment checks"
    echo "  TIMEOUT                 Timeout for various operations (default: 300)"
    echo "  DB_HOST                 Database hostname"
    echo "  RABBITMQ_HOST          RabbitMQ hostname"
    echo "  REDIS_HOST             Redis hostname"
    echo
    echo "Examples:"
    echo "  $0                                    # Check production deployment"
    echo "  $0 staging us-west-2                 # Check staging in us-west-2"
    echo "  $0 production us-east-1 huskyapply   # Full specification"
    echo "  BASE_URL=https://api.huskyapply.com $0 production  # With custom URL"
}

# Main execution
if [[ "${1:-}" == "--help" ]] || [[ "${1:-}" == "-h" ]]; then
    usage
    exit 0
fi

if [[ "${1:-}" == "--post-deployment" ]]; then
    shift
    post_deployment_check "$@"
else
    main "$@"
fi