#!/bin/bash
# NOTE: if you need to debug this file, use DRY_RUN=true to echo docker commands without running them

set -exv

source cicd_common.sh
IMAGE_TAG=$(git rev-parse --short=7 HEAD)
SMOKE_TEST_TAG="latest"

if [[ -z "$QUAY_USER" || -z "$QUAY_TOKEN" ]]; then
    echo "QUAY_USER and QUAY_TOKEN must be set"
    exit 1
fi

DOCKER_CONF="$PWD/.docker"
mkdir -p "$DOCKER_CONF"
docker --config="$DOCKER_CONF" login -u="$QUAY_USER" -p="$QUAY_TOKEN" quay.io

for service in $SERVICES; do
  IMAGE="quay.io/cloudservices/$service"
  DOCKERFILE=$(get_dockerfile $service)
  docker --config="$DOCKER_CONF" build --ulimit nofile=2048:2048 "${IMAGE}:${IMAGE_TAG}" $PWD -f $PWD/$(get_dockerfile $service)
  docker --config="$DOCKER_CONF" push "${IMAGE}:${IMAGE_TAG}"
  docker --config="$DOCKER_CONF" tag "${IMAGE}:${IMAGE_TAG}" "${IMAGE}:${SMOKE_TEST_TAG}"
  docker --config="$DOCKER_CONF" push "${IMAGE}:${SMOKE_TEST_TAG}"
  docker --config="$DOCKER_CONF" tag "${IMAGE}:${IMAGE_TAG}" "${IMAGE}:qa"
  docker --config="$DOCKER_CONF" push "${IMAGE}:qa"
done
