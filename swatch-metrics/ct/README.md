# Guide for "swatch-metrics" Component Tests

## Running Tests Locally

### 1. Start Dependencies

Start the required containers:

```bash
podman compose up -d kafka kafka-bridge kafka-setup wiremock prometheus
```

### 2. Run Component Tests

Execute tests for swatch-metrics:

```bash
./mvnw clean install -Pcomponent-tests -pl swatch-metrics/ct -am
```

## Running Tests Against OpenShift (Bonfire)

### 1. Deploy Dependencies in OpenShift

Deploy only the necessary dependencies for swatch-metrics:

```bash
bonfire deploy rhsm --no-remove-resources app:rhsm --source=appsre --ref-env insights-stage --component rhsm --component swatch-kafka-bridge --component wiremock --component prometheus --component swatch-metrics --remove-dependencies swatch-metrics
```

You can use a custom image of the service by adding the following parameters to the previous command: `-p swatch-metrics/IMAGE=quay.io/yourname/swatch-metrics -p swatch-metrics/IMAGE_TAG=yourtag`.

### 2. Run Component Tests Against OpenShift

Execute tests for swatch-metrics against the OpenShift environment:

```bash
./mvnw clean install -Pcomponent-tests -pl swatch-metrics/ct -am -Dswatch.component-tests.global.target=openshift
```

The key difference is the addition of the `-Dswatch.component-tests.global.target=openshift` parameter, which tells the test framework to target the OpenShift deployment instead of local containers.
