#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "$ROOT"

PROJECT=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --project)
      [[ $# -ge 2 ]] || {
        echo "Missing value for --project" >&2
        exit 1
      }
      PROJECT="$2"
      shift 2
      ;;
    *)
      echo "Unknown argument: $1" >&2
      echo "Usage: $0 [--project <module>]" >&2
      exit 1
      ;;
  esac
done

TMP="${TMPDIR:-/tmp}/rhsm-m2-repro-$$"
MAVEN_CENTRAL_BASE_URL="https://repo.maven.apache.org/maven2/"
MAVEN_REDHAT_GA_REPO_URL="https://maven.repository.redhat.com/ga/"
MAVEN_NEXUS_REDHAT_REPO_URL="https://nexus.corp.redhat.com/repository/maven-central/"
cleanup() { rm -rf "$TMP"; }
trap cleanup EXIT
mkdir -p "$TMP"

MAVEN_SETTINGS_PATH="$ROOT/.mvn/mvn_settings.xml"
if [[ ! -f "$MAVEN_SETTINGS_PATH" ]]; then
  echo "Missing $MAVEN_SETTINGS_PATH" >&2
  exit 1
fi

MVN_BIN="./mvnw"
if command -v mvn >/dev/null 2>&1; then
  MVN_BIN="mvn"
fi

MVN_OFFLINE_CMD=("$MVN_BIN" -s "$MAVEN_SETTINGS_PATH" -U -q -Dmaven.repo.local="$TMP" -DskipTests)
if [[ -n "$PROJECT" ]]; then
  MVN_OFFLINE_CMD+=(-pl "$PROJECT" -am)
fi
MVN_OFFLINE_CMD+=(dependency:go-offline)

MVN_GO_OFFLINE_MAX_ATTEMPTS="${MVN_GO_OFFLINE_MAX_ATTEMPTS:-6}"
MVN_GO_OFFLINE_BACKOFF_BASE_SEC="${MVN_GO_OFFLINE_BACKOFF_BASE_SEC:-5}"
MVN_GO_OFFLINE_BACKOFF_MAX_SEC="${MVN_GO_OFFLINE_BACKOFF_MAX_SEC:-120}"
attempt=1
while true; do
  if "${MVN_OFFLINE_CMD[@]}"; then
    break
  fi
  if [[ "$attempt" -ge "$MVN_GO_OFFLINE_MAX_ATTEMPTS" ]]; then
    echo "dependency:go-offline failed after ${MVN_GO_OFFLINE_MAX_ATTEMPTS} attempt(s)" >&2
    exit 1
  fi
  delay=$(( MVN_GO_OFFLINE_BACKOFF_BASE_SEC * (1 << (attempt - 1)) ))
  if [[ "$delay" -gt "$MVN_GO_OFFLINE_BACKOFF_MAX_SEC" ]]; then
    delay="$MVN_GO_OFFLINE_BACKOFF_MAX_SEC"
  fi
  printf 'dependency:go-offline failed (attempt %d/%d); retrying in %ds...\n' \
    "$attempt" "$MVN_GO_OFFLINE_MAX_ATTEMPTS" "$delay" >&2
  sleep "$delay"
  attempt=$((attempt + 1))
done

WRAPPER_PROPS="$ROOT/.mvn/wrapper/maven-wrapper.properties"
OUT_PATH="$ROOT/out/artifacts.lock.yaml"

repo_base_for() {
  case "$1" in
    central)
      printf '%s' "$MAVEN_CENTRAL_BASE_URL"
      ;;
    maven-central)
      printf '%s' "$MAVEN_CENTRAL_BASE_URL"
      ;;
    sonatype-maven-org)
      printf '%s' "$MAVEN_CENTRAL_BASE_URL"
      ;;
    redhat-ga-repository)
      printf '%s' "$MAVEN_REDHAT_GA_REPO_URL"
      ;;
    nexus-redhat-repository)
      printf '%s' "$MAVEN_NEXUS_REDHAT_REPO_URL"
      ;;
    jboss-public-repository-group)
      printf '%s' 'https://repository.jboss.org/nexus/content/groups/public/'
      ;;
    *)
      return 1
      ;;
  esac
}

sha256_stream() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum | awk '{print $1}'
  else
    shasum -a 256 | awk '{print $1}'
  fi
}

sha256_file() {
  local f="$1"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$f" | awk '{print $1}'
  else
    shasum -a 256 "$f" | awk '{print $1}'
  fi
}

sha256_url() {
  curl -fsSL "$1" | sha256_stream
}

seen_mark_string() {
  printf '%s' "$1" | sha256_stream
}

seen_has_rel() {
  local h
  h="$(seen_mark_string "$1")"
  [[ -f "$SEEN_DIR/$h" ]]
}

seen_mark_rel() {
  local h
  h="$(seen_mark_string "$1")"
  : >"$SEEN_DIR/$h"
}

yaml_escape() {
  local s="$1"
  s="${s//\\/\\\\}"
  s="${s//\"/\\\"}"
  printf '%s' "$s"
}

maven_rel_path_from_url() {
  local url="$1"
  if [[ "$url" == *"/maven2/"* ]]; then
    printf '%s' "${url#*maven2/}"
    return
  fi
  local after="${url#*://}"
  printf '%s' "${after#*/}"
}

