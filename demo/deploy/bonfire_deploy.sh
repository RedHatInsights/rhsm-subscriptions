#!/bin/bash

VENV_DIR=~/bonfire_venv

mkdir -p $VENV_DIR
python3 -m venv $VENV_DIR
. $VENV_DIR/bin/activate
pip install --upgrade crc-bonfire

IMAGE=quay.io/lburnett/rhsm
IMAGE_TAG=quarkus_poc
BONFIRE_APP_NAME=lburnettquarkus
BONFIRE_COMPONENT_NAME=service_demo
NAMESPACE=ephemeral-fmfjx3

bonfire deploy -n $NAMESPACE \
--no-remove-resources $BONFIRE_COMPONENT_NAME \
-p $BONFIRE_COMPONENT_NAME/IMAGE=$IMAGE \
-i $IMAGE=$IMAGE_TAG $BONFIRE_APP_NAME