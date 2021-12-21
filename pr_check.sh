#!/bin/bash

export APP_NAME="rhsm"  # name of app-sre "application" folder this component lives in
export COMPONENT_NAME="rhsm" # Need to hardcode this env variable because deploy scripts assume that service to component relationship is 1:1. swatch-system-conduit is a separate quay image, but part of the rhsm ClowdApp/component
export IQE_PLUGINS="rhsm-subscriptions"  # name of the IQE plugin for this APP
export IQE_MARKER_EXPRESSION="clowder_smoke"  # This is the value passed to pytest -m
export IQE_FILTER_EXPRESSION=""  # This is the value passed to pytest -k
export IQE_CJI_TIMEOUT="30m"  # This is the time to wait for smoke test to complete or fail
export COMPONENTS_W_RESOURCES=$COMPONENT_NAME
export DEPLOY_TIMEOUT="3600" #1hr
EXTRA_DEPLOY_ARGS="-f"

# Install bonfire repo/initialize
CICD_URL=https://raw.githubusercontent.com/RedHatInsights/bonfire/master/cicd
curl -s $CICD_URL/bootstrap.sh > .cicd_bootstrap.sh && source .cicd_bootstrap.sh

# source cicd_common.sh
for service in "rhsm-subscriptions"; do

  export IMAGE="quay.io/cloudservices/$service"  # the image location on quay
  export DOCKERFILE="$(get_dockerfile $service)"

  # Build the image and push to quay
  source $CICD_ROOT/build.sh
done

export EXTRA_DEPLOY_ARGS=$EXTRA_DEPLOY_ARGS

# Run the unit tests
#source $APP_ROOT/unit_test.sh

# Deploy to an ephemeral namespace for testing
source $CICD_ROOT/deploy_ephemeral_env.sh

# Run somke tests with ClowdJobInvocation
#source $CICD_ROOT/cji_smoke_test.sh
