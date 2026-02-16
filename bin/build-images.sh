#!/bin/bash

valid_artifacts=('rhsm-subscriptions'
  'swatch-system-conduit'
  'swatch-producer-aws'
  'swatch-contracts'
  'swatch-producer-azure'
  'swatch-metrics'
  'swatch-billable-usage'
  'swatch-unleash-import'
  'swatch-metrics-hbi'
  'swatch-database'
  'swatch-utilization')

function build_failed() {
    # Return from the directory the script started in
    popd || exit 1
    exit 1
}

function print_valid_artifacts() {
  echo "Valid build artifacts are: $(echo "${valid_artifacts[@]}" | xargs | sed -e 's/ /, /g')"
}

function get_docker_file_path() {
  docker_file="unknown"
  if [[ "$1" == "rhsm-subscriptions" ]]; then
    docker_file="Dockerfile"
  elif [[ -f "$1/src/main/docker/Dockerfile.jvm" ]]; then
    docker_file="$1/src/main/docker/Dockerfile.jvm"
  else
    docker_file="$1/Dockerfile"
  fi

  if [[ ! -f "$docker_file" ]]; then
      echo "Unable to find docker file: ${docker_file}"
      build_failed
  fi
  echo $docker_file
}

function usage() {
  echo "Usage: $0 [-k] [-p platform] [-t tag] [BUILD_ARTIFACT...]"
  echo "-k            keep built images"
  echo "-p [platform] target platform (e.g. linux/amd64). Auto-detected on Apple Silicon."
  echo "-t [tag]      image tag"
  print_valid_artifacts
  echo "Providing no artifact ids will result in a build for all artifacts"
  exit 0
}
# Run this script from the root of the repo
pushd "$(git rev-parse --show-toplevel)" || exit

commit=$(git rev-parse --short=7 HEAD)
tag="$commit"
keep=0
platform=""

# Auto-detect Apple Silicon and default to linux/amd64
if [[ "$(uname -s)" == "Darwin" && "$(uname -m)" == "arm64" ]]; then
  platform="linux/amd64"
fi

while getopts ":hkp:t:" o; do
    case "$o" in
        k)
            keep=1
            ;;
        p)
            platform=${OPTARG}
            ;;
        t)
            tag=${OPTARG}
            ;;
        h)
            usage
            ;;
        *)
            usage
            ;;
    esac
done
shift $((OPTIND-1))

projects=("$@")
if [ ${#projects[@]} -eq 0 ]; then
  projects+=("${valid_artifacts[@]}")
fi

function validate_artifact() {
  for artifact in "${valid_artifacts[@]}"
  do
    if [[ "$artifact" == "$1" ]]; then
      return
    fi
  done
  echo "ERROR: Invalid build artifact specified: $1"
  print_valid_artifacts
  build_failed
}

# Validate that all applicable projects are valid.
for p in "${projects[@]}"; do
  validate_artifact "$p"
done

quay_user=$(podman login --get-login quay.io)
if [ $? -ne 0 ]; then
    echo "Login to quay.io first using podman login"
    exit 1
fi

# Exit script if a podman command fails
trap build_failed ERR

platform_args=()
if [[ -n "$platform" ]]; then
  echo "Target platform: $platform"
  platform_args+=(--platform "$platform")
fi

./mvnw clean
for p in "${projects[@]}"; do
  echo "Building ${p}"

  docker_file=$(get_docker_file_path "$p")
  podman build . -f "$docker_file" \
    "${platform_args[@]}" \
    --build-arg-file bin/dev-argfile.conf \
    -t quay.io/$quay_user/$p:$tag \
    --label "git-commit=${commit}" --ulimit nofile=2048:2048
done

function push_and_clean() {
  podman push "$1"
  if [ $keep -eq 0 ]; then
    podman rmi "$1"
  fi
}

# Do all builds (and make sure they succeed before any pushes)
for p in "${projects[@]}"; do
  push_and_clean "quay.io/$quay_user/$p:$tag"
done
