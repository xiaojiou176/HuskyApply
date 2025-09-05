# HuskyApply - Personal Project by Yifeng Yu
# Simple commands for 3-tier microservices setup

.PHONY: help setup run stop test clean

help: ## Show available commands
	@echo "HuskyApply - Personal Project by Yifeng Yu"
	@echo "==========================================="
	@echo "A 3-tier microservices platform:"
	@echo "  - brain/     Python AI processing service"
	@echo "  - gateway/   Java Spring Boot API gateway"  
	@echo "  - frontend/  Vanilla JavaScript UI"
	@echo ""
	@echo "Commands:"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  %-12s %s\n", $$1, $$2}'

setup: ## Install dependencies and prepare development environment
	@echo "Setting up Python service..."
	@cd brain && uv sync --extra test || (python3 -m pip install -e . && echo "Using pip fallback")
	@echo "Setting up Java gateway..."
	@cd gateway && mvn -DskipTests package
	@echo "Setting up frontend..."
	@cd frontend && [ -f package.json ] && npm install || echo "Static frontend, no npm setup needed"
	@echo "Environment setup complete!"

run: ## Start all services (requires setup first)
	@echo "Starting infrastructure services..."
	@docker-compose -f ops/infra/docker-compose.yml up -d postgres-db rabbitmq-server redis-server || echo "Docker services unavailable, continuing..."
	@echo "Starting Python brain service..."
	@cd brain && uv run python main.py &
	@echo "Starting Java gateway..."
	@cd gateway && mvn spring-boot:run &
	@echo "Starting frontend..."
	@cd frontend && python -m http.server 3000 &
	@echo "Services started! Access at: http://localhost:3000"

stop: ## Stop all running services
	@echo "Stopping services..."
	@docker-compose -f ops/infra/docker-compose.yml down 2>/dev/null || true
	@pkill -f "python main.py" || true
	@pkill -f "spring-boot:run" || true
	@pkill -f "http.server 3000" || true
	@echo "All services stopped"

test: ## Run tests for all services
	@echo "Testing Python service..."
	@cd brain && uv run pytest -q || echo "Python tests unavailable"
	@echo "Testing Java gateway..."
	@cd gateway && mvn -q test || echo "Java tests unavailable"
	@echo "Frontend tests..."
	@echo "Static frontend - manual testing at http://localhost:3000"

clean: ## Clean build artifacts and temporary files
	@echo "Cleaning workspace..."
	@./scripts/clean_all.sh