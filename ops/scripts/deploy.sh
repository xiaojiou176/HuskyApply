#!/bin/bash

# HuskyApply Kubernetes Deployment Script
# Usage: ./deploy.sh [environment] [region]
# Example: ./deploy.sh production us-east-1

set -euo pipefail

# Configuration
ENVIRONMENT=${1:-staging}
REGION=${2:-us-east-1}
PROJECT_NAME="huskyapply"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
K8S_DIR="${SCRIPT_DIR}/../k8s"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
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

# Validate prerequisites
validate_prerequisites() {
    log_info "Validating prerequisites..."
    
    # Check if kubectl is installed
    if ! command -v kubectl &> /dev/null; then
        log_error "kubectl is not installed. Please install kubectl first."
        exit 1
    fi
    
    # Check if helm is installed
    if ! command -v helm &> /dev/null; then
        log_error "Helm is not installed. Please install Helm first."
        exit 1
    fi
    
    # Check if we can access the cluster
    if ! kubectl cluster-info &> /dev/null; then
        log_error "Cannot access Kubernetes cluster. Please check your kubeconfig."
        exit 1
    fi
    
    # Check if docker is installed (for building images)
    if ! command -v docker &> /dev/null; then
        log_warning "Docker is not installed. Image building will be skipped."
    fi
    
    log_success "Prerequisites validated successfully"
}

# Build and push Docker images
build_and_push_images() {
    log_info "Building and pushing Docker images..."
    
    if ! command -v docker &> /dev/null; then
        log_warning "Docker not available, skipping image build"
        return 0
    fi
    
    # Set image registry based on environment
    if [[ "$ENVIRONMENT" == "production" ]]; then
        REGISTRY="huskyapply.azurecr.io"
    else
        REGISTRY="huskyapply-staging.azurecr.io"
    fi
    
    # Build Gateway image
    log_info "Building Gateway image..."
    cd "${SCRIPT_DIR}/../gateway"
    docker build -t "${REGISTRY}/gateway:latest" -t "${REGISTRY}/gateway:$(git rev-parse --short HEAD)" .
    
    # Build Brain image
    log_info "Building Brain image..."
    cd "${SCRIPT_DIR}/../brain"
    docker build -t "${REGISTRY}/brain:latest" -t "${REGISTRY}/brain:$(git rev-parse --short HEAD)" .
    
    # Push images
    log_info "Pushing images to registry..."
    docker push "${REGISTRY}/gateway:latest"
    docker push "${REGISTRY}/gateway:$(git rev-parse --short HEAD)"
    docker push "${REGISTRY}/brain:latest"
    docker push "${REGISTRY}/brain:$(git rev-parse --short HEAD)"
    
    log_success "Images built and pushed successfully"
}

# Setup namespace and RBAC
setup_namespace() {
    log_info "Setting up namespace and RBAC..."
    
    # Apply namespace
    kubectl apply -f "${K8S_DIR}/namespace.yaml"
    
    # Set current context to the namespace
    kubectl config set-context --current --namespace="${PROJECT_NAME}"
    
    log_success "Namespace setup completed"
}

# Deploy infrastructure components
deploy_infrastructure() {
    log_info "Deploying infrastructure components..."
    
    # Deploy PostgreSQL
    log_info "Deploying PostgreSQL..."
    kubectl apply -f "${K8S_DIR}/postgres.yaml"
    
    # Deploy RabbitMQ
    log_info "Deploying RabbitMQ..."
    kubectl apply -f "${K8S_DIR}/rabbitmq.yaml"
    
    # Deploy Redis
    log_info "Deploying Redis..."
    kubectl apply -f "${K8S_DIR}/redis.yaml"
    
    # Wait for infrastructure to be ready
    log_info "Waiting for infrastructure components to be ready..."
    kubectl wait --for=condition=ready pod -l app=postgres --timeout=300s || true
    kubectl wait --for=condition=ready pod -l app=rabbitmq --timeout=300s || true
    kubectl wait --for=condition=ready pod -l app=redis --timeout=300s || true
    
    log_success "Infrastructure deployment completed"
}

# Deploy application components
deploy_applications() {
    log_info "Deploying application components..."
    
    # Apply ConfigMaps and Secrets
    log_info "Applying ConfigMaps and Secrets..."
    kubectl apply -f "${K8S_DIR}/configmap.yaml"
    
    # Handle secrets (warn about production security)
    if [[ "$ENVIRONMENT" == "production" ]]; then
        log_warning "PRODUCTION DEPLOYMENT: Please ensure secrets are properly configured!"
        log_warning "Update all CHANGE_ME_* values in secrets.yaml before applying!"
        read -p "Have you updated all production secrets? (y/N): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            log_error "Please update production secrets before deploying"
            exit 1
        fi
    fi
    
    kubectl apply -f "${K8S_DIR}/secrets.yaml"
    
    # Deploy Gateway
    log_info "Deploying Gateway service..."
    kubectl apply -f "${K8S_DIR}/gateway.yaml"
    
    # Deploy Brain
    log_info "Deploying Brain service..."
    kubectl apply -f "${K8S_DIR}/brain.yaml"
    
    # Deploy Frontend
    log_info "Deploying Frontend..."
    kubectl apply -f "${K8S_DIR}/frontend.yaml"
    
    # Wait for applications to be ready
    log_info "Waiting for applications to be ready..."
    kubectl wait --for=condition=ready pod -l app=gateway --timeout=300s || true
    kubectl wait --for=condition=ready pod -l app=brain --timeout=300s || true
    kubectl wait --for=condition=ready pod -l app=frontend --timeout=300s || true
    
    log_success "Application deployment completed"
}

