#!/bin/bash

function build_failed() {
    # Return from the directory the script started in
    popd
    exit 1
}

# Run this script from the root of the repo
pushd $(git rev-parse --show-toplevel)

commit=$(git rev-parse --short=7 HEAD)
tag=${1:-$commit}
quay_user=$(podman login --get-login quay.io)

if [ $? -ne 0 ]; then
    echo "Login to quay.io first using podman login"
    exit 1
fi

# Exit script if a podman command fails
trap build_failed ERR
podman build . -t quay.io/$quay_user/rhsm:$tag --label "git-commit=${commit}"
podman build . -f swatch-system-conduit/Dockerfile -t quay.io/$quay_user/swatch-system-conduit:$tag --label "git-commit=${commit}"

./gradlew :swatch-producer-aws:assemble
pushd swatch-producer-aws
podman build . -f src/main/docker/Dockerfile.jvm -t quay.io/$quay_user/swatch-producer-aws:$tag --label "git-commit=${commit}"
popd

podman push quay.io/$quay_user/rhsm:$tag
podman push quay.io/$quay_user/swatch-system-conduit:$tag
podman push quay.io/$quay_user/swatch-producer-aws:$tag
