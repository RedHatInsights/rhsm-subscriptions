#!/bin/bash

python3 -m venv .bonfire_venv
source .bonfire_venv/bin/activate

pip install --upgrade pip 'setuptools<58' wheel
pip install --upgrade 'crc-bonfire>=2.17.2'

export NAMESPACE=$(bonfire namespace reserve)
export BONFIRE_APP_NAME=swatch-producer-aws
export BONFIRE_COMPONENT_NAME=swatch-producer-aws
export IMAGE=quay.io/cloudservices/swatch-producer-aws
export IMAGE_TAG=latest

bonfire deploy -n $NAMESPACE \
--no-remove-resources $BONFIRE_COMPONENT_NAME \
-p $BONFIRE_COMPONENT_NAME/IMAGE=$IMAGE \
-i $IMAGE=$IMAGE_TAG $BONFIRE_APP_NAME