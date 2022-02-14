#!/bin/bash
get_dockerfile() {
  component=$1
  if [ "$component" == "rhsm-subscriptions" ]; then
    echo "./Dockerfile"
  else
    echo "$component/Dockerfile"
  fi
}

# First sed removes leading ".", second sed removes leading "/"
# so that "./swatch-system-conduit" becomes "swatch-system-conduit"
SERVICES="rhsm-subscriptions $(find -name Dockerfile -exec dirname {} \; | sed 's/^.//' | sed 's#^/##')"
