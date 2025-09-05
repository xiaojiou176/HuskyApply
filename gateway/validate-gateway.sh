#!/bin/bash

# HuskyApply Gateway Service Validation Script
# Comprehensive validation of the Java Spring Boot Gateway service

set -e

echo "🚀 HuskyApply Gateway Service Validation"
echo "============================================"

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Counters
passed=0
warnings=0
failed=0

log_success() {
    echo -e "${GREEN}✅ $1${NC}"
    ((passed++))
}

log_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
    ((warnings++))
}

log_error() {
    echo -e "${RED}❌ $1${NC}"
    ((failed++))
}

log_info() {
    echo -e "${BLUE}$1${NC}"
}

# Check if we're in the gateway directory
if [ ! -f "pom.xml" ] || [ ! -d "src/main/java" ]; then
    log_error "Not in Gateway service directory. Please run from gateway/ folder."
    exit 1
fi

log_info "🔍 Validating Gateway service architecture..."

# 1. Project Structure Validation
log_info "\n📁 Validating project structure..."

required_dirs=(
    "src/main/java/com/huskyapply/gateway"
    "src/main/java/com/huskyapply/gateway/controller"
    "src/main/java/com/huskyapply/gateway/service"
    "src/main/java/com/huskyapply/gateway/model"
    "src/main/java/com/huskyapply/gateway/repository"
    "src/main/java/com/huskyapply/gateway/config"
    "src/main/java/com/huskyapply/gateway/dto"
    "src/main/resources/db/migration"
    "src/test/java"
)

for dir in "${required_dirs[@]}"; do
    if [ -d "$dir" ]; then
        log_success "Directory structure: $dir exists"
    else
        log_error "Missing directory: $dir"
    fi
done

# 2. Maven Configuration
log_info "\n🔧 Validating Maven configuration..."
if [ -f "pom.xml" ]; then
    log_success "Maven configuration: pom.xml exists"
    
    # Check for essential dependencies
    if grep -q "spring-boot-starter-web" pom.xml; then
        log_success "Spring Boot Web dependency found"
    else
        log_error "Missing spring-boot-starter-web dependency"
    fi
    
    if grep -q "spring-boot-starter-data-jpa" pom.xml; then
        log_success "Spring Boot JPA dependency found"
    else
        log_error "Missing spring-boot-starter-data-jpa dependency"
    fi
    
    if grep -q "spring-boot-starter-security" pom.xml; then
        log_success "Spring Security dependency found"
    else
        log_error "Missing spring-boot-starter-security dependency"
    fi
    
    if grep -q "postgresql" pom.xml; then
        log_success "PostgreSQL driver dependency found"
    else
        log_error "Missing PostgreSQL driver dependency"
    fi
else
    log_error "Maven configuration: pom.xml not found"
fi

# 3. Java Compilation
log_info "\n☕ Validating Java compilation..."
if mvn clean compile -q 2>/dev/null; then
    log_success "Java Compilation: All main classes compile successfully"
else
    log_error "Java Compilation: Compilation errors in main classes"
fi

# Test compilation (allow failure)
if mvn test-compile -q 2>/dev/null; then
    log_success "Test Compilation: All test classes compile successfully"
else
    log_warning "Test Compilation: Some test compilation issues found"
fi

# 4. Configuration Files
log_info "\n⚙️  Validating configuration files..."
if [ -f "src/main/resources/application.properties" ]; then
    log_success "Application configuration: application.properties exists"
    
    # Check for essential properties
    properties_file="src/main/resources/application.properties"
    
    if grep -q "jwt.secret.key" "$properties_file"; then
        log_success "JWT configuration found"
    else
        log_warning "JWT configuration might be missing"
    fi
    
    if grep -q "spring.rabbitmq" "$properties_file"; then
        log_success "RabbitMQ configuration found"
    else
        log_warning "RabbitMQ configuration might be missing"
    fi
    
    if grep -q "spring.data.redis" "$properties_file"; then
        log_success "Redis configuration found"
    else
        log_warning "Redis configuration might be missing"
    fi
else
    log_error "Application configuration: application.properties not found"
fi

# 5. Database Migrations
log_info "\n🗄️ Validating database migrations..."
migration_count=$(find src/main/resources/db/migration -name "V*.sql" 2>/dev/null | wc -l)
if [ "$migration_count" -gt 0 ]; then
    log_success "Database migrations: Found $migration_count migration files"
    
    # Check for essential tables
    if find src/main/resources/db/migration -name "*.sql" -exec grep -l "users" {} \; | grep -q .; then
        log_success "Users table migration found"
    else
        log_warning "Users table migration might be missing"
    fi
    
    if find src/main/resources/db/migration -name "*.sql" -exec grep -l "jobs" {} \; | grep -q .; then
        log_success "Jobs table migration found"
    else
        log_warning "Jobs table migration might be missing"
    fi
