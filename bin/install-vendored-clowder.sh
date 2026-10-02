#!/bin/sh
# Temporary SWATCH-5598 bootstrap. Remove after adopting the upstream library release.
set -eu

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
vendor_dir="$repo_root/third-party/clowder"
artifact=clowder-quarkus-config-source-2.12.0-SWATCH-5598-SNAPSHOT.jar

case "${1:-}" in
  --help|-h)
    printf '%s\n' 'Usage: sh bin/install-vendored-clowder.sh [Maven options]' \
      '       sh bin/install-vendored-clowder.sh --verify-only' \
      'Defaults to the repository Maven wrapper. Set MAVEN_CMD to use another Maven executable.' \
      'Pass the same settings and maven.repo.local options used by the subsequent build.'
    exit 0
    ;;
esac

# Verify before invoking Maven, even if a previous copy is already installed in its cache.
(
  cd "$vendor_dir"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum -c SHA256SUMS
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 -c SHA256SUMS
  else
    printf '%s\n' 'A SHA-256 utility (sha256sum or shasum) is required.' >&2
    exit 1
  fi
)

if [ "${1:-}" = '--verify-only' ]; then
  exit 0
fi

cd "$repo_root"
maven_cmd=${MAVEN_CMD:-"$repo_root/mvnw"}
# Run as a separate invocation, before Maven resolves dependencies for the main build.
# Supplying the flattened POM preserves transitive dependencies and the resolved snapshot version.
exec "$maven_cmd" --batch-mode --no-transfer-progress --non-recursive \
  --file "$repo_root/pom.xml" "$@" \
  org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file \
  "-Dfile=$vendor_dir/$artifact" \
  "-DpomFile=$vendor_dir/clowder-quarkus-config-source.pom" \
  -DgeneratePom=false
