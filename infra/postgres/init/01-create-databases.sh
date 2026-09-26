#!/bin/bash
# Runs once, the first time the postgres container starts with an empty volume.
# Creates one database and one login role per microservice. Each role owns its
# database, and PUBLIC loses CONNECT, so a service cannot open another service's DB.
# To re-run after changing this file: `just clean` (deletes the volume), then `just start`.
set -euo pipefail

create_service_db() {
  local db="$1" role="$2" password="$3"
  echo "Creating database '${db}' owned by role '${role}'"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
       -v db="$db" -v role="$role" -v pw="$password" <<'EOSQL'
CREATE ROLE :"role" LOGIN PASSWORD :'pw';
CREATE DATABASE :"db" OWNER :"role";
REVOKE ALL ON DATABASE :"db" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"db" TO :"role";
EOSQL
}

create_service_db auth    auth_svc    "${AUTH_DB_PASSWORD:?AUTH_DB_PASSWORD is not set}"
create_service_db profile profile_svc "${PROFILE_DB_PASSWORD:?PROFILE_DB_PASSWORD is not set}"
create_service_db movies  movies_svc  "${MOVIES_DB_PASSWORD:?MOVIES_DB_PASSWORD is not set}"