parse_wrapper_props() {
  distributionUrl="" wrapperUrl=""
  [[ -f "$WRAPPER_PROPS" ]] || {
    echo "Missing $WRAPPER_PROPS" >&2
    exit 1
  }
  while IFS= read -r raw || [[ -n "$raw" ]]; do
    local line="${raw#"${raw%%[![:space:]]*}"}"
    line="${line%"${line##*[![:space:]]}"}"
    [[ -z "$line" || "$line" == \#* ]] && continue
    [[ "$line" != *"="* ]] && continue
    local key="${line%%=*}"
    local val="${line#*=}"
    key="${key#"${key%%[![:space:]]*}"}"; key="${key%"${key##*[![:space:]]}"}"
    val="${val#"${val%%[![:space:]]*}"}"; val="${val%"${val##*[![:space:]]}"}"
    case "$key" in
      distributionUrl) distributionUrl="$val" ;;
      wrapperUrl) wrapperUrl="$val" ;;
    esac
  done < "$WRAPPER_PROPS"
  [[ -n "$distributionUrl" && -n "$wrapperUrl" ]] || {
    echo "distributionUrl/wrapperUrl not found in $WRAPPER_PROPS" >&2
    exit 1
  }
}

repo_id_for_artifact() {
  local artifact_path="$1"
  local artifact_name
  artifact_name="$(basename "$artifact_path")"
  local dir
  dir="$(dirname "$artifact_path")"
  local rr="${dir}/_remote.repositories"
  [[ -f "$rr" ]] || {
    echo ""
    return
  }
  local prefix="${artifact_name}>"
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line#"${line%%[![:space:]]*}"}"
    line="${line%"${line##*[![:space:]]}"}"
    [[ -z "$line" || "$line" == \#* ]] && continue
    if [[ "$line" == "$prefix"* ]]; then
      local rest="${line#"$prefix"}"
      local rid="${rest%%=*}"
      rid="${rid#"${rid%%[![:space:]]*}"}"
      rid="${rid%"${rid##*[![:space:]]}"}"
      [[ -n "$rid" ]] && echo "$rid" && return
    fi
  done < "$rr"
  echo ""
}

should_skip_file() {
  local path="$1"
  local name
  name="$(basename "$path")"
  case "$name" in
    resolver-status.properties|_maven.repositories|maven-metadata-local.xml|maven-metadata.xml|_remote.repositories)
      return 0
      ;;
  esac
  case "$name" in
    *.sha1|*.lastUpdated|*.repositories|*.properties)
      return 0
      ;;
  esac
  local lname
  lname="$(printf '%s' "$name" | tr '[:upper:]' '[:lower:]')"
  [[ "$lname" == *.xml && "$name" == *maven-metadata* ]] && return 0
  case "$lname" in
    *.jar|*.pom|*.war|*.zip|*.module) return 1 ;;
    *) return 0 ;;
  esac
}

parse_wrapper_props
dist_rel="$(maven_rel_path_from_url "$distributionUrl")"
wrap_rel="$(maven_rel_path_from_url "$wrapperUrl")"

m2_root="$(cd "$TMP" && pwd)"
TMP="$m2_root"
SEEN_DIR="$TMP/.seen-rels"
mkdir -p "$SEEN_DIR"

declare -a ROW_RELS=()
declare -a ROW_URLS=()
declare -a ROW_HASHES=()

while IFS= read -r -d '' f; do
  should_skip_file "$f" && continue
  rel="${f#"$m2_root"/}"
  rid="$(repo_id_for_artifact "$f")"
  if [[ -z "$rid" ]]; then
    echo "Skipping artifact without remote repository mapping: ${rel}" >&2
    continue
  fi
  base="$(repo_base_for "$rid")" || {
    echo "Unknown Maven repository id '${rid}' for ${rel}" >&2
    exit 1
  }
  [[ "$base" == */ ]] || base="${base}/"
  hash="$(sha256_file "$f")"
  ROW_RELS+=("$rel")
  ROW_URLS+=("${base}${rel}")
  ROW_HASHES+=("$hash")
done < <(find "$m2_root" -type f -print0)

if [[ "${#ROW_RELS[@]}" -eq 0 ]]; then
  echo "No jar/pom/war/zip artifacts found under $m2_root" >&2
  exit 1
fi

SORT_IDX=()
while IFS= read -r idx; do
  [[ -n "$idx" ]] && SORT_IDX+=("$idx")
done < <(
  for i in "${!ROW_RELS[@]}"; do
    printf '%s\t%d\n' "${ROW_RELS[$i]}" "$i"
  done | LC_ALL=C sort -t $'\t' -k1,1 | cut -f2
)

count=0

add_extra() {
  local rel="$1"
  local url="$2"
  seen_has_rel "$rel" && return 0
  local ck="sha256:$(sha256_url "$url")"
  printf '  - download_url: "%s"\n' "$(yaml_escape "$url")"
  printf '    checksum: "%s"\n' "$(yaml_escape "$ck")"
  printf '    filename: "%s"\n' "$(yaml_escape "$rel")"
  seen_mark_rel "$rel"
  count=$((count + 1))
}

mkdir -p "$(dirname "$OUT_PATH")"
{
  printf '%s\n' "---" "metadata:" '  version: "1.0"' "artifacts:"
  for i in "${SORT_IDX[@]}"; do
    rel="${ROW_RELS[$i]}"
    url="${ROW_URLS[$i]}"
    hash="${ROW_HASHES[$i]}"
    seen_mark_rel "$rel"
    printf '  - download_url: "%s"\n' "$(yaml_escape "$url")"
    printf '    checksum: "sha256:%s"\n' "$hash"
    printf '    filename: "%s"\n' "$(yaml_escape "$rel")"
    count=$((count + 1))
  done

  add_extra "$dist_rel" "$distributionUrl"
  add_extra "$wrap_rel" "$wrapperUrl"
} >"$OUT_PATH"

echo "Wrote ${count} artifacts to ${OUT_PATH}"