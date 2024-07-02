#!/bin/bash
# NOTE: if you need to debug this file, use DRY_RUN=true to echo docker/podman/oc commands without running them

# before we run common consoledot builds, prepare the binary artifacts for quarkus style builds
./podman_run.sh

source cicd_common.sh

export APP_NAME="rhsm"  # name of app-sre "application" folder this component lives in

export IQE_PLUGINS="rhsm-subscriptions"  # name of the IQE plugin for this APP
export IQE_MARKER_EXPRESSION="ephemeral"  # This is the value passed to pytest -m
# export IQE_FILTER_EXPRESSION=""  # This is the value passed to pytest -k
export IQE_CJI_TIMEOUT="30m"  # This is the time to wait for smoke test to complete or fail
# IQE plugin image
export IQE_IMAGE_TAG="rhsm-subscriptions"
# NOTE: workaround for frontend deployment not being ready yet below
export IQE_LOG_LEVEL="debug"
export IQE_RP_ARGS="true"
export IQE_PARALLEL_ENABLED="false"


# Install bonfire repo/initialize
CICD_URL=https://raw.githubusercontent.com/RedHatInsights/bonfire/master/cicd
curl -s $CICD_URL/bootstrap.sh > .cicd_bootstrap.sh && source .cicd_bootstrap.sh
# Borrow the venv that bonfire sets up to do validation of our topic references

# Disable the validation of topics for now until SWATCH-1904 is resolved.
# python bin/validate-topics.py

# Initialize the GIT config which is required by the Gradle Nebula plugin to build the images
git config user.name "$(git --no-pager log --format=format:'%an' -n 1)"
git config user.email "$(git --no-pager log --format=format:'%ae' -n 1)"

IMAGES=""

export COMPONENT_NAME="rhsm"  # name of app-sre "resourceTemplate" in deploy.yaml for this component
# prebuild artifacts for quarkus builds
for service in $SERVICES; do
  export IMAGE="quay.io/cloudservices/$service"  # the image location on quay
  export DOCKERFILE="$(get_dockerfile $service)"

  # Build the image and push to quay
  APP_ROOT=$(get_approot $service)
  source $CICD_ROOT/build.sh

  IMAGES=" ${IMAGES} -i ${IMAGE}=${IMAGE_TAG} "
done

APP_ROOT=$PWD
export IQE_IBUTSU_SOURCE="rhsm-ephemeral-${IMAGE_TAG}"
# NOTE: uncomment the following line to test authenticated kafka
#NAMESPACE_POOL="managed-kafka"
EXTRA_DEPLOY_ARGS="--timeout 1800 ${IMAGES}"
OPTIONAL_DEPS_METHOD=none

# set CLI option for --no-remove-resources
export COMPONENTS_W_RESOURCES="app:rhsm"

# NOTE: this ensures that all of the other services end up deployed with the latest template
export EXTRA_COMPONENTS="rhsm $(find -name clowdapp.yaml -exec dirname {} \; | cut -d'/' -f2 | xargs)"
for EXTRA_COMPONENT_NAME in $EXTRA_COMPONENTS; do
  export EXTRA_DEPLOY_ARGS="${EXTRA_DEPLOY_ARGS} --set-template-ref ${EXTRA_COMPONENT_NAME}=${GIT_COMMIT}"
done

# temporary to test bonfire updates
pip install --pre --upgrade crc-bonfire

# Deploy to an ephemeral namespace for testing
source deploy_ephemeral_env.sh

# Run smoke tests with ClowdJobInvocation
 source $CICD_ROOT/cji_smoke_test.sh
# During the PR checks the Ibutsu URL and test run IDs are published as a comment by InsightsDroid account (on GitHub) or iqe-bot (on GitLab).
# This is achieved by adding this line to `pr_check.sh` of the repo:
 source $CICD_ROOT/post_test_results.sh


