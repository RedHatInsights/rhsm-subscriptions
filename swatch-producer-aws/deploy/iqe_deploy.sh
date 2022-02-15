#!/bin/bash

VENV_DIR=~/bonfire_venv

mkdir -p $VENV_DIR
python3 -m venv $VENV_DIR
. $VENV_DIR/bin/activate
pip install --upgrade crc-bonfire

IMAGE=quay.io/cloudservices/swatch-producer-aws
IMAGE_TAG=02142022
BONFIRE_APP_NAME=swatch-producer-aws
BONFIRE_COMPONENT_NAME=swatch-producer-aws
NAMESPACE=ephemeral-w9ysl9

bonfire deploy-iqe-cji ${BONFIRE_APP_NAME} -n ${NAMESPACE} --debug-pod