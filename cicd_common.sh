#!/bin/bash
if [[ -n "$DRY_RUN" ]]; then
  docker() {
    echo [DRYRUN] docker "$@"
  }
  podman() {
    echo [DRYRUN] podman "$@"
  }
  oc() {
    echo [DRYRUN] oc "$@"
  }
fi

get_dockerfile() {
  component=$1
  if [[ -f $component/src/main/docker/Dockerfile.jvm ]]; then
    echo "$component/src/main/docker/Dockerfile.jvm"
  else
    if [ "$component" == "rhsm-subscriptions" ]; then
        echo "./Dockerfile"
      else
        echo "$component/Dockerfile"
      fi
  fi
}

get_approot() {
  component=$1
  echo "$PWD"
}

# First sed removes leading ".", second sed removes leading "/"
# so that "./swatch-system-conduit" becomes "swatch-system-conduit"
SERVICES="rhsm-subscriptions `(find -name Dockerfile.jvm -exec dirname {} \;; find -regex '.*/Dockerfile' -exec dirname {} \;) | sed 's/^.//' | sed 's#^/##' | sed s'#/.*##'`"
