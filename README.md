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
| `just test-e2e` | Run Playwright E2E tests against the local Docker stack |
| `just test-e2e-ui` | Run Playwright E2E tests in UI mode for debugging |
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

## GCP Cloud Deployment (Kubernetes)

Everything lives under `gcpcloud/`. A Docker-based management container
(Dockerfile + supervisord) ships terraform, ansible, gcloud, and kubectl so
you don't need any of those installed locally.

### Prerequisites

- A GCP project with billing enabled and the Compute Engine API active.
- A GCP service account JSON key (`gcpcloud/YOUR-GCP-KEY.json`).
  Download it from GCP Console → IAM → Service Accounts → Create Key (JSON).
- A Docker Hub account with the project images pushed (see **Push images** below).

### 1. Configure

Edit `gcpcloud/terraform-gcp-variables.tf`:
```hcl
variable "GCP_PROJECT_ID" { default = "your-gcp-project-id" }
variable "GCP_MACHINE_TYPE" { default = "n1-standard-2" }  # 7.5 GB RAM — needed for Ollama
variable "GCP_ZONE" { default = "europe-west1-c" }          # change if zone is out of capacity
variable "DOCKER_HUB_USER" { default = "your-dockerhub-username" }
```

Edit `gcpcloud/terraform-gcp-provider.tf` — replace the credentials filename:
```hcl
credentials = file("psia-g37.json")   # must match the file you placed in gcpcloud/
```

### 2. Push images to Docker Hub

Before provisioning, build and push all service images so the K8s nodes can pull them:

```bash
# from repo root — build and tag each image
docker build -t <DOCKER_HUB_USER>/g37-auth-service           services/auth-service
docker build -t <DOCKER_HUB_USER>/g37-profile-service        services/profile-service
docker build -t <DOCKER_HUB_USER>/g37-recommendation-service services/recommendation-service
docker build -t <DOCKER_HUB_USER>/g37-agent-service          services/agent-service
docker build -t <DOCKER_HUB_USER>/g37-gateway                frontend

docker push <DOCKER_HUB_USER>/g37-auth-service
docker push <DOCKER_HUB_USER>/g37-profile-service
docker push <DOCKER_HUB_USER>/g37-recommendation-service
docker push <DOCKER_HUB_USER>/g37-agent-service
docker push <DOCKER_HUB_USER>/g37-gateway
```

### 3. Start the management container

```bash
cd gcpcloud
just start   # builds the mgmt image and runs the container
just mgmt    # opens a shell inside it
```

All following commands run **inside the mgmt container** unless noted otherwise.

### 4. Authenticate with GCP

```bash
gcloud auth login       # follow the browser link and paste the verification code
```

### 5. Generate an SSH key (first time only)

```bash
ssh-keygen -t rsa -b 2048   # press ENTER to all prompts — no passphrase
```

### 6. Provision infrastructure with Terraform

```bash
cd /root/project/gcpcloud
terraform init
terraform plan
terraform apply
```

Terraform creates 1 master + 3 worker VMs (`n1-standard-2`, Ubuntu 22.04) and
a firewall rule that opens ports 80, 443, 6443 (K8s API), 179 (Calico BGP),
and the NodePort range 30000–32767.

The final output lists the public IPs:
```
master    = <master-ip>
worker_IPs = ["worker1 = <ip1>", "worker2 = <ip2>", "worker3 = <ip3>"]
```

If you get a zone capacity error, change `GCP_ZONE` (e.g. `europe-west1-b`) and re-apply.

### 7. Fill in the Ansible inventory and /etc/hosts

Take the IPs from the terraform output and do **both** of the following.

**On the host** — edit `gcpcloud/gcphosts`:
```
master    ansible_host=<master-ip>  ansible_user=ubuntu ansible_connection=ssh
worker1   ansible_host=<ip1>        ansible_user=ubuntu ansible_connection=ssh
worker2   ansible_host=<ip2>        ansible_user=ubuntu ansible_connection=ssh
worker3   ansible_host=<ip3>        ansible_user=ubuntu ansible_connection=ssh
```

