#!/usr/bin/env bash
# https://github.com/olivergondza/bash-strict-mode
set -emEuo pipefail
trap 's=$?; echo >&2 "$0: Error on line "$LINENO": $BASH_COMMAND"; exit $s' ERR
# EXIT traps will be invoked even if the shell exits due to an ERR signal
trap cleanup EXIT

cleanup() {
  # So we don't get a warning about an undefined variable if the trap runs before TMP_DIR is defined
  TMP_DIR=${TMP_DIR:-}
  if [ -n "${TMP_DIR}" ]; then
    rm -rf "${TMP_DIR}"
  fi
}

name_cert() {
  local subject
  local serial
  local fingerprint
  subject=$(openssl x509 -in "$1" -noout -subject)
  serial=$(openssl x509 -in "$1" -noout -serial)
  fingerprint=$(openssl x509 -in "$1" -noout -fingerprint)
  echo "Certificate $subject ($serial) ($fingerprint)"
}

check_pem() {
  name=$(name_cert "$cert")
  if openssl x509 -checkend $WARNING_SECONDS -noout -in "$1"; then
    echo "${GREEN}$name is OK${RESET_COLOR}"
    return 0
  else
    echo "${RED}$name has expired or will do so within $WARNING_DAYS days!${RESET_COLOR}"
    return 1
  fi
}

check_pkcs12() {
  # Extract all certificates to temp files
  TMP_DIR=$(mktemp -d)
  openssl pkcs12 -in "$1" -nodes -passin file:"$PASSWORD_FILE" -nokeys | \
    awk '/BEGIN CERTIFICATE/,/END CERTIFICATE/' > "$TMP_DIR/all_certs.pem"

  # Split into individual certificates
  csplit -q -f "$TMP_DIR/cert-" -b %02d.pem -z "$TMP_DIR/all_certs.pem" \
    '/BEGIN CERTIFICATE/' '{*}' 2>/dev/null

  EXIT_STATUS=0
  for cert in "$TMP_DIR"/cert-*.pem; do
    if [ -s "$cert" ]; then
      name=$(name_cert "$cert")
      if openssl x509 -in "$cert" -checkend $WARNING_SECONDS -noout; then
        echo "${GREEN}$name is OK${RESET_COLOR}"
      else
        echo "${RED}$name has expired or will do so within $WARNING_DAYS days!${RESET_COLOR}"
        EXIT_STATUS=1
      fi
      echo
    fi
  done
  return $EXIT_STATUS
}

check_cert() {
  if [ ! -s "$1" ]; then
    echo "Error: Certificate file '$1' does not exist or is not a regular, non-empty file"
    return 1
  fi

  if [ ! -r "$1" ]; then
    echo "Error: Certificate file '$1' is not readable"
    return 1
  fi

  if [ "$TYPE" == "PEM" ]; then
    check_pem "$1"
    return $?
  fi

  if [ "$TYPE" == "PKCS12" ]; then
    check_pkcs12 "$1"
    return $?
  fi
}

usage() {
  echo "Usage: $0 [-t PEM|PKCS12] [-p PASSWORD_FILE] CERTIFICATES..."
  echo "-t TYPE          certificate type.  Either PEM or PKCS12"
  echo "-p PASSWORD_FILE file containing certificate password, if necessary"
  echo "-w WARNING_DAYS  if expiration is within this many days, issue a warning"
}

# tput requires info about the terminal to use
TERM=${TERM:-linux}
export TERM

RED=$(tput setaf 1 || echo '')
GREEN=$(tput setaf 2 || echo '')
RESET_COLOR=$(tput sgr0 || echo '')

: "${TYPE:=PEM}"
: "${WARNING_DAYS:=30}"
: "${PASSWORD_FILE:=}"

while getopts ":ht:p:w:" o; do
  case "$o" in
    t)
      TYPE=${OPTARG}
      ;;
    p)
      PASSWORD_FILE=${OPTARG}
      ;;
    w)
      WARNING_DAYS=${OPTARG}
      ;;
    h)
      usage
      exit 0
      ;;
    *)
      usage
      exit 0
      ;;
  esac
done
shift $((OPTIND-1))

if [ "$TYPE" != "PEM" ] && [ "$TYPE" != "PKCS12" ]; then
  echo "${RED}Error: TYPE must be either 'PEM' or 'PKCS12', got '$TYPE'${RESET_COLOR}"
  echo
  usage
  exit 1
fi

if ! [[ "$WARNING_DAYS" =~ ^[0-9]+$ ]] || [ "$WARNING_DAYS" -le 0 ]; then
  echo "${RED}Error: WARNING_DAYS must be a positive integer, got '$WARNING_DAYS'${RESET_COLOR}"
  echo
  usage
  exit 1
fi

WARNING_SECONDS=$((WARNING_DAYS * 86400))

if [ "$TYPE" == "PEM" ] && [ -n "$PASSWORD_FILE" ]; then
  echo "${RED}Error: Password file (-p) cannot be used with PEM certificates${RESET_COLOR}"
  echo
  usage
  exit 1
fi

CERTIFICATES=("$@")

if [ ${#CERTIFICATES[@]} -eq 0 ]; then
  echo "${RED}Error: No certificate files provided as arguments${RESET_COLOR}"
  echo
  usage
  exit 1
fi

EXIT_STATUS=0

for cert in "${CERTIFICATES[@]}"; do
  if ! check_cert "$cert"; then
    EXIT_STATUS=1
  fi
  echo
done

exit $EXIT_STATUS
