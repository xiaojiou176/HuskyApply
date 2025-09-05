#!/bin/bash

# Deploy HPA v2 Configuration for 10x Load Scaling
# This script deploys advanced autoscaling configuration

set -euo pipefail

NAMESPACE="${NAMESPACE:-huskyapply}"
MONITORING_NAMESPACE="${MONITORING_NAMESPACE:-monitoring}"

echo "🚀 Deploying HPA v2 Configuration for 10x Load Scaling"

# Function to check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Check required tools
echo "📋 Checking prerequisites..."
if ! command_exists kubectl; then
    echo "❌ kubectl is required but not installed"
    exit 1
fi

if ! command_exists helm; then
    echo "❌ helm is required but not installed"
    exit 1
fi

# Check cluster connection
if ! kubectl cluster-info >/dev/null 2>&1; then
    echo "❌ Cannot connect to Kubernetes cluster"
    exit 1
fi

echo "✅ Prerequisites check passed"

# Create namespaces if they don't exist
echo "📁 Creating namespaces..."
kubectl create namespace $NAMESPACE --dry-run=client -o yaml | kubectl apply -f -
kubectl create namespace $MONITORING_NAMESPACE --dry-run=client -o yaml | kubectl apply -f -

# Deploy Metrics Server (required for basic resource metrics)
echo "📊 Installing Metrics Server..."
if ! kubectl get deployment metrics-server -n kube-system >/dev/null 2>&1; then
    kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
    echo "⏳ Waiting for Metrics Server to be ready..."
    kubectl wait --for=condition=available --timeout=300s deployment/metrics-server -n kube-system
else
    echo "✅ Metrics Server already installed"
fi

# Deploy Prometheus and Grafana for custom metrics (if not already installed)
echo "📊 Setting up Prometheus stack..."
if ! helm list -n $MONITORING_NAMESPACE | grep -q prometheus; then
    echo "Installing Prometheus Operator..."
    helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
    helm repo update
    
    helm install prometheus prometheus-community/kube-prometheus-stack \
        --namespace $MONITORING_NAMESPACE \
        --set prometheus.prometheusSpec.serviceMonitorSelectorNilUsesHelmValues=false \
        --set prometheus.prometheusSpec.ruleSelectorNilUsesHelmValues=false \
        --set prometheus.prometheusSpec.retention=7d \
        --set prometheus.prometheusSpec.storageSpec.volumeClaimTemplate.spec.resources.requests.storage=50Gi \
        --set grafana.adminPassword=admin123 \
        --wait --timeout=600s
else
    echo "✅ Prometheus stack already installed"
fi

# Deploy Prometheus Adapter for custom metrics
echo "🔧 Deploying Prometheus Adapter..."
kubectl apply -f ../k8s/prometheus-adapter.yaml
kubectl wait --for=condition=available --timeout=300s deployment/prometheus-adapter -n $MONITORING_NAMESPACE

# Deploy Cluster Autoscaler
echo "🏗️ Deploying Cluster Autoscaler..."
kubectl apply -f ../k8s/cluster-autoscaler.yaml

# Deploy Advanced HPA Configuration
echo "📈 Deploying Advanced HPA Configuration..."
kubectl apply -f ../k8s/advanced-hpa.yaml

# Update existing deployments with optimized settings
echo "🔄 Updating service deployments..."
kubectl apply -f ../k8s/gateway.yaml
kubectl apply -f ../k8s/brain.yaml

# Verify HPA is working
echo "🔍 Verifying HPA deployment..."
sleep 30  # Give HPAs time to initialize

# Check HPA status
echo "📊 HPA Status:"
kubectl get hpa -n $NAMESPACE

# Check custom metrics API
echo "📊 Custom Metrics API Status:"
if kubectl get --raw "/apis/custom.metrics.k8s.io/v1beta1" >/dev/null 2>&1; then
    echo "✅ Custom metrics API is accessible"
else
    echo "⚠️ Custom metrics API not accessible yet (may need a few minutes)"
fi

# Check external metrics API
if kubectl get --raw "/apis/external.metrics.k8s.io/v1beta1" >/dev/null 2>&1; then
    echo "✅ External metrics API is accessible"
else
    echo "⚠️ External metrics API not accessible yet (may need a few minutes)"
fi

# Check VPA if enabled
if kubectl get vpa -n $NAMESPACE >/dev/null 2>&1; then
    echo "📊 VPA Status:"
    kubectl get vpa -n $NAMESPACE
else
    echo "ℹ️ VPA not installed (install with: kubectl apply -f https://github.com/kubernetes/autoscaler/tree/master/vertical-pod-autoscaler/deploy)"
fi

# Display resource quotas
echo "💾 Resource Quotas:"
kubectl describe resourcequota huskyapply-compute-quota -n $NAMESPACE || echo "ℹ️ Resource quota not found"

# Display monitoring endpoints
GRAFANA_SVC=$(kubectl get svc -n $MONITORING_NAMESPACE -l app.kubernetes.io/name=grafana -o jsonpath='{.items[0].metadata.name}')
if [ -n "$GRAFANA_SVC" ]; then
    echo "📊 Grafana Dashboard:"
    echo "   kubectl port-forward -n $MONITORING_NAMESPACE svc/$GRAFANA_SVC 3000:80"
    echo "   Access: http://localhost:3000 (admin/admin123)"
fi

PROMETHEUS_SVC=$(kubectl get svc -n $MONITORING_NAMESPACE -l app.kubernetes.io/name=prometheus -o jsonpath='{.items[0].metadata.name}')
if [ -n "$PROMETHEUS_SVC" ]; then
    echo "📊 Prometheus Dashboard:"
    echo "   kubectl port-forward -n $MONITORING_NAMESPACE svc/$PROMETHEUS_SVC 9090:9090"
    echo "   Access: http://localhost:9090"
fi

echo ""
echo "🎉 HPA v2 deployment completed successfully!"
echo ""
echo "📊 Scaling Capabilities:"
echo "   • Gateway: 3-50 replicas (16.7x scaling)"
echo "   • Brain: 2-30 replicas (15x scaling)"  
echo "   • Metrics: CPU, Memory, Custom business metrics"
echo "   • Node scaling: Cluster Autoscaler enabled"
echo ""
echo "📈 Custom Metrics Available:"
echo "   • http_requests_per_second"
echo "   • job_queue_depth"
echo "   • response_latency_p95"
echo "   • ai_processing_queue_depth"
echo "   • ai_processing_duration_p90"
echo "   • rabbitmq_messages_ready"
echo ""
echo "🔧 Management Commands:"
echo "   • View HPA status: kubectl get hpa -n $NAMESPACE"
echo "   • View metrics: kubectl top pods -n $NAMESPACE"
echo "   • Scale manually: kubectl scale deployment gateway --replicas=10 -n $NAMESPACE"
echo "   • View events: kubectl get events -n $NAMESPACE --sort-by=.metadata.creationTimestamp"
echo ""
echo "⚠️ Important Notes:"
echo "   • Custom metrics may take 5-10 minutes to become available"
echo "   • Ensure your Prometheus is scraping application metrics"
echo "   • Monitor resource quotas during scaling events"
echo "   • Test scaling behavior in staging environment first"