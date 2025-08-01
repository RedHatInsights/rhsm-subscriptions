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

export APP_NAME="rhsm"  # name of app-sre "application" folder this component lives in

export IQE_PLUGINS="rhsm-subscriptions"  # name of the IQE plugin for this APP
export IQE_MARKER_EXPRESSION="ephemeral"  # This is the value passed to pytest -m
# export IQE_FILTER_EXPRESSION=""  # This is the value passed to pytest -k
export IQE_CJI_TIMEOUT="120m"  # This is the time to wait for smoke test to complete or fail
# IQE plugin image
export IQE_IMAGE_TAG="rhsm-subscriptions"
# NOTE: workaround for frontend deployment not being ready yet below
export IQE_LOG_LEVEL="debug"
export IQE_RP_ARGS="true"
export IQE_PARALLEL_ENABLED="false"

# Install bonfire repo/initialize
CICD_URL=https://raw.githubusercontent.com/RedHatInsights/bonfire/master/cicd
curl -s $CICD_URL/bootstrap.sh > .cicd_bootstrap.sh && source .cicd_bootstrap.sh

# Initialize the GIT config which is required by the Gradle Nebula plugin to build the images
git config user.name "$(git --no-pager log --format=format:'%an' -n 1)"
git config user.email "$(git --no-pager log --format=format:'%ae' -n 1)"

IMAGES=""

export COMPONENT_NAME="rhsm"  # name of app-sre "resourceTemplate" in deploy.yaml for this component

for service in $SERVICES; do
  export IMAGE_NAME="quay.io/cloudservices/${service}"
  export DOCKERFILE=$(get_dockerfile "$service")
  docker build --ulimit nofile=2048:2048 \
    -t "${IMAGE_NAME}:${IMAGE_TAG}" \
    -t "${IMAGE_NAME}:${SMOKE_TEST_TAG}" \
    -t "${IMAGE_NAME}:qa" -f "$DOCKERFILE" .
  docker push "${IMAGE_NAME}:${IMAGE_TAG}"
  docker push "${IMAGE_NAME}:${SMOKE_TEST_TAG}"
  docker push "${IMAGE_NAME}:qa"

  IMAGES=" ${IMAGES} -i ${IMAGE_NAME}=${IMAGE_TAG} "
done

export IQE_IBUTSU_SOURCE="rhsm-ee-${IMAGE_TAG}"
# NOTE: uncomment the following line to test authenticated kafka
#NAMESPACE_POOL="managed-kafka"
EXTRA_DEPLOY_ARGS="--timeout 1800 ${IMAGES}"
OPTIONAL_DEPS_METHOD=none

# set CLI option for --no-remove-resources
export COMPONENTS_W_RESOURCES="app:rhsm app:export-service"

# NOTE: this ensures that all of the other services end up deployed with the latest template
export EXTRA_COMPONENTS="rhsm swatch-kafka-bridge $(find -name clowdapp.yaml -exec dirname {} \; | cut -d'/' -f2 | xargs)"
for EXTRA_COMPONENT_NAME in $EXTRA_COMPONENTS; do
    export EXTRA_DEPLOY_ARGS="${EXTRA_DEPLOY_ARGS} --set-template-ref ${EXTRA_COMPONENT_NAME}=${GIT_COMMIT}"
done

# Deploy to an ephemeral namespace for testing
source deploy_ephemeral_env.sh

# Run smoke tests with ClowdJobInvocation
source $CICD_ROOT/cji_smoke_test.sh
