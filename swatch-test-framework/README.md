## SWATCH Test Framework – Running Component Tests

This repository includes a helper script to make running component tests simple and repeatable.

### Script

- Path: `bin/run-component-test.sh`
- Purpose: Builds the local test framework, ensures a local compose stack is up (Podman or Docker), then runs a specified component test.

What it does:
- Builds `swatch-test-framework` (and any required modules) locally.
- Detects your container engine and compose command (supports Podman and Docker).
- Starts the compose stack in detached mode if it is not already running.
- Runs the target test inside the requested module.

### Requirements
- Java 17+ and Maven Wrapper (`./mvnw`)
- One of: Podman or Docker
- One of: `podman compose`, `podman-compose`, `docker compose`, or `docker-compose`

### Usage

Run the default Azure component test:
```bash
bin/run-component-test.sh
```

Specify a module and a test class FQN:
```bash
bin/run-component-test.sh swatch-producer-azure \
  com.redhat.swatch.component.tests.SwatchAzureProducerIT
```

The script will:
1) Build `:swatch-test-framework` (with `-am`) and install it locally
2) Bring up the compose stack if not already running
3) Run the specified test via Maven in the chosen module

### Service Ports for Local Quarkus Dev
The framework can auto-assign ports, or you can pin them by adding a test `application.properties` under your service module, for example:
```
<service-module>/src/test/resources/application.properties
```
```properties
SERVER_PORT=8000
quarkus.management.port=9000
```

### Notes
- The script uses a simple heuristic to detect whether the compose stack is up (looks for common container names like kafka/zookeeper/postgres/wiremock). If your stack uses different names, start it manually before running the script.
- You can still run tests manually if you prefer:
```bash
./mvnw -pl :swatch-test-framework -am -DskipTests install
./mvnw -pl :swatch-producer-azure -am \
  -Dtest=com.redhat.swatch.component.tests.SwatchAzureProducerIT \
  -Dsurefire.failIfNoSpecifiedTests=false \
  test
```

### Troubleshooting
- Compose not found: ensure Podman or Docker and their compose plugin/CLI are installed.
- Port parsing errors: ensure `SERVER_PORT` and `quarkus.management.port` are numbers (set via `application.properties` or `-D` system properties).
- If you change framework code, the script will rebuild and reinstall it before running tests.


