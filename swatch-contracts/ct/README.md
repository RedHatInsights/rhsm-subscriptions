# Guide for "swatch-contracts" Component Tests

## Running Tests Locally

### 1. Start Local Dependencies

Start the required local services using Docker Compose:

```bash
podman compose up -d kafka kafka-bridge kafka-setup amqp wiremock db
```

This will start all necessary dependencies (databases, Kafka, etc.) required for the component tests.

### 2. Run Component Tests

Execute tests for a specific service. For example, to run tests for `swatch-contracts`:

```bash
./mvnw clean install -Pcomponent-tests -pl swatch-contracts/ct -am
```

## Running Tests Against OpenShift (Bonfire)

### 1. Deploy Dependencies in OpenShift

Deploy only the necessary dependencies for a specific service:

```bash
bonfire deploy rhsm --source=appsre --ref-env insights-stage --component rhsm --component swatch-kafka-bridge --component swatch-database --component wiremock --component artemis --component swatch-contracts --no-remove-resources app:rhsm
```

### 2. Run Component Tests Against OpenShift

Execute tests for a specific service against the OpenShift environment. For example, to run tests for `swatch-contracts`:

```bash
./mvnw clean install -Pcomponent-tests -pl swatch-contracts/ct -am -Dswatch.component-tests.global.target=openshift
```

The key difference is the addition of the `-Dswatch.component-tests.global.target=openshift` parameter, which tells the test framework to target the OpenShift deployment instead of local containers.
