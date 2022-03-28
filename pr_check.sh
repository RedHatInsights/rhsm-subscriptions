#!/bin/bash
# NOTE: if you need to debug this file, use DRY_RUN=true to echo docker/podman/oc commands without running them

# before we run common consoledot builds, prepare the binary artifacts for quarkus style builds
./podman_run.sh ./gradlew assemble

source cicd_common.sh

export APP_NAME="rhsm"  # name of app-sre "application" folder this component lives in
export COMPONENT_NAME="rhsm"
export IQE_PLUGINS="rhsm-subscriptions"  # name of the IQE plugin for this APP
export IQE_MARKER_EXPRESSION="smoke"  # This is the value passed to pytest -m
export IQE_FILTER_EXPRESSION=""  # This is the value passed to pytest -k
export IQE_CJI_TIMEOUT="30m"  # This is the time to wait for smoke test to complete or fail

# Install bonfire repo/initialize
CICD_URL=https://raw.githubusercontent.com/RedHatInsights/bonfire/master/cicd
curl -s $CICD_URL/bootstrap.sh > .cicd_bootstrap.sh && source .cicd_bootstrap.sh

APP_ROOT=$PWD

# Run the unit tests
source $APP_ROOT/unit_test.sh

# prebuild artifacts for quarkus builds
for service in $SERVICES; do
  export COMPONENT_NAME="$service-clowdapp"  # name of app-sre "resourceTemplate" in deploy.yaml for this component
  export IMAGE="quay.io/cloudservices/$service"  # the image location on quay
  export DOCKERFILE="$(get_dockerfile $service)"

  # Build the image and push to quay
  APP_ROOT=$(get_approot $service)
  source $CICD_ROOT/build.sh
  # Deploy to an ephemeral namespace for testing
  source $CICD_ROOT/deploy_ephemeral_env.sh
done

# Run smoke tests with ClowdJobInvocation
source $CICD_ROOT/cji_smoke_test.sh
