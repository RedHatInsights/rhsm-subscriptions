#!/usr/bin/env bash
#
# Generates `out/artifacts.lock.yaml` with deterministic SHA-256 checksums for
# Maven artifacts resolved by this repository.
#
# What this script does:
# - Resolves module dependencies from `dependency:tree` plus required Maven
#   plugins using the default local Maven repository (`~/.m2/repository`).
# - Collects resolved artifact files (.jar/.pom/.war/.zip/.module), skipping
#   Maven metadata and bookkeeping files.
# - Maps each artifact to a known source repository URL using
#   `_remote.repositories` metadata.
# - Computes SHA-256 checksums for each artifact and writes the final lock file.
# - Adds Maven Wrapper distribution and wrapper JAR entries to the same lock.
#
# Inputs:
# - Optional `--project <module>` to restrict resolution scope (`-pl ... -am`).
# - Maven settings at `.mvn/maven-settings.xml`.
#
# Output:
# - `out/artifacts.lock.yaml`
#
# Logging:
# - High-level commands are printed before execution for visibility.
#
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "$ROOT"

PROJECT=""
log_step() { printf '[generate-artifacts-lock] %s\n' "$*" >&2; }
run_cmd() {
  printf '[generate-artifacts-lock] +'
  printf ' %q' "$@"
  printf '\n' >&2
  "$@"
}

resolve_root_property() {
  local prop="$1"
  sed -n "s|.*<${prop}>\\(.*\\)</${prop}>.*|\\1|p" "$ROOT/pom.xml" | head -n 1
}

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

WORK_TMP="${TMPDIR:-/tmp}/rhsm-artifacts-lock-$$"
MAVEN_CENTRAL_BASE_URL="https://repo.maven.apache.org/maven2/"
MAVEN_GOOGLE_CENTRAL_MIRROR_URL="https://maven-central.storage-download.googleapis.com/maven2/"
MAVEN_REDHAT_GA_REPO_URL="https://maven.repository.redhat.com/ga/"
MAVEN_SPLUNK_ARTIFACTORY_URL="https://splunk.jfrog.io/splunk/ext-releases-local/"
MAVEN_CONFLUENT_REPO_URL="https://packages.confluent.io/maven/"
MAVEN_APACHE_SNAPSHOTS_URL="https://repository.apache.org/snapshots/"
cleanup() { rm -rf "$WORK_TMP"; }
trap cleanup EXIT
mkdir -p "$WORK_TMP"

MAVEN_SETTINGS_PATH="$ROOT/.mvn/maven-settings.xml"
if [[ ! -f "$MAVEN_SETTINGS_PATH" ]]; then
  echo "Missing $MAVEN_SETTINGS_PATH" >&2
  exit 1
fi

MVN_BIN="./mvnw"
if command -v mvn >/dev/null 2>&1; then
  MVN_BIN="mvn"
fi

M2_ROOT="$HOME/.m2/repository"
mkdir -p "$M2_ROOT"

MVN_BASE_CMD=("$MVN_BIN" -q -s "$MAVEN_SETTINGS_PATH" -DskipTests)
if [[ -n "$PROJECT" ]]; then
  MVN_BASE_CMD+=(-pl "$PROJECT" -am)
fi
DEPS_TREE_LIST="$WORK_TMP/dependency-tree.txt"
PLUGINS_LIST="$WORK_TMP/plugin-list.txt"
MVN_DEPS_CMD=("${MVN_BASE_CMD[@]}" dependency:tree -DoutputFile="$DEPS_TREE_LIST")
MVN_PLUGINS_CMD=("${MVN_BASE_CMD[@]}" -DoutputAbsoluteArtifactFilename=true dependency:resolve-plugins -DoutputFile="$PLUGINS_LIST")

log_step "Resolving module dependency tree"
if ! run_cmd "${MVN_DEPS_CMD[@]}"; then
  echo "dependency:tree failed." >&2
  exit 1
fi
log_step "Resolving required Maven plugins"
if ! run_cmd "${MVN_PLUGINS_CMD[@]}"; then
  echo "dependency:resolve-plugins failed." >&2
  exit 1
