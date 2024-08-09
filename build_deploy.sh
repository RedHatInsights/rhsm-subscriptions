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

cleanup() {
  rm -rf "$DOCKER_CONFIG"
}
DOCKER_CONFIG=$(mktemp -d -p "$HOME" '.docker-XXXXXXX')
export DOCKER_CONFIG
trap cleanup EXIT

docker login -u="$QUAY_USER" --password-stdin quay.io <<< "$QUAY_TOKEN"

# Initialize the GIT config which is required by the Gradle Nebula plugin to build the images
git config user.name "$(git --no-pager log --format=format:'%an' -n 1)"
git config user.email "$(git --no-pager log --format=format:'%ae' -n 1)"

for service in $SERVICES; do
  IMAGE_NAME="quay.io/cloudservices/${service}"
  DOCKERFILE=$(get_dockerfile "$service")
  docker build --ulimit nofile=2048:2048 \
    -t "${IMAGE_NAME}:${IMAGE_TAG}" \
    -t "${IMAGE_NAME}:${SMOKE_TEST_TAG}" \
    -t "${IMAGE_NAME}:qa" -f "$DOCKERFILE" .
  docker push "${IMAGE_NAME}:${IMAGE_TAG}"
  docker push "${IMAGE_NAME}:${SMOKE_TEST_TAG}"
  docker push "${IMAGE_NAME}:qa"
done
