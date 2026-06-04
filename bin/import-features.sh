#!/usr/bin/env bash
# https://github.com/olivergondza/bash-strict-mode
set -eEuo pipefail
trap 's=$?; echo >&2 "$0: Error on line "$LINENO": $BASH_COMMAND"; exit $s' ERR
trap cleanup EXIT

# Note that Unleash will only issue up to a maximum of 10 tokens, so we'll clean up after ourselves
function cleanup() {
  # So we don't get a warning about an undefined variable if the trap runs before AUTH_ID is defined
  AUTH_ID=${AUTH_ID:-}
  if [ -n "${AUTH_ID}" ]; then
    curl -v -L -X DELETE "${UNLEASH_URL}/api/admin/user/tokens/${AUTH_ID}" \
      --header "Authorization: $AUTH_TOKEN"
  fi

  COOKIE_JAR=${COOKIE_JAR:-}
  if [ -n "${COOKIE_JAR}" ]; then
    rm "${COOKIE_JAR}"
  fi
}

# Here's how resolution works:
# * If UNLEASH_HOST is set in the environment, that's the value that's going to be used, period.
# * If not and the CLOWDER_CONFIG file has featureFlags.hostname, that's the value that will be used
# * If the CLOWDER_CONFIG file isn't readable or doesn't have featureFlags.hostname, and
#   UNLEASH_HOST isn't in the environment, default to localhost.

: "${CLOWDER_CONFIG:=/cdapp/cdappconfig.json}"
if [ -r "${CLOWDER_CONFIG}" ]; then
  # jq returns "null" instead of the empty string if something isn't there.  We want the empty
  # string so that later down we can still use parameter substitution to set a default value.  Use
  # the "//" operator and "empty" operator to effect this.
  # See https://github.com/jqlang/jq/issues/354#issuecomment-43147898
  : "${UNLEASH_SCHEME:=$(jq -r '.featureFlags.scheme // empty' "${CLOWDER_CONFIG}")}"
  : "${UNLEASH_HOST:=$(jq -r '.featureFlags.hostname // empty' "${CLOWDER_CONFIG}")}"
  : "${UNLEASH_PORT:=$(jq -r '.featureFlags.port // empty' "${CLOWDER_CONFIG}")}"
fi

: "${UNLEASH_USER:=admin}"
: "${UNLEASH_PASSWORD:=unleash4all}"
: "${UNLEASH_SCHEME:=http}"
: "${UNLEASH_HOST:=localhost}"
: "${UNLEASH_PORT:=4242}"
: "${UNLEASH_ADMIN_TOKEN:=}"

UNLEASH_URL="${UNLEASH_SCHEME}://${UNLEASH_HOST}:${UNLEASH_PORT}"

IMPORT_FILE=${1:-}
if [ -z "$IMPORT_FILE" ]; then
  echo "Usage: $(basename "$0") JSON_FILE"
  exit 1
fi

# Unleash offers multiple types of authentication tokens: admin tokens (deprecated), personal
# access tokens (PATs), and client tokens.  The tokens we care about are the first two.  The
# problem is that some APIs require PATs and some do not.  Notably the environment import endpoint
# /api/admin/features-batch/import (see
# https://docs.getunleash.io/reference/api/unleash/import-toggles and
# https://docs.getunleash.io/how-to/how-to-environment-import-export#import) *requires* a PAT.
#
# We are using an older, deprecated import API /api/admin/state/import (see
# https://docs.getunleash.io/reference/api/unleash/import and
# https://docs.getunleash.io/how-to/how-to-import-export) which accepts admin tokens.  We're
# using this API since it uses the same JSON format as the start-up import (see
# https://docs.getunleash.io/how-to/how-to-import-export#startup-import) that we use for the local
# container during development.
#
# The expected flow for using PATs seems to be that tokens are created via the web UI.  This won't
# work for us since we need the whole process to be automated.  PATs can be created via the
# /api/admin/user/tokens endpoint, but it requires a session cookie.  We can acquire one of
# those by logging in at /api/admin/user/tokens. The bad part is it requires a user name and
# password which are *not* provided via clowder!  Nevertheless, I'm adding the code to login and
# acquire a PAT for the future case when we need to move off of the deprecated import API.

# See https://stackoverflow.com/a/13864829
if [ -z "${UNLEASH_ADMIN_TOKEN}" ]; then
  COOKIE_JAR=$(mktemp -t cookie.XXXXX)

  curl -s -S -c "$COOKIE_JAR" -L "${UNLEASH_URL}/auth/simple/login" \
    --header "Content-Type: application/json" \
    --data @- > /dev/null <<EOF
    {
    "username": "$UNLEASH_USER",
    "password": "$UNLEASH_PASSWORD"
    }
EOF

  AUTH_JSON=$(curl -s -S -b "$COOKIE_JAR" -L "${UNLEASH_URL}/api/admin/user/tokens" \
    --header "Content-Type: application/json" \
    --data @- <<EOF
    {
    "description": "Admin PAT $(date +%s.%N)",
    "expiresAt": "$(date -d '+100 years' --utc +%Y-%m-%dT%H:%M:%S)Z"
    }
EOF
  )
  AUTH_TOKEN="$(echo $AUTH_JSON | jq -r '.secret')"
  AUTH_ID="$(echo $AUTH_JSON | jq -r '.id')"
else
  AUTH_TOKEN="${UNLEASH_ADMIN_TOKEN}"
fi

curl -L -X POST "${UNLEASH_URL}/api/admin/state/import" \
  --header "Content-Type: application/json" \
  --header "Authorization: ${AUTH_TOKEN}" \
  --data @"${IMPORT_FILE}"
