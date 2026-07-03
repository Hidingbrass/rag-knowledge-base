PYTHON ?= python3

.PHONY: help install test test-api test-backend pre-submit smoke demo-job docker-up docker-infra docker-down docker-logs docker-ps

help:
	@echo "Common project commands:"
	@echo "  make install       Install Python dependencies"
	@echo "  make test          Run FastAPI and Spring Boot tests"
	@echo "  make test-api      Run FastAPI pytest"
	@echo "  make test-backend  Run Spring Boot Maven tests"
	@echo "  make pre-submit    Run local pre-submit checks"
	@echo "  make smoke         Check running local services"
	@echo "  make demo-job      Run real job-agent demo"
	@echo "  make docker-infra  Start MySQL and Qdrant"
	@echo "  make docker-up     Build and start the full stack"
	@echo "  make docker-down   Stop Docker Compose services"
	@echo "  make docker-logs   Follow api and backend logs"
	@echo "  make docker-ps     Show Docker Compose services"

install:
	$(PYTHON) -m pip install -r requirements.txt

test: test-api test-backend

test-api:
	$(PYTHON) -m pytest -q

test-backend:
	cd springboot-backend && mvn -s maven-settings.xml test

pre-submit:
	bash scripts/pre_submit_check.sh

smoke:
	bash scripts/smoke_check.sh

demo-job:
	bash scripts/demo_job_agent.sh

docker-infra:
	docker compose up -d mysql qdrant

docker-up:
	docker compose up --build -d

docker-down:
	docker compose down

docker-logs:
	docker compose logs -f api backend

docker-ps:
	docker compose ps
