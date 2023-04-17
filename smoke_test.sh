#!/bin/bash

# this test job is used as a 'canary job' to monitor for an ephemeral env outage

set -ex

IMAGE="quay.io/cloudservices/rhsm-subscriptions"
APP_NAME="rhsm"  # name of app-sre "application" folder this component lives in
COMPONENT_NAME="rhsm"  # name of app-sre "resourceTemplate" in deploy.yaml for this component
IQE_PLUGINS="rhsm-subscriptions"
IQE_MARKER_EXPRESSION="ephemeral"
IQE_FILTER_EXPRESSION=""
IQE_ENV="clowder_smoke"
IQE_CJI_TIMEOUT="30m"
DEPLOY_TIMEOUT="1800"  # 15min

if [ -z $OC_LOGIN_TOKEN ]; then
    OC_LOGIN_TOKEN=$OC_LOGIN_TOKEN_DEV
    OC_LOGIN_SERVER=$OC_LOGIN_SERVER_DEV
fi

# Get bonfire helper scripts
CICD_URL=https://raw.githubusercontent.com/RedHatInsights/bonfire/master/cicd
rm -f .cicd_bootstrap.sh
curl -s $CICD_URL/bootstrap.sh > .cicd_bootstrap.sh && source .cicd_bootstrap.sh

# Smoke tests
source $CICD_ROOT/deploy_ephemeral_env.sh
source $CICD_ROOT/cji_smoke_test.sh
