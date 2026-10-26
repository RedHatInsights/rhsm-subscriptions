# Guide for "swatch-system-conduit" Component Tests

## Running Tests Locally

### 1. Start Local Dependencies

Start the required local services using Docker Compose:

```bash
podman compose up -d kafka kafka-bridge kafka-setup wiremock db
```

This starts the local PostgreSQL provider, Kafka, and Wiremock used by the component tests.

### 2. Run Component Tests

Execute tests for a specific service. For example, to run tests for `swatch-system-conduit`:

```bash
./mvnw clean install -Pcomponent-tests -pl swatch-system-conduit/ct -am
```

## Running Tests Against OpenShift (Bonfire)

### 1. Deploy Dependencies in OpenShift

Deploy only the necessary dependencies for a specific service:

```bash
bonfire deploy rhsm --source=appsre --ref-env insights-stage --component rhsm --component swatch-kafka-bridge --component swatch-database --component wiremock --component swatch-system-conduit --remove-dependencies swatch-system-conduit/swatch-tally --no-remove-resources app:rhsm
```

### 2. Run Component Tests Against OpenShift

Execute tests for a specific service against the OpenShift environment. For example, to run tests for `swatch-system-conduit`:

```bash
./mvnw clean install -Pcomponent-tests -pl swatch-system-conduit/ct -am -Dswatch.component-tests.global.target=openshift
```