fi
log_step "Dependency resolution completed"

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
    central-direct)
      printf '%s' "$MAVEN_CENTRAL_BASE_URL"
      ;;
    google-central-mirror)
      printf '%s' "$MAVEN_GOOGLE_CENTRAL_MIRROR_URL"
      ;;
    redhat-ga-repository)
      printf '%s' "$MAVEN_REDHAT_GA_REPO_URL"
      ;;
    jboss-public-repository-group)
      printf '%s' 'https://repository.jboss.org/nexus/content/groups/public/'
      ;;
    splunk-artifactory)
      printf '%s' "$MAVEN_SPLUNK_ARTIFACTORY_URL"
      ;;
    confluent)
      printf '%s' "$MAVEN_CONFLUENT_REPO_URL"
      ;;
    apache.snapshots)
      printf '%s' "$MAVEN_APACHE_SNAPSHOTS_URL"
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

m2_root="$(cd "$M2_ROOT" && pwd)"
SEEN_DIR="$WORK_TMP/.seen-rels"
mkdir -p "$SEEN_DIR"

declare -a ARTIFACT_FILES=()

collect_from_tree() {
  local listing="$1"
  [[ -f "$listing" ]] || return 0
  while IFS= read -r raw || [[ -n "$raw" ]]; do
    local line token
    line="$(printf '%s' "$raw" | sed -E $'s/\x1B\\[[0-9;]*[[:alpha:]]//g')"
    token="${line#${line%%[![:space:]\|\+\-\\]*}}"
    token="${token%% *}"
    [[ "$token" == *:* ]] || continue
    IFS=':' read -r -a parts <<<"$token"
    if [[ "${#parts[@]}" -ne 5 && "${#parts[@]}" -ne 6 ]]; then
      continue
    fi
    local group_id artifact_id version dir
    group_id="${parts[0]}"
    artifact_id="${parts[1]}"
    if [[ "${#parts[@]}" -eq 6 ]]; then
      version="${parts[4]}"
    else
      version="${parts[3]}"
    fi
    dir="$m2_root/${group_id//./\/}/$artifact_id/$version"
    [[ -d "$dir" ]] || continue
    shopt -s nullglob
    for f in "$dir"/*; do
      [[ -f "$f" ]] || continue
      should_skip_file "$f" && continue
      seen_has_rel "$f" && continue
      seen_mark_rel "$f"
      ARTIFACT_FILES+=("$f")
    done
    shopt -u nullglob
  done < "$listing"
}

collect_from_tree "$DEPS_TREE_LIST"
collect_from_listing() {
  local listing="$1"
  [[ -f "$listing" ]] || return 0
  while IFS= read -r raw || [[ -n "$raw" ]]; do
    local line
    line="$(printf '%s' "$raw" | sed -E $'s/\x1B\\[[0-9;]*[[:alpha:]]//g')"
    local candidate="${line##*:}"
    [[ "$candidate" == "$m2_root/"* && -f "$candidate" ]] || continue
    local dir
    dir="$(dirname "$candidate")"
    shopt -s nullglob
    for f in "$dir"/*; do
      [[ -f "$f" ]] || continue
      should_skip_file "$f" && continue
      seen_has_rel "$f" && continue
      seen_mark_rel "$f"
      ARTIFACT_FILES+=("$f")
    done
    shopt -u nullglob
  done < "$listing"
}
collect_from_listing "$PLUGINS_LIST"

add_coord_dir() {
  local group_id="$1"
  local artifact_id="$2"
  local version="$3"
  [[ -n "$group_id" && -n "$artifact_id" && -n "$version" ]] || return 0
  local dir="$m2_root/${group_id//./\/}/$artifact_id/$version"
  [[ -d "$dir" ]] || return 0
  shopt -s nullglob
  for f in "$dir"/*; do
    [[ -f "$f" ]] || continue
    should_skip_file "$f" && continue
    seen_has_rel "$f" && continue
    seen_mark_rel "$f"
    ARTIFACT_FILES+=("$f")
  done
  shopt -u nullglob
}

ensure_pom_in_local_repo() {
  local group_id="$1"
  local artifact_id="$2"
  local version="$3"
  local pom_path="$m2_root/${group_id//./\/}/$artifact_id/$version/${artifact_id}-${version}.pom"
  [[ -f "$pom_path" ]] && return 0
  run_cmd "$MVN_BIN" -q -s "$MAVEN_SETTINGS_PATH" -DskipTests \
    "dependency:get" "-Dartifact=${group_id}:${artifact_id}:${version}:pom" "-Dtransitive=false" >/dev/null
}

collect_import_boms_from_pom() {
  local pom_path="$1"
  [[ -f "$pom_path" ]] || return 0
  python3 - "$pom_path" <<'PY'
import sys
import xml.etree.ElementTree as ET

path = sys.argv[1]
ns = {"m": "http://maven.apache.org/POM/4.0.0"}
root = ET.parse(path).getroot()

props = {}
properties = root.find("m:properties", ns)
if properties is not None:
    for child in properties:
        key = child.tag.split("}", 1)[-1]
        props[key] = (child.text or "").strip()

def resolve_version(raw):
    if not raw:
        return ""
    value = raw.strip()
    seen = set()
    while value.startswith("${") and value.endswith("}"):
        key = value[2:-1]
        if key in seen or key not in props:
            break
        seen.add(key)
        value = props[key]
    return value

for dep in root.findall(".//m:dependencyManagement/m:dependencies/m:dependency", ns):
    dep_type = dep.findtext("m:type", default="jar", namespaces=ns).strip()
    dep_scope = dep.findtext("m:scope", default="", namespaces=ns).strip()
    if dep_type != "pom" or dep_scope != "import":
        continue
    g = dep.findtext("m:groupId", default="", namespaces=ns).strip()
    a = dep.findtext("m:artifactId", default="", namespaces=ns).strip()
    v = resolve_version(dep.findtext("m:version", default="", namespaces=ns))
    if g and a and v and "${" not in v:
        print(f"{g}:{a}:{v}")
PY
}

# Ensure model-resolution artifacts used by parent/import BOM chains are present
# in the lock file for offline container builds.
SPRING_BOOT_VERSION="$(resolve_root_property spring.boot.version || true)"
QUARKUS_PLATFORM_VERSION="$(resolve_root_property quarkus.platform.version || true)"
if [[ -n "$SPRING_BOOT_VERSION" ]]; then
  ensure_pom_in_local_repo "org.springframework.boot" "spring-boot-dependencies" "$SPRING_BOOT_VERSION"
fi
if [[ -n "$QUARKUS_PLATFORM_VERSION" ]]; then
  ensure_pom_in_local_repo "io.quarkus" "quarkus-bom" "$QUARKUS_PLATFORM_VERSION"
  ensure_pom_in_local_repo "io.quarkus.platform" "quarkus-maven-plugin" "$QUARKUS_PLATFORM_VERSION"
fi
add_coord_dir "org.springframework.boot" "spring-boot-dependencies" "$SPRING_BOOT_VERSION"
add_coord_dir "io.quarkus" "quarkus-bom" "$QUARKUS_PLATFORM_VERSION"
add_coord_dir "io.quarkus.platform" "quarkus-maven-plugin" "$QUARKUS_PLATFORM_VERSION"

SPRING_BOOT_POM_PATH="$m2_root/org/springframework/boot/spring-boot-dependencies/${SPRING_BOOT_VERSION}/spring-boot-dependencies-${SPRING_BOOT_VERSION}.pom"
QUARKUS_BOM_POM_PATH="$m2_root/io/quarkus/quarkus-bom/${QUARKUS_PLATFORM_VERSION}/quarkus-bom-${QUARKUS_PLATFORM_VERSION}.pom"
declare -a IMPORT_BOM_RELS=()

collect_and_add_import_boms() {
  local pom_path="$1"
  local coords
  coords="$(collect_import_boms_from_pom "$pom_path" || true)"
  [[ -n "$coords" ]] || return 0
  while IFS=':' read -r g a v; do
    [[ -n "$g" && -n "$a" && -n "$v" ]] || continue
    local group_path
    group_path="$(printf '%s' "$g" | tr '.' '/')"
    ensure_pom_in_local_repo "$g" "$a" "$v"
    add_coord_dir "$g" "$a" "$v"
    IMPORT_BOM_RELS+=("${group_path}/$a/$v/${a}-${v}.pom")
  done <<<"$coords"
}

collect_and_add_import_boms "$SPRING_BOOT_POM_PATH"
collect_and_add_import_boms "$QUARKUS_BOM_POM_PATH"

SPRING_BOOT_BOM_REL="org/springframework/boot/spring-boot-dependencies/${SPRING_BOOT_VERSION}/spring-boot-dependencies-${SPRING_BOOT_VERSION}.pom"
QUARKUS_BOM_REL="io/quarkus/quarkus-bom/${QUARKUS_PLATFORM_VERSION}/quarkus-bom-${QUARKUS_PLATFORM_VERSION}.pom"
QUARKUS_PLUGIN_POM_REL="io/quarkus/platform/quarkus-maven-plugin/${QUARKUS_PLATFORM_VERSION}/quarkus-maven-plugin-${QUARKUS_PLATFORM_VERSION}.pom"
QUARKUS_PLUGIN_JAR_REL="io/quarkus/platform/quarkus-maven-plugin/${QUARKUS_PLATFORM_VERSION}/quarkus-maven-plugin-${QUARKUS_PLATFORM_VERSION}.jar"

declare -a ROW_RELS=()
declare -a ROW_URLS=()
declare -a ROW_HASHES=()

log_step "Preparing lock entries from resolved artifacts in ${m2_root}"
for f in "${ARTIFACT_FILES[@]}"; do
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
done

if [[ "${#ROW_RELS[@]}" -eq 0 ]]; then
  echo "No resolved artifacts found for lock generation" >&2
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
  local local_file="$m2_root/$rel"
  if [[ ! -f "$local_file" ]]; then
    log_step "Skipping extra artifact ${rel} (not found in local Maven repository)"
    return 0
  fi
  log_step "Adding extra artifact ${rel}"
  local ck="sha256:$(sha256_file "$local_file")"
  printf '  - download_url: "%s"\n' "$(yaml_escape "$url")"
  printf '    checksum: "%s"\n' "$(yaml_escape "$ck")"
  printf '    filename: "%s"\n' "$(yaml_escape "$rel")"
  seen_mark_rel "$rel"
  count=$((count + 1))
}

mkdir -p "$(dirname "$OUT_PATH")"
log_step "Writing lock file to ${OUT_PATH}"
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
  # Keep model-resolution BOMs/extensions available for offline Maven builds.
  [[ -n "$SPRING_BOOT_VERSION" ]] && add_extra "$SPRING_BOOT_BOM_REL" "${MAVEN_CENTRAL_BASE_URL}${SPRING_BOOT_BOM_REL}"
  [[ -n "$QUARKUS_PLATFORM_VERSION" ]] && add_extra "$QUARKUS_BOM_REL" "${MAVEN_CENTRAL_BASE_URL}${QUARKUS_BOM_REL}"
  [[ -n "$QUARKUS_PLATFORM_VERSION" ]] && add_extra "$QUARKUS_PLUGIN_POM_REL" "${MAVEN_CENTRAL_BASE_URL}${QUARKUS_PLUGIN_POM_REL}"
  [[ -n "$QUARKUS_PLATFORM_VERSION" ]] && add_extra "$QUARKUS_PLUGIN_JAR_REL" "${MAVEN_CENTRAL_BASE_URL}${QUARKUS_PLUGIN_JAR_REL}"
  for rel in "${IMPORT_BOM_RELS[@]}"; do
    local_file="$m2_root/$rel"
    [[ -f "$local_file" ]] || continue
    rid="$(repo_id_for_artifact "$local_file")"
    [[ -n "$rid" ]] || continue
    base="$(repo_base_for "$rid" || true)"
    [[ -n "$base" ]] || continue
    [[ "$base" == */ ]] || base="${base}/"
    add_extra "$rel" "${base}${rel}"
  done
} >"$OUT_PATH"

echo "Wrote ${count} artifacts to ${OUT_PATH}"