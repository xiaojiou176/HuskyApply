# HuskyApply Brain Service Makefile

# Variables
PYTHON := python
UV := uv
DOCKER_IMAGE := huskyapply-brain
DOCKER_TAG := latest
SERVICE_NAME := brain-service

# Colors for output
RED := \033[0;31m
GREEN := \033[0;32m
YELLOW := \033[1;33m
BLUE := \033[0;34m
NC := \033[0m # No Color

.PHONY: help setup dev test lint format clean docker build deploy health

# Default target
help: ## Show this help message
	@echo "$(BLUE)HuskyApply Brain Service$(NC)"
	@echo "Available commands:"
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "  $(GREEN)%-15s$(NC) %s\n", $$1, $$2}' $(MAKEFILE_LIST)

# Development Setup
setup: ## Initial setup for development
	@echo "$(YELLOW)Setting up development environment...$(NC)"
	$(UV) sync --extra dev --extra test
	@echo "$(GREEN)✅ Setup complete!$(NC)"

dev: ## Start development server with hot reload
	@echo "$(YELLOW)Starting development server...$(NC)"
	$(UV) run uvicorn main:app --host 0.0.0.0 --port 8000 --reload

# Testing
test: ## Run all tests
	@echo "$(YELLOW)Running tests...$(NC)"
	$(UV) run pytest -v

test-cov: ## Run tests with coverage report
	@echo "$(YELLOW)Running tests with coverage...$(NC)"
	$(UV) run pytest --cov=. --cov-report=term-missing --cov-report=html

test-unit: ## Run only unit tests
	@echo "$(YELLOW)Running unit tests...$(NC)"
	$(UV) run pytest -v -m unit

test-integration: ## Run only integration tests
	@echo "$(YELLOW)Running integration tests...$(NC)"
	$(UV) run pytest -v -m integration

test-fast: ## Run tests excluding slow ones
	@echo "$(YELLOW)Running fast tests...$(NC)"
	$(UV) run pytest -v -m "not slow"

# Code Quality
lint: ## Run all linting and type checks
	@echo "$(YELLOW)Running linting and type checks...$(NC)"
	$(UV) run black --check .
	$(UV) run isort --check-only .
	$(UV) run flake8 .
	$(UV) run mypy .
	@echo "$(GREEN)✅ All checks passed!$(NC)"

format: ## Format code with black and isort
	@echo "$(YELLOW)Formatting code...$(NC)"
	$(UV) run black .
	$(UV) run isort .
	@echo "$(GREEN)✅ Code formatted!$(NC)"

typecheck: ## Run type checking with mypy
	@echo "$(YELLOW)Running type checks...$(NC)"
	$(UV) run mypy .

# Docker Operations
docker-build: ## Build Docker image
	@echo "$(YELLOW)Building Docker image...$(NC)"
	docker build -t $(DOCKER_IMAGE):$(DOCKER_TAG) .
	@echo "$(GREEN)✅ Docker image built: $(DOCKER_IMAGE):$(DOCKER_TAG)$(NC)"

docker-build-dev: ## Build development Docker image
	@echo "$(YELLOW)Building development Docker image...$(NC)"
	docker build --target development -t $(DOCKER_IMAGE):dev .
	@echo "$(GREEN)✅ Development Docker image built: $(DOCKER_IMAGE):dev$(NC)"

docker-run: ## Run Docker container
	@echo "$(YELLOW)Starting Docker container...$(NC)"
	docker run -d \
		--name $(SERVICE_NAME) \
		--env-file .env \
		-p 8000:8000 \
		$(DOCKER_IMAGE):$(DOCKER_TAG)
	@echo "$(GREEN)✅ Container started: $(SERVICE_NAME)$(NC)"

docker-run-dev: ## Run development Docker container
	@echo "$(YELLOW)Starting development Docker container...$(NC)"
	docker run -d \
		--name $(SERVICE_NAME)-dev \
		--env-file .env \
		-p 8000:8000 \
		-v $(PWD):/app \
		$(DOCKER_IMAGE):dev
	@echo "$(GREEN)✅ Development container started: $(SERVICE_NAME)-dev$(NC)"

docker-stop: ## Stop and remove Docker container
	@echo "$(YELLOW)Stopping Docker container...$(NC)"
	-docker stop $(SERVICE_NAME)
	-docker rm $(SERVICE_NAME)
	-docker stop $(SERVICE_NAME)-dev
	-docker rm $(SERVICE_NAME)-dev
	@echo "$(GREEN)✅ Container stopped and removed$(NC)"

docker-logs: ## Show Docker container logs
	docker logs -f $(SERVICE_NAME)

# Production Operations
build: ## Build production-ready package
	@echo "$(YELLOW)Building production package...$(NC)"
	$(UV) build
	@echo "$(GREEN)✅ Package built successfully!$(NC)"

install: ## Install package in production mode
	@echo "$(YELLOW)Installing package...$(NC)"
	$(UV) pip install .
	@echo "$(GREEN)✅ Package installed!$(NC)"

# Health and Monitoring
health: ## Check service health
	@echo "$(YELLOW)Checking service health...$(NC)"
	@curl -s http://localhost:8000/healthz | $(PYTHON) -m json.tool || echo "$(RED)❌ Service not responding$(NC)"

metrics: ## Show Prometheus metrics
	@echo "$(YELLOW)Fetching metrics...$(NC)"
	@curl -s http://localhost:8000/metrics | head -20

logs: ## Show service logs (if running via systemd)
	sudo journalctl -u huskyapply-brain -f --lines=50

# Database and Dependencies
check-deps: ## Check for dependency updates
	@echo "$(YELLOW)Checking for dependency updates...$(NC)"
	$(UV) sync --upgrade
	@echo "$(GREEN)✅ Dependencies checked!$(NC)"

security-scan: ## Run security scan on dependencies
	@echo "$(YELLOW)Running security scan...$(NC)"
	$(UV) run safety check
	@echo "$(GREEN)✅ Security scan complete!$(NC)"

# Cleanup
clean: ## Clean up build artifacts and cache
	@echo "$(YELLOW)Cleaning up...$(NC)"
	rm -rf build/
	rm -rf dist/
	rm -rf *.egg-info/
	rm -rf .pytest_cache/
	rm -rf .mypy_cache/
	rm -rf htmlcov/
	find . -type d -name __pycache__ -exec rm -rf {} +
	find . -name "*.pyc" -delete
	@echo "$(GREEN)✅ Cleanup complete!$(NC)"

clean-docker: ## Clean up Docker images and containers
	@echo "$(YELLOW)Cleaning up Docker resources...$(NC)"
	-docker stop $(SERVICE_NAME) $(SERVICE_NAME)-dev
	-docker rm $(SERVICE_NAME) $(SERVICE_NAME)-dev
	-docker rmi $(DOCKER_IMAGE):$(DOCKER_TAG) $(DOCKER_IMAGE):dev
	docker system prune -f
	@echo "$(GREEN)✅ Docker cleanup complete!$(NC)"

# CI/CD Helpers
ci-setup: ## Setup CI environment
	@echo "$(YELLOW)Setting up CI environment...$(NC)"
	pip install uv
	$(UV) sync --extra dev --extra test

ci-test: ## Run tests for CI
	@echo "$(YELLOW)Running CI tests...$(NC)"
	$(UV) run pytest --cov=. --cov-report=xml --cov-report=term

ci-lint: ## Run linting for CI
	@echo "$(YELLOW)Running CI linting...$(NC)"
	$(UV) run black --check .
	$(UV) run isort --check-only .
	$(UV) run flake8 .
	$(UV) run mypy .

# Deployment
deploy-local: ## Deploy locally using docker-compose
	@echo "$(YELLOW)Deploying locally...$(NC)"
	docker-compose up -d brain
	@echo "$(GREEN)✅ Local deployment complete!$(NC)"

deploy-staging: ## Deploy to staging environment
	@echo "$(YELLOW)Deploying to staging...$(NC)"
	# Add staging deployment commands here
	@echo "$(GREEN)✅ Staging deployment complete!$(NC)"

deploy-prod: ## Deploy to production environment
	@echo "$(YELLOW)Deploying to production...$(NC)"
	# Add production deployment commands here
	@echo "$(GREEN)✅ Production deployment complete!$(NC)"

# All-in-one commands
all: clean format lint test build ## Run all quality checks and build
	@echo "$(GREEN)✅ All tasks completed successfully!$(NC)"

quick-check: format test-fast lint ## Quick development check
	@echo "$(GREEN)✅ Quick checks passed!$(NC)"

# Environment
env-check: ## Check environment configuration
	@echo "$(YELLOW)Checking environment configuration...$(NC)"
	@echo "Python version: $$($(PYTHON) --version)"
	@echo "UV version: $$($(UV) --version)"
	@echo "Current directory: $(PWD)"
	@test -f .env && echo "✅ .env file exists" || echo "❌ .env file missing"
	@test -f pyproject.toml && echo "✅ pyproject.toml exists" || echo "❌ pyproject.toml missing"
	@echo "$(GREEN)✅ Environment check complete!$(NC)"

# Help for specific sections
help-dev: ## Show development commands
	@echo "$(BLUE)Development Commands:$(NC)"
	@echo "  setup, dev, test, test-cov, lint, format"

help-docker: ## Show Docker commands
	@echo "$(BLUE)Docker Commands:$(NC)"
	@echo "  docker-build, docker-run, docker-stop, docker-logs"

help-deploy: ## Show deployment commands
	@echo "$(BLUE)Deployment Commands:$(NC)"
	@echo "  build, deploy-local, deploy-staging, deploy-prod"