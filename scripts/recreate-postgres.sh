#!/bin/bash

set -e

COMPOSE_FILE="$(dirname "$0")/../backend/docker-compose.yaml"

echo "Stopping and removing the postgres container..."
docker compose -f "$COMPOSE_FILE" stop postgres
docker compose -f "$COMPOSE_FILE" rm -f postgres

echo "Removing the postgres data volume..."
docker volume rm o-fingidor_postgres_data

echo "Starting postgres with a fresh empty database..."
docker compose -f "$COMPOSE_FILE" up -d postgres

echo "Done. Postgres is running with an empty database."
