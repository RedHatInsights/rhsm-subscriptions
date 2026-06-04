# Guide for "swatch-producer-aws" Component Tests

## Running Tests Locally

### 1. Start Local Dependencies

Start the required local services using Docker Compose:

```bash
podman compose up -d kafka kafka-bridge kafka-setup wiremock
```

### 2. Run Component Tests

Execute the component tests for `swatch-producer-aws`:

```bash
./mvnw clean install -Pcomponent-tests -pl swatch-producer-aws/ct -am
```

Alternatively, you can use the Makefile shorthand which also builds the service before running:

```bash
make build component-test swatch-producer-aws
```

## Running Tests Against OpenShift (Bonfire)

### 1. Deploy Dependencies in OpenShift

Deploy only the necessary dependencies for `swatch-producer-aws`:

```bash
bonfire deploy rhsm --source=appsre --ref-env insights-stage --component rhsm --component swatch-kafka-bridge --component wiremock --component swatch-producer-aws --no-remove-resources app:rhsm --remove-dependencies swatch-producer-aws
```

### 2. Run Component Tests Against OpenShift

Execute tests for `swatch-producer-aws` against the OpenShift environment:

```bash
./mvnw clean install -Pcomponent-tests -pl swatch-producer-aws/ct -am -Dswatch.component-tests.global.target=openshift
```

The key difference is the addition of the `-Dswatch.component-tests.global.target=openshift` parameter,
which tells the test framework to target the OpenShift deployment instead of local containers.
