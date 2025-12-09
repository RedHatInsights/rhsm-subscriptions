# Guide for "swatch-system-conduit" Component Tests

## Running Tests Locally

### 1. Start Local Dependencies

Start the required local services using Docker Compose:

```bash
podman compose up -d kafka kafka-bridge kafka-setup amqp wiremock db
```

This will start all necessary dependencies (databases, Kafka, etc.) required for the component tests.

### 2. Run the database migrations

```bash
make run-migrations
```

### 3. Run Component Tests

Execute tests for a specific service. For example, to run tests for `swatch-system-conduit`:

```bash
./mvnw clean install -Pcomponent-tests -pl swatch-system-conduit/ct
```

**Note**: The component tests require:
- `RHSM_USE_STUB=true` to use the stub RHSM API instead of the real API
- `INVENTORY_ENABLE_KAFKA=true` to enable Kafka-based inventory updates (required for reading from `platform.inventory.host-ingress` topic)
- `KAFKA_SEEK_OVERRIDE_END=true` to skip old messages in the Kafka topic (prevents processing messages from previous test runs)

## Running Tests Against OpenShift (Bonfire)

### 1. Deploy Dependencies in OpenShift

Deploy only the necessary dependencies for a specific service:

```bash
bonfire deploy rhsm --source=appsre --ref-env insights-stage --component swatch-kafka-bridge --component swatch-database --component wiremock --component artemis --component swatch-system-conduit
```

### 2. Run Component Tests Against OpenShift

Execute tests for a specific service against the OpenShift environment. For example, to run tests for `swatch-system-conduit`:

```bash
./mvnw clean install -Pcomponent-tests -pl swatch-system-conduit/ct -am -Dswatch.component-tests.global.target=openshift
```
