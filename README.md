# PSIA — AI Movie Recommendation Hub (group 37)

Microservices app for the Platforms for AI Systems project: register, build a movie profile, browse other users,
ask an LLM for recommendations, and get a feed pre-computed by a background agent.


## Prerequisites

- **Docker** (Desktop on macOS/Windows with >= 8 GB memory; on Windows use WSL2)
- **[just](https://github.com/casey/just)** — command runner (like make but simpler)
- **git**, **curl**, **jq** — used by `scripts/smoke.sh`
- **Node.js 22** — only needed for `npm run dev` (hot-reload frontend dev); production builds happen in Docker

A JDK is **not** required. Maven builds run inside Docker containers.

## Quick start

```bash
# 1. Clone and configure
git clone <repo-url> && cd proj-agi
cp .env.example .env
# Edit .env: change every password and set JWT_SECRET to a random 32+ char string.

# 2. Start everything (movie catalog loads automatically on first startup)
just start          # builds all images and pulls the LLM model (~1 GB, first run only)
just ps             # wait until every service shows "healthy"

# 3. Open the app
open http://localhost:8080    # or just visit it in a browser

# 4. Verify
just smoke          # runs end-to-end checks through the gateway
```

The movie catalog (~7k enriched movies with posters, overviews, directors and
cast) is bundled in the Docker image and loaded into Postgres automatically on
first startup. No external downloads or scripts needed.

## Day-to-day commands

| Command | What it does |
|---|---|
| `just start` | Build images, start all containers |
| `just stop` | Stop containers (data volumes kept) |
| `just ps` | Container status and health |
| `just restart <service>` | Rebuild and restart one service, e.g. `just restart auth-service` |
| `just logs <service>` | Follow logs (omit service for all) |
| `just smoke` | End-to-end test through the gateway |
| `just test <service>` | Run Maven tests in Docker, e.g. `just test auth-service` |
| `just monitoring` | (Re)start only Prometheus, Grafana and the exporters |
| `just psql auth` | Open psql on a service's database |
| `just redis` | Open redis-cli |
| `just clean` | Stop and delete Postgres + Redis data (keeps LLM model and the downloaded dataset) |
| `just nuke` | Stop and delete EVERYTHING including the LLM model |

## Movie dataset

The catalog (~7k movies) is a pre-built enriched CSV
(`dataset/movies_enriched.csv`) bundled into recommendation-service's Docker
image at build time. `MovieDataLoader` bulk-inserts it into the `movies` table
on first startup (no-op if the table already has rows). See `dataset/README.md`.

To refresh the catalog: `just psql movies` → `TRUNCATE movies;`, then
`just restart recommendation-service`.

## Frontend development

```bash
cd frontend && npm run dev    # Vite dev server with hot reload, proxies /api to localhost:8080
```

Requires the backend to be running (`just start`).

## Services

| Service | Path via gateway | Store |
|---|---|---|
| gateway (nginx + Vue) | `/` | — |
| auth-service | `/api/auth/*` | Postgres db `auth` |
| profile-service | `/api/profiles/*` | Postgres db `profile` |
| recommendation-service | `/api/movies/*`, `/api/recommendations` | Postgres db `movies` + Ollama |
| agent-service | `/api/feed/*` | Redis + Ollama |
| llm (Ollama) | internal | model volume |
| prometheus | `http://localhost:9090` | metrics volume |
| grafana | `http://localhost:3000` | dashboards volume |
| postgres-exporter, redis-exporter | internal | — |

recommendation-service covers movie search/browse (`GET /api/movies`) and
the "Ask AI" recommendation flow (`POST /api/recommendations`): pick some
genres and a mood, and it asks the LLM to pick from a genre-matched pool of
IMDb movies pulled from Postgres, falling back to a plain top-rated list if
the LLM is unavailable.

agent-service builds each user's feed in the background. Every
`AGENT_INTERVAL_SECONDS` it fetches profiles changed since its last run (plus
users whose feed was requested but missing), asks the LLM to pick movies from
their favourite genres (skipping ones they already liked/disliked), adds movies
liked by similar users, and stores the result in Redis for `FEED_TTL_HOURS`.
`GET /api/feed` returns the stored feed, or `202 {"status":"pending"}` and
queues the user for the next run.

## Monitoring

`just start` also brings up the monitoring stack:

- **Prometheus** (`http://localhost:9090`) scrapes every 15s: `/actuator/prometheus` on the four
  Spring services, `postgres-exporter` and `redis-exporter`. Config: `infra/monitoring/prometheus/prometheus.yml`.
- **Grafana** (`http://localhost:3000`, user `admin`, password `GRAFANA_ADMIN_PASSWORD` from `.env`)
  opens on the provisioned **PSIA Movie Hub** dashboard (`infra/monitoring/grafana/`).

Dashboard sections:

| Section | What it shows |
|---|---|
| Overview | Targets up/down, request rate, p95 latency, 4xx/5xx rate per service |
| Endpoints | Request rate and p95 latency per endpoint (filter by service) |
| AI / LLM | LLM calls by outcome, LLM p95/avg latency, recommendations by mode, AI success ratio |
| Background agent | Feed builds (ai/fallback/failed), build duration, builds in progress, feed requests by status, agent ticks |
| Users | Registrations, logins (success/failure), profile updates |
| JVM & connection pools | Heap, CPU, threads, GC pauses, Hikari DB connections |
| Data stores | Postgres/Redis up, connections and transactions per database, Redis keys and memory |

Custom application metrics (on top of Spring Boot's HTTP/JVM/Hikari metrics):

| Metric | Service | Labels |
|---|---|---|
| `llm_request_seconds` (histogram) | recommendation, agent | `outcome` = success / timeout / error |
| `recommendations_total` | recommendation | `mode` = ai / fallback / quick / cached |
| `feed_build_seconds` (histogram) | agent | — |
| `feed_builds_total` | agent | `result` = ai / fallback / failed |
| `feed_builds_in_progress` (gauge) | agent | — |
| `feed_requests_total` | agent | `status` = ready / building / needs_preferences |
| `agent_ticks_total`, `agent_profiles_queued_total` | agent | `result` = ok / error |
| `auth_registrations_total`, `auth_logins_total` | auth | `result` = success / failure |
| `profile_updates_total` | profile | — |

Actuator endpoints are not routed by the gateway; only Prometheus reaches them over the internal network.

## Team

| Member | Owns |
|---|---|
| _João Sérgio Viegas_ | auth-service, profile-service, frontend: auth/profile/browse pages |
| _Diogo Lobo_ | recommendation-service, dataset, frontend: movie search + "Ask AI" page |
| _Miguel Silva_ | agent-service, frontend shell + feed page, compose/justfile integration |