# Deploy networking and ingress
deploy_networking() {
    log_info "Deploying networking and ingress..."
    
    # Install cert-manager if not exists
    if ! kubectl get namespace cert-manager &> /dev/null; then
        log_info "Installing cert-manager..."
        helm repo add jetstack https://charts.jetstack.io
        helm repo update
        helm install cert-manager jetstack/cert-manager \
            --namespace cert-manager \
            --create-namespace \
            --set installCRDs=true
        
        # Wait for cert-manager to be ready
        kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=cert-manager -n cert-manager --timeout=120s
    fi
    
    # Apply ingress configuration
    kubectl apply -f "${K8S_DIR}/ingress.yaml"
    
    log_success "Networking deployment completed"
}

# Setup monitoring
setup_monitoring() {
    log_info "Setting up monitoring..."
    
    # Install Prometheus Operator if not exists
    if ! helm list -n monitoring | grep -q prometheus-operator; then
        log_info "Installing Prometheus Operator..."
        helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
        helm repo update
        helm install prometheus-operator prometheus-community/kube-prometheus-stack \
            --namespace monitoring \
            --create-namespace \
            --set prometheus.prometheusSpec.serviceMonitorSelectorNilUsesHelmValues=false \
            --set prometheus.prometheusSpec.retention=30d \
            --set grafana.adminPassword=admin123
    fi
    
    # Apply monitoring configuration
    kubectl apply -f "${K8S_DIR}/monitoring.yaml"
    
    log_success "Monitoring setup completed"
}

# Verify deployment
verify_deployment() {
    log_info "Verifying deployment..."
    
    echo
    echo "=== Deployment Status ==="
    kubectl get pods -o wide
    echo
    kubectl get services
    echo
    kubectl get ingress
    echo
    
    # Check application health
    log_info "Checking application health..."
    
    # Wait a bit for services to stabilize
    sleep 10
    
    # Test Gateway health
    if kubectl get svc gateway-service &> /dev/null; then
        GATEWAY_PORT=$(kubectl get svc gateway-service -o jsonpath='{.spec.ports[0].port}')
        kubectl port-forward svc/gateway-service 8080:${GATEWAY_PORT} &
        PORT_FORWARD_PID=$!
        sleep 5
        
        if curl -f http://localhost:8080/actuator/health &> /dev/null; then
            log_success "Gateway health check passed"
        else
            log_warning "Gateway health check failed"
        fi
        
        kill $PORT_FORWARD_PID &> /dev/null || true
    fi
    
    # Test Brain health
    if kubectl get svc brain-service &> /dev/null; then
        BRAIN_PORT=$(kubectl get svc brain-service -o jsonpath='{.spec.ports[0].port}')
        kubectl port-forward svc/brain-service 8000:${BRAIN_PORT} &
        PORT_FORWARD_PID=$!
        sleep 5
        
        if curl -f http://localhost:8000/healthz &> /dev/null; then
            log_success "Brain health check passed"
        else
            log_warning "Brain health check failed"
        fi
        
        kill $PORT_FORWARD_PID &> /dev/null || true
    fi
    
    log_success "Deployment verification completed"
}

# Print access information
print_access_info() {
    echo
    echo "=================================================="
    echo "🎉 HuskyApply Deployment Complete!"
    echo "=================================================="
    echo
    echo "Environment: $ENVIRONMENT"
    echo "Region: $REGION"
    echo "Namespace: $PROJECT_NAME"
    echo
    echo "Access URLs:"
    
    if [[ "$ENVIRONMENT" == "production" ]]; then
        echo "  Main App: https://huskyapply.com"
        echo "  API: https://api.huskyapply.com"
    else
        INGRESS_IP=$(kubectl get ingress huskyapply-ingress -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "pending")
        echo "  Main App: http://$INGRESS_IP (or use port-forward)"
        echo "  API: http://$INGRESS_IP/api"
    fi
    
    echo
    echo "Monitoring:"
    echo "  Grafana: kubectl port-forward -n monitoring svc/prometheus-operator-grafana 3000:80"
    echo "  Prometheus: kubectl port-forward -n monitoring svc/prometheus-operator-prometheus 9090:9090"
    echo "  RabbitMQ Management: kubectl port-forward svc/rabbitmq-management 15672:15672"
    echo
    echo "Useful Commands:"
    echo "  View logs: kubectl logs -f deployment/gateway"
    echo "  Scale up: kubectl scale deployment gateway --replicas=5"
    echo "  Get status: kubectl get all"
    echo "  Delete deployment: kubectl delete namespace $PROJECT_NAME"
    echo
    echo "🚀 Happy job hunting with HuskyApply!"
}

# Main execution
main() {
    log_info "Starting HuskyApply deployment to $ENVIRONMENT in $REGION"
    
    validate_prerequisites
    build_and_push_images
    setup_namespace
    deploy_infrastructure
    deploy_applications
    deploy_networking
    setup_monitoring
    verify_deployment
    print_access_info
    
    log_success "Deployment completed successfully!"
}

# Run main function
main "$@"