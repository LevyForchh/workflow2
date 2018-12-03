#!/usr/bin/env bash

set -euo pipefail

DIR="${0%/*}"
export COMPOSE_PROJECT_NAME=workflow_test

docker-compose -f "$DIR/test-db-compose.yml" pull
docker-compose -f "$DIR/test-db-compose.yml" up -d

docker pull gcr.io/liveramp-eng/workflow2/sqldump:latest
docker run --rm --name test_migrate --network=workflow_test_shared -e "DB_PORT=3306" -e "DB_USERNAME=root" -e "DB_HOSTNAME=mysql" gcr.io/liveramp-eng/workflow2/sqldump:latest