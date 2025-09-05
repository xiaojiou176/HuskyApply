# HuskyApply Deployment Guide

## Overview

HuskyApply supports multiple deployment scenarios from local development to production-ready multi-region Kubernetes deployments. This guide covers all deployment options with step-by-step instructions.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Local Development Setup](#local-development-setup)
3. [Docker Compose Deployment](#docker-compose-deployment)
4. [Single-Region Kubernetes Deployment](#single-region-kubernetes-deployment)
5. [Multi-Region Production Deployment](#multi-region-production-deployment)
6. [Environment Configuration](#environment-configuration)
7. [Monitoring Setup](#monitoring-setup)
8. [SSL/TLS Configuration](#ssltls-configuration)
9. [Database Migration](#database-migration)
10. [Backup and Recovery](#backup-and-recovery)

## Prerequisites

### Required Software
- **Docker** 24.0+ and Docker Compose 2.0+
- **Kubernetes** 1.28+ (for K8s deployments)
- **kubectl** configured with cluster access
- **Helm** 3.12+ (for monitoring stack)
- **Git** for source code management

### Required Accounts
- **AWS Account** for S3 storage
- **OpenAI API Key** for AI processing
- **Stripe Account** for payment processing (optional)
- **Domain and SSL Certificate** for production

### System Requirements

#### Minimum (Development)
- 4 GB RAM
- 2 CPU cores
- 20 GB storage

#### Recommended (Production)
- 16 GB RAM per node
- 4 CPU cores per node
- 100 GB storage
- Load balancer
- Redis cluster

## Local Development Setup

### 1. Clone Repository
```bash
git clone https://github.com/your-org/huskyapply.git
cd huskyapply
```

### 2. Environment Configuration
Create environment file:
```bash
cp infra/.env.example infra/.env
```

Edit `infra/.env` with your configuration:
```bash
# Database Configuration
POSTGRES_DB=huskyapply
POSTGRES_USER=husky
POSTGRES_PASSWORD=your_secure_password

# Message Queue
RABBITMQ_DEFAULT_USER=husky
RABBITMQ_DEFAULT_PASS=your_secure_password

# AI Services
OPENAI_API_KEY=sk-your-openai-key-here
ANTHROPIC_API_KEY=your-anthropic-key-here

# Security
JWT_SECRET_KEY=your-256-bit-secret-key-here
INTERNAL_API_KEY=your-internal-api-key-here

# Storage
S3_BUCKET_NAME=your-s3-bucket
S3_REGION=us-east-1
AWS_ACCESS_KEY_ID=your-access-key
AWS_SECRET_ACCESS_KEY=your-secret-key

# Redis
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=your-redis-password

# Payment (Optional)
STRIPE_SECRET_KEY=sk_test_your-stripe-key
STRIPE_PUBLISHABLE_KEY=pk_test_your-stripe-key
```

### 3. Start Services
```bash
cd infra
docker-compose up --build
```

### 4. Verify Installation
```bash
# Check service health
curl http://localhost:8080/actuator/health
curl http://localhost:8000/healthz

# Access applications
echo "Frontend: http://localhost:3000"
echo "Gateway API: http://localhost:8080"
echo "Brain API: http://localhost:8000"
echo "RabbitMQ Management: http://localhost:15672"
```

## Docker Compose Deployment

### Production Docker Compose Setup

Create `docker-compose.prod.yml`:
```yaml
version: '3.8'

services:
  gateway:
    build:
      context: ../gateway
      dockerfile: Dockerfile
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=production
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/huskyapply
      - SPRING_REDIS_HOST=redis
      - RABBITMQ_HOST=rabbitmq
    depends_on:
      - postgres
      - rabbitmq
      - redis
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  brain:
    build:
      context: ../brain
      dockerfile: Dockerfile
    ports:
      - "8000:8000"
    environment:
      - RABBITMQ_HOST=rabbitmq
      - GATEWAY_INTERNAL_URL=http://gateway:8080
    depends_on:
      - rabbitmq
      - postgres
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8000/healthz"]
      interval: 30s
      timeout: 10s
      retries: 3

  frontend:
    build:
      context: ../frontend
      dockerfile: Dockerfile
    ports:
      - "80:80"
    depends_on:
      - gateway
    restart: unless-stopped

  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./backups:/backups
    ports:
      - "5432:5432"
    restart: unless-stopped
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER}"]
      interval: 10s
      timeout: 5s
      retries: 5

  rabbitmq:
    image: rabbitmq:3.12-management-alpine
    environment:
      RABBITMQ_DEFAULT_USER: ${RABBITMQ_DEFAULT_USER}
      RABBITMQ_DEFAULT_PASS: ${RABBITMQ_DEFAULT_PASS}
    volumes:
      - rabbitmq_data:/var/lib/rabbitmq
    ports:
      - "5672:5672"
      - "15672:15672"
    restart: unless-stopped
    healthcheck:
      test: rabbitmq-diagnostics -q ping
      interval: 30s
      timeout: 30s
      retries: 3

  redis:
    image: redis:7.2-alpine
    command: redis-server --appendonly yes --requirepass ${REDIS_PASSWORD}
    volumes:
      - redis_data:/data
    ports:
      - "6379:6379"
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 3s
      retries: 3

  nginx:
    image: nginx:1.25-alpine
    ports:
      - "443:443"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf
      - ./ssl:/etc/nginx/ssl
    depends_on:
      - gateway
      - frontend
    restart: unless-stopped

volumes:
  postgres_data:
  rabbitmq_data:
  redis_data:
```

### Deploy with Docker Compose
```bash
# Production deployment
docker-compose -f docker-compose.prod.yml up -d

# Check status
docker-compose -f docker-compose.prod.yml ps

# View logs
docker-compose -f docker-compose.prod.yml logs -f gateway
```

## Single-Region Kubernetes Deployment

### 1. Prepare Cluster
```bash
# Verify kubectl access
kubectl cluster-info

# Create namespace
kubectl create namespace huskyapply
kubectl config set-context --current --namespace=huskyapply
```

### 2. Create Secrets
```bash
# Database secrets
kubectl create secret generic postgres-secret \
  --from-literal=username=husky \
  --from-literal=password=your-secure-password

# Application secrets
kubectl create secret generic app-secrets \
  --from-literal=jwt-secret=your-256-bit-secret \
  --from-literal=internal-api-key=your-internal-key \
  --from-literal=openai-api-key=sk-your-openai-key \
  --from-literal=stripe-secret=sk-your-stripe-key

# AWS secrets
kubectl create secret generic aws-secrets \
  --from-literal=access-key-id=your-access-key \
  --from-literal=secret-access-key=your-secret-key
```

### 3. Deploy Infrastructure
```bash
# Apply Kubernetes manifests
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secrets.yaml
kubectl apply -f k8s/postgres.yaml
kubectl apply -f k8s/rabbitmq.yaml
kubectl apply -f k8s/redis.yaml

# Wait for infrastructure
kubectl wait --for=condition=ready pod -l app=postgres --timeout=300s
kubectl wait --for=condition=ready pod -l app=rabbitmq --timeout=300s
kubectl wait --for=condition=ready pod -l app=redis --timeout=300s
```

### 4. Deploy Applications
```bash
# Deploy application services
kubectl apply -f k8s/gateway.yaml
kubectl apply -f k8s/brain.yaml
kubectl apply -f k8s/frontend.yaml

# Wait for applications
kubectl wait --for=condition=ready pod -l app=gateway --timeout=300s
kubectl wait --for=condition=ready pod -l app=brain --timeout=300s
kubectl wait --for=condition=ready pod -l app=frontend --timeout=300s
```

### 5. Setup Ingress and SSL
```bash
# Install cert-manager (if not installed)
helm repo add jetstack https://charts.jetstack.io
helm repo update
helm install cert-manager jetstack/cert-manager \
  --namespace cert-manager \
  --create-namespace \
  --set installCRDs=true

# Apply ingress configuration
kubectl apply -f k8s/ingress.yaml

# Check certificate status
kubectl get certificate
kubectl describe certificate huskyapply-tls
```

### 6. Verify Deployment
```bash
# Check all resources
kubectl get all

# Check ingress
kubectl get ingress

# Test endpoints
curl -k https://your-domain.com/api/v1/health
curl -k https://your-domain.com
```

## Multi-Region Production Deployment

### 1. Prepare Multiple Clusters
Set up kubectl contexts for each region:
```bash
# Configure contexts
kubectl config set-cluster huskyapply-us-east-1 --server=https://us-east-1-cluster
kubectl config set-cluster huskyapply-us-west-2 --server=https://us-west-2-cluster
kubectl config set-cluster huskyapply-eu-west-1 --server=https://eu-west-1-cluster
kubectl config set-cluster huskyapply-ap-southeast-1 --server=https://ap-southeast-1-cluster

# Verify contexts
kubectl config get-contexts
```

### 2. Deploy Primary Region
```bash
# Deploy to primary region (US-East-1)
./scripts/deploy.sh production us-east-1
```

### 3. Deploy Secondary Regions
```bash
# Deploy to all regions
./scripts/multi-region-deploy.sh
```

### 4. Configure Global Load Balancer
```yaml
# global-lb.yaml
apiVersion: networking.istio.io/v1alpha3
kind: Gateway
metadata:
  name: global-gateway
spec:
  selector:
    istio: ingressgateway
  servers:
  - port:
      number: 443
      name: https
      protocol: HTTPS
    tls:
      mode: SIMPLE
      credentialName: huskyapply-tls
    hosts:
    - "huskyapply.com"
    - "*.huskyapply.com"
---
apiVersion: networking.istio.io/v1alpha3
kind: VirtualService
metadata:
  name: global-routing
spec:
  hosts:
  - "huskyapply.com"
  gateways:
  - global-gateway
  http:
  - match:
    - headers:
        cf-ipcountry:
          exact: "US"
    route:
    - destination:
        host: us-east-1.huskyapply.com
      weight: 100
  - match:
    - headers:
        cf-ipcountry:
          exact: "GB"
    route:
    - destination:
        host: eu-west-1.huskyapply.com
      weight: 100
  - route:
    - destination:
        host: us-east-1.huskyapply.com
      weight: 50
    - destination:
        host: us-west-2.huskyapply.com
      weight: 30
    - destination:
        host: eu-west-1.huskyapply.com
      weight: 20
```

## Environment Configuration

### Production Environment Variables

#### Gateway Service
```bash
# Database
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres-primary:5432/huskyapply
SPRING_DATASOURCE_USERNAME=husky
SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD}

# Redis
SPRING_REDIS_HOST=redis-cluster.default.svc.cluster.local
SPRING_REDIS_PORT=6379
SPRING_REDIS_PASSWORD=${REDIS_PASSWORD}

# RabbitMQ
SPRING_RABBITMQ_HOST=rabbitmq-cluster.default.svc.cluster.local
SPRING_RABBITMQ_PORT=5672
SPRING_RABBITMQ_USERNAME=${RABBITMQ_USER}
SPRING_RABBITMQ_PASSWORD=${RABBITMQ_PASSWORD}

# Security
JWT_SECRET_KEY=${JWT_SECRET}
INTERNAL_API_KEY=${INTERNAL_API_KEY}

# AWS
AWS_REGION=us-east-1
AWS_S3_BUCKET=huskyapply-production
AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY}
AWS_SECRET_ACCESS_KEY=${AWS_SECRET_KEY}

# Payment
STRIPE_SECRET_KEY=${STRIPE_SECRET_KEY}
STRIPE_PUBLISHABLE_KEY=${STRIPE_PUBLIC_KEY}

# Monitoring
MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,prometheus,metrics
MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always
```

#### Brain Service
```bash
# API Configuration
HOST=0.0.0.0
PORT=8000

# RabbitMQ
RABBITMQ_HOST=rabbitmq-cluster.default.svc.cluster.local
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=${RABBITMQ_USER}
RABBITMQ_PASSWORD=${RABBITMQ_PASSWORD}

# Database
DATABASE_URL=postgresql://husky:${DB_PASSWORD}@postgres-primary:5432/huskyapply

# AI Services
OPENAI_API_KEY=${OPENAI_API_KEY}
ANTHROPIC_API_KEY=${ANTHROPIC_API_KEY}

# Gateway Integration
GATEWAY_INTERNAL_URL=http://gateway-service:8080
INTERNAL_API_KEY=${INTERNAL_API_KEY}

# Logging
LOG_LEVEL=INFO
STRUCTURED_LOGGING=true
```

### Configuration Management
Use Kubernetes ConfigMaps and Secrets for configuration:

```bash
# ConfigMap for non-sensitive config
kubectl create configmap gateway-config \
  --from-literal=spring.profiles.active=production \
  --from-literal=spring.jpa.hibernate.ddl-auto=validate \
  --from-literal=logging.level.com.huskyapply=INFO

# Secret for sensitive data
kubectl create secret generic app-secrets \
  --from-literal=db-password="$(openssl rand -base64 32)" \
  --from-literal=jwt-secret="$(openssl rand -base64 32)" \
  --from-literal=redis-password="$(openssl rand -base64 32)"
```

## Monitoring Setup

### 1. Install Prometheus Operator
```bash
# Add Helm repository
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

# Install Prometheus stack
helm install monitoring prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --create-namespace \
  --set grafana.adminPassword=your-secure-password \
  --set prometheus.prometheusSpec.retention=30d \
  --set prometheus.prometheusSpec.storageSpec.volumeClaimTemplate.spec.resources.requests.storage=100Gi
```

### 2. Apply Custom Monitoring
```bash
# Deploy application monitoring
kubectl apply -f k8s/monitoring.yaml

# Verify monitoring setup
kubectl get servicemonitor -n monitoring
kubectl get prometheusrule -n monitoring
```

### 3. Access Monitoring Dashboards
```bash
# Port forward Grafana
kubectl port-forward -n monitoring svc/monitoring-grafana 3000:80

# Port forward Prometheus
kubectl port-forward -n monitoring svc/monitoring-prometheus 9090:9090

# Access URLs
echo "Grafana: http://localhost:3000 (admin/your-secure-password)"
echo "Prometheus: http://localhost:9090"
```

## SSL/TLS Configuration

### 1. Cert-manager Setup
```bash
# Install cert-manager
helm install cert-manager jetstack/cert-manager \
  --namespace cert-manager \
  --create-namespace \
  --set installCRDs=true

# Create cluster issuer
kubectl apply -f - <<EOF
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-prod
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory
    email: admin@yourdomain.com
    privateKeySecretRef:
      name: letsencrypt-prod
    solvers:
    - http01:
        ingress:
          class: nginx
EOF
```

### 2. Configure SSL Certificates
```yaml
# Add to ingress.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: huskyapply-ingress
  annotations:
    cert-manager.io/cluster-issuer: "letsencrypt-prod"
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
spec:
  tls:
  - hosts:
    - huskyapply.com
    - api.huskyapply.com
    secretName: huskyapply-tls
  rules:
  - host: huskyapply.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: frontend-service
            port:
              number: 80
```

### 3. Verify SSL
```bash
# Check certificate status
kubectl get certificate
kubectl describe certificate huskyapply-tls

# Test SSL
curl -I https://huskyapply.com
openssl s_client -connect huskyapply.com:443 -servername huskyapply.com
```

## Database Migration

### 1. Manual Migration
```bash
# Connect to database
kubectl exec -it postgres-0 -- psql -U husky -d huskyapply

# Check current version
SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;

# Run migrations manually if needed
kubectl exec -it gateway-pod -- java -jar app.jar --spring.flyway.migrate-at-start=true
```

### 2. Automated Migration
```yaml
# Add to gateway deployment
apiVersion: apps/v1
kind: Deployment
metadata:
  name: gateway
spec:
  template:
    spec:
      initContainers:
      - name: migration
        image: huskyapply/gateway:latest
        command: ['java', '-jar', '/app.jar']
        args: ['--spring.profiles.active=migration']
        env:
        - name: SPRING_DATASOURCE_URL
          value: "jdbc:postgresql://postgres:5432/huskyapply"
        - name: SPRING_FLYWAY_MIGRATE_AT_START
          value: "true"
```

### 3. Backup Before Migration
```bash
# Create backup
kubectl exec postgres-0 -- pg_dump -U husky huskyapply > backup-$(date +%Y%m%d).sql

# Store backup
kubectl create configmap db-backup-$(date +%Y%m%d) --from-file=backup-$(date +%Y%m%d).sql
```

## Backup and Recovery

### 1. Database Backup
```bash
# Create automated backup job
kubectl apply -f - <<EOF
apiVersion: batch/v1
kind: CronJob
metadata:
  name: postgres-backup
spec:
  schedule: "0 2 * * *"  # Daily at 2 AM
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: postgres-backup
            image: postgres:16-alpine
            env:
            - name: PGPASSWORD
              valueFrom:
                secretKeyRef:
                  name: postgres-secret
                  key: password
            command:
            - /bin/bash
            - -c
            - |
              pg_dump -h postgres -U husky huskyapply | \
              gzip > /backup/backup-\$(date +%Y%m%d-%H%M%S).sql.gz
            volumeMounts:
            - name: backup-storage
              mountPath: /backup
          volumes:
          - name: backup-storage
            persistentVolumeClaim:
              claimName: backup-pvc
          restartPolicy: OnFailure
EOF
```

### 2. Application State Backup
```bash
# Backup application configuration
kubectl get secret app-secrets -o yaml > app-secrets-backup.yaml
kubectl get configmap gateway-config -o yaml > gateway-config-backup.yaml

# Backup persistent volumes
kubectl get pv > pv-backup.yaml
kubectl get pvc > pvc-backup.yaml
```

### 3. Disaster Recovery Plan
```bash
#!/bin/bash
# disaster-recovery.sh

# 1. Restore database
kubectl exec postgres-0 -- psql -U husky -d huskyapply < latest-backup.sql

# 2. Restore application secrets
kubectl apply -f app-secrets-backup.yaml
kubectl apply -f gateway-config-backup.yaml

# 3. Restart applications
kubectl rollout restart deployment/gateway
kubectl rollout restart deployment/brain

# 4. Verify health
kubectl wait --for=condition=ready pod -l app=gateway --timeout=300s
kubectl wait --for=condition=ready pod -l app=brain --timeout=300s

# 5. Test functionality
curl -f https://api.huskyapply.com/health
```

## Troubleshooting

### Common Deployment Issues

#### 1. Pod Startup Failures
```bash
# Check pod status
kubectl describe pod <pod-name>

# Check logs
kubectl logs <pod-name> --previous

# Check events
kubectl get events --sort-by=.metadata.creationTimestamp
```

#### 2. Database Connection Issues
```bash
# Test database connectivity
kubectl exec -it postgres-0 -- pg_isready

# Check database logs
kubectl logs postgres-0

# Test from application pod
kubectl exec -it gateway-pod -- curl jdbc:postgresql://postgres:5432/huskyapply
```

#### 3. SSL Certificate Issues
```bash
# Check certificate status
kubectl describe certificate huskyapply-tls

# Check cert-manager logs
kubectl logs -n cert-manager -l app=cert-manager

# Manual certificate creation
kubectl create secret tls huskyapply-tls --cert=tls.crt --key=tls.key
```

#### 4. Service Discovery Issues
```bash
# Check service endpoints
kubectl get endpoints

# Test service connectivity
kubectl exec -it gateway-pod -- nslookup postgres
kubectl exec -it gateway-pod -- curl http://brain-service:8000/healthz
```

### Performance Tuning

#### 1. Resource Limits
```yaml
resources:
  requests:
    memory: "1Gi"
    cpu: "500m"
  limits:
    memory: "2Gi"
    cpu: "1000m"
```

#### 2. Auto-scaling
```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: gateway-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: gateway
  minReplicas: 3
  maxReplicas: 20
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

### Health Checks

#### 1. Application Health
```bash
# Gateway health
curl http://localhost:8080/actuator/health

# Brain health  
curl http://localhost:8000/healthz

# Database health
kubectl exec postgres-0 -- pg_isready
```

#### 2. Kubernetes Health
```bash
# Cluster health
kubectl get nodes
kubectl get componentstatus

# Application health
kubectl get pods
kubectl get services
kubectl get ingress
```

---

**Version**: 1.0  
**Last Updated**: September 1, 2024  
**Maintainer**: Yifeng Yu  
**Support**: [GitHub Issues](https://github.com/xiaojiou176/HuskyApply/issues)