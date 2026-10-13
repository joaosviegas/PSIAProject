# Same idea as the labs' justfiles: short names for the commands you run all day.
# Install just: https://github.com/casey/just

set dotenv-load := true

# List recipes
default:
    @just --list

# Fail early with a clear message if .env is missing
_check-env:
    @test -f .env || { echo "Missing .env -> run: cp .env.example .env  (then edit the values)"; exit 1; }

# Build images and start everything (needs phase 1 done: services/ and frontend/ must exist)
start: _check-env
    @test -d services/auth-service || { echo "services/ doesn't exist yet -> run phase 1 (docs/prompts/01-walking-skeleton.md) first, or use 'just infra'"; exit 1; }
    docker compose up -d --build

# Start only the ready-made containers (Postgres, Redis, Ollama + model pull). Works before phase 1.
infra: _check-env
    docker compose up -d postgres redis llm llm-init

# (Re)start only the monitoring stack: Prometheus, Grafana and the Postgres/Redis exporters
monitoring: _check-env
    docker compose up -d prometheus grafana postgres-exporter redis-exporter

# Stop and remove containers (data volumes are kept)
stop:
    docker compose down

# Stop and DELETE app data (Postgres, Redis). Keeps the downloaded LLM model (~1 GB) and dataset/raw.
clean:
    docker compose down
    -docker volume rm psia_postgres-data psia_redis-data

# Stop and delete EVERYTHING, including the LLM model and downloaded/seeded dataset files
nuke:
    docker compose down -v
    rm -rf dataset/raw

# Delete local build output (node_modules, dist, target) and Docker's build cache. Rarely needed.
clean-build:
    rm -rf frontend/node_modules frontend/dist services/*/target
    docker builder prune -f

# Rebuild and restart one service, e.g. `just restart auth-service`
restart service:
    docker compose up -d --build {{service}}

# Follow logs, e.g. `just logs` or `just logs recommendation-service`
logs service="":
    docker compose logs -f {{service}}

# Container status and health
ps:
    docker compose ps

# Open psql on a service database, e.g. `just psql auth`
psql db="postgres":
    docker compose exec postgres psql -U postgres -d {{db}}

# Open redis-cli on the agent cache
redis:
    docker compose exec redis redis-cli

# End-to-end check through the gateway
smoke:
    ./scripts/smoke.sh

# Run unit tests for a specific service or all services
test service="":
    #!/usr/bin/env bash
    if [ -z "{{service}}" ]; then
        echo "Running tests for all services..."
        for s in auth-service profile-service recommendation-service agent-service; do
            echo "--- Testing $s ---"
            docker run --rm -v "$PWD/services/$s":/app -v psia-m2:/root/.m2 -w /app maven:3.9-eclipse-temurin-21 mvn -q test || exit 1
        done
    else
        docker run --rm -v "$PWD/services/{{service}}":/app -v psia-m2:/root/.m2 -w /app maven:3.9-eclipse-temurin-21 mvn -q test
    fi

# Run Playwright E2E tests in Docker against the local stack
test-e2e:
    @echo "Starting infrastructure for E2E tests..."
    just start
    @echo "Waiting for Gateway to be ready on port 8080..."
    timeout 60 sh -c 'until curl -s http://localhost:8080/ > /dev/null; do sleep 2; done'
    docker run --rm \
      --network host \
      -v "$PWD/frontend:/work" \
      -w /work \
      mcr.microsoft.com/playwright:v1.64.0-jammy \
      sh -c "npm ci && npx playwright test"

# Run Playwright in UI mode for debugging
test-e2e-ui:
    @echo "Starting infrastructure for E2E tests..."
    just start
    @echo "Waiting for Gateway to be ready on port 8080..."
    timeout 60 sh -c 'until curl -s http://localhost:8080/ > /dev/null; do sleep 2; done'
    xhost +local:root || true
    docker run --rm -it \
      --network host \
      -e DISPLAY=$$DISPLAY \
      -v /tmp/.X11-unix:/tmp/.X11-unix \
      -v "$PWD/frontend:/work" \
      -w /work \
      mcr.microsoft.com/playwright:v1.64.0-jammy \
      sh -c "npm ci && xvfb-run npx playwright test --ui"