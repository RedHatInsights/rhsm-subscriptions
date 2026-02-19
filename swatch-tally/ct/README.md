# Guide for "swatch-tally" Component Tests

## Nightly Tally Tests (temporary DB seeding)

Nightly tally CTs currently seed host-buckets **directly into the DB** (CT-only helper in `swatch-tally/ct/java/utils/`). This is intentionally isolated so it can be removed later.

DB connection can be overridden via env vars: `SWATCH_CT_DB_HOST`, `SWATCH_CT_DB_PORT`, `SWATCH_CT_DB_NAME`, `SWATCH_CT_DB_USER`, `SWATCH_CT_DB_PASSWORD`, `SWATCH_CT_DB_SSLMODE`.

## Running Tests Locally

### 1. Start Local Dependencies

Start the required local services using Docker Compose:

```bash
podman compose up -d kafka kafka-bridge kafka-setup amqp wiremock db
```

### 2. Run Component Tests

Execute tests for a specific service. For example, to run tests for `swatch-tally`:

```bash
./mvnw clean install -Pcomponent-tests -pl swatch-tally/ct -am
```

## Running Tests Against OpenShift (Bonfire)

### 1. Deploy Dependencies in OpenShift

Deploy only the necessary dependencies for a specific service:

```bash
bonfire deploy rhsm --source=appsre --ref-env insights-stage --component swatch-kafka-bridge --component swatch-database --component wiremock --component artemis --component swatch-tally
```

### 2. Run Component Tests Against OpenShift

Execute tests for a specific service against the OpenShift environment. For example, to run tests for `swatch-tally`:

```bash
./mvnw clean install -Pcomponent-tests -pl swatch-tally/ct -am -Dswatch.component-tests.global.target=openshift
```

The key difference is the addition of the `-Dswatch.component-tests.global.target=openshift` parameter, which tells the test framework to target the OpenShift deployment instead of local containers.