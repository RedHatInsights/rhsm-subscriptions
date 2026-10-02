#!/bin/sh
# Offline regression checks for the temporary SWATCH-5598 bootstrap.
set -eu

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
test_dir=$(mktemp -d "${TMPDIR:-/tmp}/swatch-vendor-test.XXXXXX")
test_dir=$(CDPATH= cd -- "$test_dir" && pwd)
trap 'rm -rf -- "$test_dir"' EXIT
fixture="$test_dir/repo with spaces"
mkdir -p "$fixture/bin" "$fixture/third-party"
cp "$repo_root/bin/install-vendored-clowder.sh" "$fixture/bin/"
cp -R "$repo_root/third-party/clowder" "$fixture/third-party/"
touch "$fixture/pom.xml"
export VENDOR_TEST_LOG="$test_dir/maven.log"
printf '%s\n' '#!/bin/sh' \
  'printf "%s\n" "$PWD" "$@" > "$VENDOR_TEST_LOG"' \
  'exit "${VENDOR_TEST_EXIT:-0}"' > "$fixture/mvnw"
chmod +x "$fixture/mvnw"
unset MAVEN_CMD

fail() {
  printf 'FAIL: %s\n' "$*" >&2
  exit 1
}

assert_arg() {
  grep -Fxq -- "$1" "$VENDOR_TEST_LOG" || fail "Missing Maven argument: $1"
}

cd "$test_dir"
sh "$fixture/bin/install-vendored-clowder.sh" --help >/dev/null
[ ! -e "$VENDOR_TEST_LOG" ] || fail 'Help invoked Maven'
sh "$fixture/bin/install-vendored-clowder.sh" --verify-only >/dev/null
[ ! -e "$VENDOR_TEST_LOG" ] || fail 'Verification invoked Maven'
printf '%s\n' 'PASS: help and verification do not invoke Maven'

sh "$fixture/bin/install-vendored-clowder.sh" \
  -s 'settings with spaces.xml' '-Dmaven.repo.local=cache with spaces' >/dev/null
assert_arg "$fixture"
assert_arg --non-recursive
assert_arg --file
assert_arg "$fixture/pom.xml"
assert_arg -s
assert_arg 'settings with spaces.xml'
assert_arg '-Dmaven.repo.local=cache with spaces'
assert_arg org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file
assert_arg "-Dfile=$fixture/third-party/clowder/clowder-quarkus-config-source-2.12.0-SWATCH-5598-SNAPSHOT.jar"
assert_arg "-DpomFile=$fixture/third-party/clowder/clowder-quarkus-config-source.pom"
assert_arg -DgeneratePom=false
printf '%s\n' 'PASS: wrapper, working directory, artifact metadata, and option forwarding'

mv "$fixture/mvnw" "$test_dir/custom maven"
MAVEN_CMD="$test_dir/custom maven" sh "$fixture/bin/install-vendored-clowder.sh" >/dev/null
assert_arg -DgeneratePom=false
status=0
MAVEN_CMD="$test_dir/custom maven" VENDOR_TEST_EXIT=42 \
  sh "$fixture/bin/install-vendored-clowder.sh" >/dev/null || status=$?
[ "$status" -eq 42 ] || fail 'Maven failure status was not propagated'
printf '%s\n' 'PASS: Maven executable override and failure propagation'

for artifact in clowder-quarkus-config-source-2.12.0-SWATCH-5598-SNAPSHOT.jar \
  clowder-quarkus-config-source.pom LICENSE; do
  rm -f "$VENDOR_TEST_LOG"
  printf '\nmodified\n' >> "$fixture/third-party/clowder/$artifact"
  if MAVEN_CMD="$test_dir/custom maven" sh "$fixture/bin/install-vendored-clowder.sh" >/dev/null 2>&1; then
    fail "Accepted modified $artifact"
  fi
  [ ! -e "$VENDOR_TEST_LOG" ] || fail "Invoked Maven for modified $artifact"
  cp "$repo_root/third-party/clowder/$artifact" "$fixture/third-party/clowder/$artifact"
done
printf '%s\n' 'PASS: changed JAR, POM, and license are rejected before Maven runs'

mv "$fixture/third-party/clowder/clowder-quarkus-config-source.pom" "$test_dir/missing.pom"
if MAVEN_CMD="$test_dir/custom maven" sh "$fixture/bin/install-vendored-clowder.sh" >/dev/null 2>&1; then
  fail 'Accepted an incomplete bundle'
fi
[ ! -e "$VENDOR_TEST_LOG" ] || fail 'Invoked Maven for an incomplete bundle'
printf '%s\n' 'PASS: incomplete bundle is rejected before Maven runs'
