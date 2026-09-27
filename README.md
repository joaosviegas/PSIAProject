# PSIA — AI Movie Recommendation Hub (group 37)

Microservices app for the Platforms for AI Systems project: register, build a movie profile, browse other users,
ask an LLM for recommendations, and get a feed pre-computed by a background agent.


## Prerequisites

- **Docker** (Desktop on macOS/Windows with >= 8 GB memory; on Windows use WSL2)
- **[just](https://github.com/casey/just)** — command runner (like make but simpler)
- **git**, **curl**, **jq** — used by `scripts/smoke.sh`
- **Python 3** — only needed for `just movies-seed` (filters the IMDb dataset before it's loaded)
- **Node.js 22** — only needed for `npm run dev` (hot-reload frontend dev); production builds happen in Docker

A JDK is **not** required. Maven builds run inside Docker containers.

## Quick start

```bash
# 1. Clone and configure
git clone <repo-url> && cd proj-agi
cp .env.example .env
# Edit .env: change every password and set JWT_SECRET to a random 32+ char string.

# 2. Get the movie dataset (downloads + filters IMDb data, ~1-2 min, first run only)
just movies-seed

# 3. Start everything
just start          # builds all images and pulls the LLM model (~1 GB, first run only)
just ps             # wait until every service shows "healthy"

# 4. Open the app
open http://localhost:8080    # or just visit it in a browser

# 5. Verify
just smoke          # runs end-to-end checks through the gateway
```

You can skip step 2 and run `just start` on its own - the app still comes up
fine, but movie search and "Ask AI" will have an empty catalog to work with
until you run `just movies-seed` (or `just movies-seed` followed by
`just restart recommendation-service` if the service is already running).

## Day-to-day commands

| Command | What it does |
|---|---|
| `just start` | Build images, start all containers |
| `just stop` | Stop containers (data volumes kept) |
| `just ps` | Container status and health |
| `just restart <service>` | Rebuild and restart one service, e.g. `just restart auth-service` |
| `just logs <service>` | Follow logs (omit service for all) |
| `just movies-seed` | Download + filter the IMDb dataset, then rebuild recommendation-service so it loads into Postgres |
| `just smoke` | End-to-end test through the gateway |
| `just test <service>` | Run Maven tests in Docker, e.g. `just test auth-service` |
| `just psql auth` | Open psql on a service's database |
| `just redis` | Open redis-cli |
| `just clean` | Stop and delete Postgres + Redis data (keeps LLM model and the downloaded dataset) |
| `just nuke` | Stop and delete EVERYTHING including the LLM model and the downloaded dataset |

## Movie dataset

recommendation-service's catalog comes from [IMDb's free non-commercial data
exports](https://developer.imdb.com/non-commercial-datasets/) rather than a
live API — no key or quota needed, just a one-time download.

```bash
just movies-seed                    # ~9-12k movies with 5000+ votes (default threshold)
just movies-seed min_votes=20000    # smaller, higher-rated catalog
```

This downloads `title.basics.tsv.gz` + `title.ratings.tsv.gz` into
`dataset/raw/`, filters them down to a small `movies.csv` (see
`dataset/README.md` for the details), and rebuilds + restarts
recommendation-service so `MovieDataLoader` picks up the new file on
startup. It's safe to re-run — the loader only populates the `movies` table
the first time it's empty, so if you want to refresh the catalog, run
`just psql movies` and `TRUNCATE movies;` first (or `just clean`, which
wipes the whole Postgres volume).

IMDb's non-commercial datasets are for personal/non-commercial use, which
this project falls under — say so in the report if you reference the data
source.

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
