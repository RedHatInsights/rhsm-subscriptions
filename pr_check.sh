#!/bin/bash

set -exv

IMAGE="quay.io/cloudservices/rhsm-subscriptions"
IMAGE_TAG=$(git rev-parse --short=7 HEAD)

docker build -f Dockerfile.test -t "${IMAGE}:${IMAGE_TAG}" .
docker run --rm "${IMAGE}:${IMAGE_TAG}" /tmp/src/gradlew --no-daemon test
docker run --rm "${IMAGE}:${IMAGE_TAG}" /tmp/src/gradlew --no-daemon checkstyleMain checkstyleTest
docker image rm "${IMAGE}:${IMAGE_TAG}"