**Inside the mgmt container** — add entries to `/etc/hosts` so hostnames resolve:
```bash
# run these inside the container (just mgmt)
echo "<master-ip>  master"  >> /etc/hosts
echo "<ip1>        worker1" >> /etc/hosts
echo "<ip2>        worker2" >> /etc/hosts
echo "<ip3>        worker3" >> /etc/hosts
```

Because `gcphosts` is mounted from the host (`/root/project/gcpcloud/gcphosts`),
editing it on the host is enough for ansible. The `/etc/hosts` change is only
needed for the container session and must be repeated if you restart the container.

### 8. Ansible playbooks 

| Playbook | What it does |
|---|---|
| `ansible-gcp-configure-nodes.yml` | Sudoers + base packages on all nodes |
| `ansible-k8s-install.yml` | containerd, kubelet, kubeadm, kubectl (v1.28) on all nodes |
| `ansible-create-cluster.yml` | `kubeadm init` on master, Calico CNI, save join command |
| `ansible-workers-join.yml` | Workers join the cluster using the saved join command |
| `ansible-deploy-app.yml` | Apply K8s manifests for all project services |

Run them in order from inside the mgmt container:
```bash
cd /root/project/gcpcloud
ansible-playbook ansible-gcp-configure-nodes.yml
ansible-playbook ansible-k8s-install.yml
ansible-playbook ansible-create-cluster.yml
ansible-playbook ansible-workers-join.yml
ansible-playbook ansible-deploy-app.yml
```

Verify the cluster on the master:
```bash
# ssh into master (see GCP Console > Compute Engine > VM instances > master > SSH)
sudo kubectl get nodes   # all 4 nodes should show Ready
sudo kubectl get pods -A # all app pods should show Running
```

### 9. K8s manifests

All manifests live in `gcpcloud/k8s/` and are applied by `ansible-deploy-app.yml`.

**Before applying — set your secrets and image names:**

1. Edit `gcpcloud/k8s/00-secret.yaml` — replace all `change_me_*` values (same as `.env`).
2. In manifests `04` through `08`, replace `DOCKER_HUB_USER` with your Docker Hub username
   (the value you set in `terraform-gcp-variables.tf`).

| File | What it creates |
|---|---|
| `00-secret.yaml` | Secret with all passwords and JWT_SECRET |
| `01-postgres.yaml` | ConfigMap (init script) + PVC (5 Gi) + Deployment + ClusterIP Service |
| `02-redis.yaml` | PVC (1 Gi) + Deployment + ClusterIP Service |
| `03-ollama.yaml` | PVC (10 Gi) + Deployment + ClusterIP Service + pull Job |
| `04-auth-service.yaml` | Deployment + ClusterIP Service |
| `05-profile-service.yaml` | Deployment + ClusterIP Service |
| `06-recommendation-service.yaml` | Deployment + ClusterIP Service |
| `07-agent-service.yaml` | Deployment + ClusterIP Service |
| `08-gateway.yaml` | Deployment + NodePort Service (port **30080**) |
| `09-monitoring.yaml` | Prometheus + Postgres/Redis exporters (ClusterIP) + Grafana (NodePort 30030). Config comes from `infra/monitoring/` via the `monitoring-config` ConfigMap created by `ansible-deploy-app.yml` |

**After applying — pull the Ollama model:**

The `ollama-pull` Job (in `03-ollama.yaml`) runs automatically but needs the Ollama pod to be up first. Watch its progress:
```bash
# on master
sudo kubectl get pods -w         # wait for ollama-* pod to be Running
sudo kubectl logs job/ollama-pull -f  # watch the model download (~1 GB)
```

Open the app at `http://<any-worker-ip>:30080` once all pods are Running.

### 10. Teardown

```bash
# from inside mgmt container
terraform destroy
```

Check the GCP Console to confirm all VMs are gone, **leaving VMs running costs money**.

Then exit the container and stop it:
```bash
# from the host, in gcpcloud/
just stop
```

## Team

| Member | Owns |
|---|---|
| _João Sérgio Viegas_ | auth-service, profile-service, frontend: auth/profile/browse pages |
| _Diogo Lobo_ | recommendation-service, dataset, frontend: movie search + "Ask AI" page |
| _Miguel Silva_ | agent-service, frontend shell + feed page, compose/justfile integration |
