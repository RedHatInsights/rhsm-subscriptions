#!/bin/bash

function build_failed() {
    # Return from the directory the script started in
    popd
    exit 1
}

function usage() {
  echo "Usage: $0 [-k] [-t tag] [BUILD_ARTIFACT...]"
  echo "-k       keep built images"
  echo "-t [tag] image tag"
  echo "Valid build artifacts are 'rhsm', 'conduit', 'swatch-producer-aws', 'swatch-contracts', 'swatch-producer-azure', 'swatch-metrics', 'swatch-billable-usage'"
  echo "Providing no artifact ids will result in a build for all artifacts"
  exit 0
}
# Run this script from the root of the repo
pushd "$(git rev-parse --show-toplevel)" || exit

commit=$(git rev-parse --short=7 HEAD)
tag="$commit"
keep=0

while getopts ":hkt:" o; do
    case "$o" in
        k)
            keep=1
            ;;
        t)
            tag=${OPTARG}
            ;;
        h)
            usage
            ;;
    esac
done
shift $((OPTIND-1))

projects=("$@")
if [ ${#projects[@]} -eq 0 ]; then
  projects[0]="rhsm"
  projects[1]="conduit"
  projects[2]="swatch-producer-aws"
  projects[3]="swatch-contracts"
  projects[4]="swatch-producer-azure"
  projects[5]="swatch-metrics"
  projects[6]="swatch-billable-usage"
fi

quay_user=$(podman login --get-login quay.io)
if [ $? -ne 0 ]; then
    echo "Login to quay.io first using podman login"
    exit 1
fi

# Exit script if a podman command fails
trap build_failed ERR

./gradlew clean
for p in "${projects[@]}"; do
  case "$p" in
    "rhsm")
      podman build . -f Dockerfile \
        --build-arg-file bin/dev-argfile.conf \
        -t quay.io/$quay_user/rhsm-subscriptions:$tag \
        --label "git-commit=${commit}" --ulimit nofile=2048:2048
      ;;
    "conduit")
      podman build . -f swatch-system-conduit/Dockerfile \
        --build-arg-file bin/dev-argfile.conf \
        -t quay.io/$quay_user/swatch-system-conduit:$tag \
        --label "git-commit=${commit}" --ulimit nofile=2048:2048
      ;;
    "swatch-producer-aws")
      podman build . -f swatch-producer-aws/src/main/docker/Dockerfile.jvm \
        --build-arg-file bin/dev-argfile.conf \
        -t quay.io/$quay_user/swatch-producer-aws:$tag \
        --label "git-commit=${commit}" --ulimit nofile=2048:2048
      ;;
    "swatch-contracts")
      podman build . -f swatch-contracts/src/main/docker/Dockerfile.jvm \
        --build-arg-file bin/dev-argfile.conf \
        -t quay.io/$quay_user/swatch-contracts:$tag \
        --label "git-commit=${commit}" --ulimit nofile=2048:2048
      ;;
    "swatch-producer-azure")
      podman build . -f swatch-producer-azure/src/main/docker/Dockerfile.jvm \
        --build-arg-file bin/dev-argfile.conf \
        -t quay.io/$quay_user/swatch-producer-azure:$tag \
        --label "git-commit=${commit}" --ulimit nofile=2048:2048
      ;;
    "swatch-metrics")
      podman build . -f swatch-metrics/src/main/docker/Dockerfile.jvm \
        --build-arg-file bin/dev-argfile.conf \
        -t quay.io/$quay_user/swatch-metrics:$tag \
        --label "git-commit=${commit}" --ulimit nofile=2048:2048
      ;;
    "swatch-billable-usage")
      podman build . -f swatch-billable-usage/src/main/docker/Dockerfile.jvm \
        --build-arg-file bin/dev-argfile.conf \
        -t quay.io/$quay_user/swatch-billable-usage:$tag \
        --label "git-commit=${commit}" --ulimit nofile=2048:2048
      ;;
    *) echo "Please use values from the set \"rhsm\", \"conduit\", \"swatch-producer-aws\", \"swatch-contracts\", \"swatch-producer-azure\", \"swatch-metrics\", \"swatch-billable-usage\"";;
  esac
done

function push_and_clean() {
  podman push "$1"
  if [ $keep -eq 0 ]; then
    podman rmi "$1"
  fi
}

# Do all builds (and make sure they succeed before any pushes)
for p in "${projects[@]}"; do
  case "$p" in
    "rhsm")
      push_and_clean "quay.io/$quay_user/rhsm-subscriptions:$tag"
      ;;
    "conduit")
      push_and_clean "quay.io/$quay_user/swatch-system-conduit:$tag"
      ;;
    "swatch-producer-aws")
      push_and_clean "quay.io/$quay_user/swatch-producer-aws:$tag"
      ;;
    "swatch-contracts")
      push_and_clean "quay.io/$quay_user/swatch-contracts:$tag"
      ;;
    "swatch-producer-azure")
      push_and_clean "quay.io/$quay_user/swatch-producer-azure:$tag"
      ;;
    "swatch-metrics")
      push_and_clean "quay.io/$quay_user/swatch-metrics:$tag"
      ;;
    "swatch-billable-usage")
      push_and_clean "quay.io/$quay_user/swatch-billable-usage:$tag"
      ;;
  esac
done
