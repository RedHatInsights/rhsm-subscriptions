#!/bin/bash

VENV_DIR=~/bonfire_venv

mkdir -p $VENV_DIR
python3 -m venv $VENV_DIR
. $VENV_DIR/bin/activate
pip install --upgrade crc-bonfire

IMAGE=quay.io/cloudservices/rhsm-subscriptions
IMAGE_TAG=latest
CONDUIT_IMAGE=quay.io/cloudservices/swatch-system-conduit
CONDUIT_IMAGE_TAG=latest

BONFIRE_APP_NAME=rhsm-subscriptions
BONFIRE_COMPONENT_NAME=rhsm-subscriptions
NAMESPACE=$(bonfire namespace reserve)

bonfire deploy -n $NAMESPACE \
--no-remove-resources $BONFIRE_COMPONENT_NAME \
-p $BONFIRE_COMPONENT_NAME/IMAGE=$IMAGE \
-i $IMAGE=$IMAGE_TAG \
-p $BONFIRE_COMPONENT_NAME/CONDUIT_IMAGE=CONDUIT_IMAGE \
-i CONDUIT_IMAGE=CONDUIT_IMAGE_TAG \
$BONFIRE_APP_NAME