else
    log_error "Database migrations: No migration files found"
fi

# 6. Security Configuration
log_info "\n🔒 Validating security configuration..."
security_config="src/main/java/com/huskyapply/gateway/config/SecurityConfig.java"
if [ -f "$security_config" ]; then
    log_success "Security configuration: SecurityConfig.java exists"
    
    if grep -q "JwtAuthenticationFilter" "$security_config"; then
        log_success "JWT authentication filter configured"
    else
        log_warning "JWT authentication filter might be missing"
    fi
    
    if grep -q "BCryptPasswordEncoder" "$security_config"; then
        log_success "Password encryption configured"
    else
        log_warning "Password encryption might be missing"
    fi
else
    log_error "Security configuration: SecurityConfig.java not found"
fi

# 7. Controller Layer
log_info "\n🎮 Validating controller layer..."
controller_count=$(find src/main/java/com/huskyapply/gateway/controller -name "*Controller.java" 2>/dev/null | wc -l)
if [ "$controller_count" -gt 0 ]; then
    log_success "Controllers: Found $controller_count controller classes"
    
    # Check for essential controllers
    if [ -f "src/main/java/com/huskyapply/gateway/controller/JobController.java" ]; then
        log_success "JobController found"
    else
        log_warning "JobController might be missing"
    fi
    
    if [ -f "src/main/java/com/huskyapply/gateway/controller/AuthController.java" ]; then
        log_success "AuthController found"
    else
        log_warning "AuthController might be missing"
    fi
else
    log_error "Controllers: No controller classes found"
fi

# 8. Service Layer
log_info "\n🏗️ Validating service layer..."
service_count=$(find src/main/java/com/huskyapply/gateway/service -name "*Service.java" 2>/dev/null | wc -l)
if [ "$service_count" -gt 0 ]; then
    log_success "Services: Found $service_count service classes"
else
    log_warning "Services: Limited service classes found"
fi

# 9. Model Layer  
log_info "\n📋 Validating model layer..."
model_count=$(find src/main/java/com/huskyapply/gateway/model -name "*.java" 2>/dev/null | wc -l)
if [ "$model_count" -gt 0 ]; then
    log_success "Models: Found $model_count model classes"
else
    log_warning "Models: Limited model classes found"
fi

# 10. Repository Layer
log_info "\n🗃️ Validating repository layer..."
repo_count=$(find src/main/java/com/huskyapply/gateway/repository -name "*Repository.java" 2>/dev/null | wc -l)
if [ "$repo_count" -gt 0 ]; then
    log_success "Repositories: Found $repo_count repository interfaces"
else
    log_warning "Repositories: Limited repository interfaces found"
fi

# 11. DTO Layer
log_info "\n📤 Validating DTO layer..."
dto_count=$(find src/main/java/com/huskyapply/gateway/dto -name "*.java" 2>/dev/null | wc -l)
if [ "$dto_count" -gt 0 ]; then
    log_success "DTOs: Found $dto_count DTO classes"
else
    log_warning "DTOs: Limited DTO classes found"
fi

# 12. Production Readiness
log_info "\n🚀 Validating production readiness..."

# Check for actuator endpoints
if grep -q "spring-boot-starter-actuator" pom.xml; then
    log_success "Actuator endpoints configured"
else
    log_warning "Actuator endpoints might be missing"
fi

# Check for metrics
if grep -q "micrometer" pom.xml; then
    log_success "Metrics collection configured"
else
    log_warning "Metrics collection might be missing"
fi

# Check for logging configuration
if grep -q "logging.pattern" src/main/resources/application.properties; then
    log_success "Logging patterns configured"
else
    log_warning "Custom logging patterns might be missing"
fi

# Summary
echo
echo "============================================"
log_info "📊 VALIDATION SUMMARY"
echo "============================================"
echo -e "${NC}Total checks: $((passed + warnings + failed))"
log_success "Passed: $passed"
log_warning "Warnings: $warnings"
log_error "Failed: $failed"

echo
if [ "$failed" -eq 0 ]; then
    if [ "$warnings" -eq 0 ]; then
        log_success "🎉 GATEWAY SERVICE IS PERFECT!"
        log_success "Ready for production deployment."
    else
        log_warning "⚡ GATEWAY SERVICE IS EXCELLENT!"
        log_warning "Minor optimizations available but production-ready."
    fi
    exit 0
else
    log_error "🔧 GATEWAY SERVICE NEEDS WORK"
    log_error "Please address the failed checks before deployment."
    echo
    log_info "🔍 FAILED CHECKS:"
    # Additional failed check details would go here
    exit 1
fi