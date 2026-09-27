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
    rm -rf dataset/raw services/recommendation-service/src/main/resources/data/movies.csv

# Delete local build output (node_modules, dist, target) and Docker's build cache. Rarely needed.
clean-build:
    rm -rf frontend/node_modules frontend/dist services/*/target
    docker builder prune -f

# Rebuild and restart one service, e.g. `just restart auth-service`
restart service:
    docker compose up -d --build {{service}}

# Download + filter the IMDb dataset into recommendation-service's movie
# catalog, then rebuild it so MovieDataLoader picks up the new CSV on
# startup (see dataset/README.md). Safe to re-run: the loader only
# populates the `movies` table the first time it's empty.
# e.g. `just movies-seed` or `just movies-seed min_votes=20000`
movies-seed min_votes="5000": _check-env
    mkdir -p dataset/raw
    @gzip -t dataset/raw/title.basics.tsv.gz 2>/dev/null || (rm -f dataset/raw/title.basics.tsv.gz && curl -fsSL -o dataset/raw/title.basics.tsv.gz https://datasets.imdbws.com/title.basics.tsv.gz)
    @gzip -t dataset/raw/title.ratings.tsv.gz 2>/dev/null || (rm -f dataset/raw/title.ratings.tsv.gz && curl -fsSL -o dataset/raw/title.ratings.tsv.gz https://datasets.imdbws.com/title.ratings.tsv.gz)
    @test -s services/recommendation-service/src/main/resources/data/movies.csv || python3 dataset/filter_imdb.py \
        --basics dataset/raw/title.basics.tsv.gz \
        --ratings dataset/raw/title.ratings.tsv.gz \
        --min-votes {{min_votes}} \
        --out services/recommendation-service/src/main/resources/data/movies.csv
    docker compose up -d --build recommendation-service
    @echo "Catalog seeded — check 'just logs recommendation-service' for 'Loaded N movies into the catalog'."

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

# Run a service's tests without a local JDK, e.g. `just test auth-service`
test service:
    docker run --rm -v "$PWD/services/{{service}}":/app -v psia-m2:/root/.m2 -w /app maven:3.9-eclipse-temurin-21 mvn -q test