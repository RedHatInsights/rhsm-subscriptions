#!/bin/bash

VENV_DIR=~/bonfire_venv

mkdir -p $VENV_DIR
python3 -m venv $VENV_DIR
. $VENV_DIR/bin/activate
pip install --upgrade crc-bonfire

ns=ephemeral-w9ysl9

#ClowdApp name
COMPONENT_NAME=swatch-producer-aws

pod=$(bonfire deploy-iqe-cji ${COMPONENT_NAME} -n ${ns} --debug-pod)