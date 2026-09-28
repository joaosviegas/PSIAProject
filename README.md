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

recommendation-service covers movie search/browse (`GET /api/movies`) and
the "Ask AI" recommendation flow (`POST /api/recommendations`): pick some
genres and a mood, and it asks the LLM to pick from a genre-matched pool of
IMDb movies pulled from Postgres, falling back to a plain top-rated list if
the LLM is unavailable.

## Team

| Member | Owns |
|---|---|
| _João Sérgio Viegas_ | auth-service, profile-service, frontend: auth/profile/browse pages |
| _Diogo Lobo_ | recommendation-service, dataset, frontend: movie search + "Ask AI" page |
| _Miguel Silva_ | agent-service, frontend shell + feed page, compose/justfile integration |
