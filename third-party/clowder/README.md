# Temporary Clowder library bundle — SWATCH-5598

This directory vendors the multicluster/V2 dependency endpoint implementation while its upstream
release is pending. It is a patched SWATCH development dependency, **not an official upstream release**.
Consumers use normal Maven dependency resolution; no `system` scope or bundled transitive classes
are introduced.

## Provenance

- Coordinates: `com.redhat.cloud.common:clowder-quarkus-config-source:2.12.0-SWATCH-5598-SNAPSHOT`
- Source: [lindseyburnett/clowder-quarkus-config-source](https://github.com/lindseyburnett/clowder-quarkus-config-source)
- Exact source commit: [`dee9839c7ab615664f85030eeb06c9a75ceb93b6`](https://github.com/lindseyburnett/clowder-quarkus-config-source/commit/dee9839c7ab615664f85030eeb06c9a75ceb93b6)
- Upstream base: `877107b` (version 2.12.0).
- Changes: public/private V2 endpoint discovery, V1 fallback, per-endpoint trust, and tests.
- Built with Java 17; all 88 library tests passed. SWATCH itself uses Java 25.
- License: Apache License 2.0, reproduced in [LICENSE](LICENSE). No separate upstream NOTICE file
  was present at the recorded source commit.

The JAR is the normal library artifact, **not** the `-app.jar` assembly containing dependencies.
The POM is the build-generated `.flattened-pom.xml`, renamed for clarity. It preserves the library's
dependency metadata and resolves the `${revision}` coordinate. `SHA256SUMS` pins the exact JAR,
POM, and license bundled here; it is an integrity check, not an independent signature.

## Build SWATCH without a library checkout

From the SWATCH repository root with Java 25:

```sh
sh bin/install-vendored-clowder.sh
./mvnw compile -DskipTests
```

The first command verifies the bundle and installs it into the selected local Maven repository.
It does not publish anything. Repeat it after clearing your Maven cache or when this bundle changes.
No library source checkout or Java 17 installation is needed just to build SWATCH.

Use `MAVEN_CMD=mvn` to select an installed Maven instead of the wrapper. Maven options are forwarded;
when using custom settings or a custom cache, supply the same options to both commands:

```sh
sh bin/install-vendored-clowder.sh -s .mvn/maven-settings.xml -Dmaven.repo.local=/tmp/swatch-m2
./mvnw -s .mvn/maven-settings.xml -Dmaven.repo.local=/tmp/swatch-m2 compile -DskipTests
```

Settings/cache paths are resolved from the SWATCH root. Regular dependencies and the Maven install
plugin still need repository access on a cold cache; this is not an offline bundle of all dependencies.
`sh bin/install-vendored-clowder.sh --verify-only` checks the bundled files without running Maven.
`sh bin/test-install-vendored-clowder.sh` runs offline bootstrap regression checks, including
modified/missing artifacts, paths with spaces, Maven option forwarding, and failure propagation.

The Makefile build/test/install paths, GitHub Maven jobs, service Dockerfiles, and Tekton component
test build invoke this bootstrap before their Maven builds. Restored caches are not assumed to
contain the snapshot. Keep the bootstrap as a separate Maven invocation: installing during a
project lifecycle can occur too late for dependency resolution.

## Local verification — 2026-09-24

- Installed the JAR and flattened POM into an initially empty Maven repository and verified they
  matched the bundled bytes. Other Maven dependencies were supplied by a read-only mirror of the
  existing developer cache; the Clowder snapshot was installed by this bootstrap.
- Compiled the full 55-module SWATCH reactor on Java 25 using that repository.
- Packaged Contracts and Utilization with container image building/pushing disabled; both packaged
  applications contain the exact bundled Clowder JAR.
- Passed `HccEndpointConfigurationTest` against the bundled artifact, plus the offline bootstrap
  regression checks. GitHub/Tekton pipelines and container image builds have not been run here.

## Rebuild or update the bundle

Use a clean library checkout at the exact source commit above with Java 17, then run:

```sh
mvn clean package -Drevision=2.12.0-SWATCH-5598-SNAPSHOT
```

Copy `target/clowder-quarkus-config-source-2.12.0-SWATCH-5598-SNAPSHOT.jar` into this directory,
copy `.flattened-pom.xml` as `clowder-quarkus-config-source.pom`, and retain the source `LICENSE`.
Do not substitute the raw `${revision}` POM or the `-app.jar` assembly. Record any new source commit
and regenerate the checksums after reviewing and testing the rebuilt files:

```sh
shasum -a 256 clowder-quarkus-config-source-2.12.0-SWATCH-5598-SNAPSHOT.jar \
  clowder-quarkus-config-source.pom LICENSE > SHA256SUMS
```

Run that checksum command from this directory; Linux can use `sha256sum` instead. JAR ZIP timestamps
can change between builds, so a rebuild is not necessarily byte-for-byte identical. Commit the JAR,
POM, checksum file, license, and provenance together. Never overwrite the official `2.12.0` coordinate.

## Remove after upstream release

1. Change `clowder-quarkus-config-source.version` in `swatch-quarkus-parent/pom.xml` to the agreed
   published version and verify it contains the V2 behavior SWATCH uses.
2. Remove this directory, `bin/install-vendored-clowder.sh`, its tests, and its calls from the
   Makefile, GitHub workflow, Dockerfiles (including vendored license copies), and Tekton component tests.
3. Remove temporary bootstrap instructions from the root README and update `docs/SWATCH-5598.md`.
4. Build and run the multicluster regression suite with a cache that does not contain the patched
   snapshot to verify the normal upstream dependency path.
