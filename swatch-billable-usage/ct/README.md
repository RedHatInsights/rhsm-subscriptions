# Guide for "swatch-billable-usage" Component Tests

## Running Tests Locally

### 1. Start Database

Start the required containers:

```bash
podman compose up -d kafka kafka-bridge kafka-setup wiremock db
```

### 2. Run Component Tests

Execute tests for a specific service. For example, to run tests for `swatch-billable-usage`:

```bash
 ./mvnw clean install -Pcomponent-tests -pl swatch-billable-usage/ct -am
```

## Running Tests Against OpenShift (Bonfire)

### 1. Deploy Dependencies in OpenShift

Deploy only the necessary dependencies for a specific service:

```bash
bonfire deploy rhsm --no-remove-resources app:rhsm --source=appsre --ref-env insights-stage --component swatch-kafka-bridge --component swatch-database --component wiremock --component artemis --component swatch-billable-usage
```

### 2. Run Component Tests Against OpenShift

Execute tests for a specific service against the OpenShift environment. For example, to run tests for `swatch-billable-usage`:

```bash
 ./mvnw clean install -Pcomponent-tests -pl swatch-billable-usage/ct -am -Dswatch.component-tests.global.target=openshift
```

The key difference is the addition of the `-Dswatch.component-tests.global.target=openshift` parameter, which tells the test framework to target the OpenShift deployment instead of local containers.