#!/bin/bash

commit=$(git rev-parse --short=7 HEAD)
tag=${1:-$commit}
quay_user=$(podman login --get-login quay.io)

if [ $? -ne 0 ]; then
    echo "Login to quay.io first using podman login"
    exit 1
fi

# Exit script if a podman command fails
trap exit ERR
podman build . -t quay.io/$quay_account/rhsm:$tag --label "git-commit=${commit}"
podman build . -f swatch-system-conduit/Dockerfile -t quay.io/$quay_user/swatch-system-conduit:$tag --label "git-commit=${commit}"
podman push quay.io/$quay_user/rhsm:$tag
podman push quay.io/$quay_user/swatch-system-conduit:$tag

