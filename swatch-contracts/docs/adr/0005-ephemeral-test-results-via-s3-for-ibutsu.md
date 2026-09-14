### 0005 - Publish Ephemeral Bonfire Test Results to S3 for Ibutsu Import

* **Status:** Accepted
* **Deciders:** Subscription Watch team
* **Date:** 2026-09-16

---

### Context

The **component tests** and **IQE tests** pipelines run on the **external Konflux** cluster against ephemeral namespaces. That environment cannot reach the Ibutsu API directly the way internal CI used to.

The **Report Portal** and **Data Router** services are being decommissioned, so we cannot keep using them for test reporting (see the superseded proposal in [0002](0002-report-portal-for-new-component-tests.md)).

We still need QE-visible runs in **Ibutsu** (searchable by source, git revision, pipeline, and related metadata). Ephemeral namespaces provision a **test-results S3** bucket (credentials via `ibutsu-aws-credentials`). Ibutsu **imports** objects from that bucket when files follow its expected shape and metadata.

---

### Decision

We will upload Bonfire test outputs from Konflux pipelines to the ephemeral **test-results S3** bucket so Ibutsu can import them.

| Test type | Artifact | Metadata |
|-----------|----------|----------|
| **Component tests** | Single merged JUnit XML (`merged-results.xml`) produced by the component test framework | Run properties on the root `<testsuites>` element (component, git revision, pipeline name, event type, integration test name, env, tool, source) via Maven `-Dswatch.ct.metadata.*` |
| **Integration (IQE)** | pytest-ibutsu **archive** (`{uuid}.tar.gz`) collected from the namespace minio bucket after the CJI | `IBUTSU_MODE`, `IBUTSU_DATA` (space-separated `key=value` pairs), and `IBUTSU_SOURCE` on the IQE pod via `IQE_ENV_VARS` |

---

### Consequences

* **Positive:** Ibutsu reporting works from external Konflux without direct cluster-to-Ibutsu connectivity; metadata aligns with component-test JUnit conventions where applicable.
* **Negative:** Depends on ephemeral S3 provisioning, Ibutsu import configuration, and pipeline maintenance (collect timing, upload task, merged report generation on test failure).
* **0002:** Superseded; Report Portal and Data Router are not a viable path because those services are being shut down.
