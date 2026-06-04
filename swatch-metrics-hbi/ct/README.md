# Guide for "swatch-metrics-hbi" Component Tests

## Running Tests Locally

### 1. Start Local Dependencies

Start the required local services using Docker Compose:

```bash
podman compose up -d kafka kafka-bridge kafka-setup unleash db
```

This will start all necessary dependencies (databases, Kafka, etc.) required for the component tests.

### 2. Run Component Tests

Execute tests for a specific service. For example, to run tests for `swatch-metrics-hbi`:

```bash
./mvnw clean install -Pcomponent-tests -pl swatch-metrics-hbi/ct -am
```

## Running Tests Against OpenShift (Bonfire)

### 1. Deploy Dependencies in OpenShift

Deploy only the necessary dependencies for a specific service. For example, for `swatch-metrics-hbi` (which only requires wiremock and kafka bridge, as Kafka comes by default):

```bash
bonfire deploy rhsm --source=appsre --ref-env insights-stage --component swatch-metrics-hbi --component swatch-kafka-bridge --component rhsm --component wiremock --no-remove-resources app:rhsm
```

### 2. Run Component Tests Against OpenShift

Execute tests for a specific service against the OpenShift environment. For example, to run tests for `swatch-metrics-hbi`:

```bash
./mvnw clean install -Pcomponent-tests -pl swatch-metrics-hbi/ct -am -Dswatch.component-tests.global.target=openshift
```

The key difference is the addition of the `-Dswatch.component-tests.global.target=openshift` parameter, which tells the test framework to target the OpenShift deployment instead of local containers.