# SWATCH-5598 — Multicluster Architecture Readiness and Migration

## Epic context

Prepare SWATCH services for the multicluster HCC architecture by reviewing, completing where
necessary, validating, and deploying the HCC-provided changes. SWATCH integrations with RBAC,
Kessel, and Export Service must support cross-cluster service discovery, TLS, and
service-to-service authentication before HCC switches their traffic to dedicated API instances.

- **Epic:** SWATCH-5598
- **Status:** Stories 1 and 3–6 implemented and locally tested. Story 2 is implemented against the local library snapshot; upstream release remains open. Stories 7–8 require non-production validation, deployment, and HCC coordination.

### Implementation checkpoint — 2026-09-24

Checked acceptance criteria below mean implemented and verified locally, **not deployed or
validated against live HCC**. The baseline audit descriptions explain why each change was needed.

- Spring now resolves public/private V2 endpoint URI, authentication, and per-endpoint CA, with
  whole-endpoint V1 fallback. V2 without a CA uses system trust. Supplied invalid CAs fail closed.
- `HccCredentials` in `swatch-common-kessel` is the shared SDK-backed token cache for Spring and
  Quarkus HTTP/gRPC clients. Credentials are acquired lazily and renewed by the SDK.
- Spring and Quarkus RBAC permission calls preserve identity; their separate SDK workspace clients
  preserve organization context and use RBAC endpoint trust. A blank deployment workspace override
  falls back to the discovered RBAC endpoint.
- Both Export clients select Bearer versus PSK from the endpoint's authentication requirement,
  including upload and error callbacks. Bearer mode does not require or send the Export PSK.
- Kessel now sends per-call bearer credentials over verified TLS, refreshes rejected tokens before
  retry, and reattaches authentication after channel recreation. Required authentication with
  `KESSEL_INSECURE=true` fails closed. Set `KESSEL_AUTH_ENABLED=true`, `KESSEL_INSECURE=false`, and
  the HCC-provided `KESSEL_INVENTORY_API_PORT` together for secure cutover; the default port remains 9000.
- All four deployment templates wire the port, workspace override, authentication flag, issuer,
  and existing service-account secret references. Tally and Contracts make the Export PSK reference
  optional, but their legacy clients still require a nonblank PSK.
- **140 focused tests passed** on Java 25, covering actual HTTP clients, TLS gRPC transport,
  rejected-token refresh, channel recreation, SDK caching/renewal, authorization denial,
  configuration files, deployment YAML, V1/V2 selection, endpoint CA isolation, and legacy behavior.
- **JVM packaging passed** for `swatch-tally` (the shared Spring API/Tally application),
  `swatch-contracts`, and `swatch-utilization`, with container image building/pushing disabled.
  The packaging run also rechecked Spring property binding, including empty truststore handling.

The library dependency is still `2.12.0-SWATCH-5598-SNAPSHOT`. It is not a published release;
the temporary [vendored bundle](../third-party/clowder/README.md) includes the normal JAR, flattened
POM, checksums, license, and exact source commit. Developers can install it with
`sh bin/install-vendored-clowder.sh`; Makefile, CI, and container build paths install it automatically.
No separate library checkout is required. Replace the bundle with the reviewed upstream release
when available; vendoring does not satisfy the upstream publication or live-validation gates.
The library's separate 88-test Java 17 verification remains applicable (no further library changes
were needed in this implementation pass).
- **Readiness deadline:** End of Q4 2026.
- **Owner:** To be assigned.
- **Affected deployments:** `swatch-api`, `swatch-tally`, `swatch-contracts`, and
  `swatch-utilization`.
- **Accepted baseline:** Use the HCC contract from the Host Inventory reference PR.
- **Scope exclusion:** No Sources client or Sources deployment dependency was found in the audit.
- **HCC ownership:** HCC provides enablement PRs and performs the configuration-only traffic
  cutovers. SWATCH reviews, fills any implementation gaps, validates, deploys, and coordinates timing.
  Designing and operating the dedicated HCC API clusters remains outside this epic.

References:

- [Multicluster HCC architecture](https://docs.google.com/document/d/1VieGlokfGi5_pLfR54cHz_7U_s4cG-oDbFArIOHaL74/edit?tab=t.0)
- [Host Inventory reference PR](https://github.com/RedHatInsights/insights-host-inventory/pull/4897)
- [Clowder V2 dependency endpoints](https://github.com/RedHatInsights/clowder/blob/master/ARCHITECTURE.md#v2-dependency-endpoints)

## How the audit maps to stories

- **Finding 1 — V2 endpoint configuration:** Stories 1 and 2, split by runtime.
- **Finding 2 — RBAC authentication:** Stories 3 and 4.
- **Finding 3 — Export Service discovery and authentication:** Stories 3 and 5.
- **Finding 4 — Kessel endpoint handling:** Story 6, using shared authentication from Story 3.
- **Finding 5 — Missing regression coverage:** Automated tests belong in Stories 1–6;
  environment-level validation belongs in Story 7.
- **Deployment, migration, monitoring, and scheduling:** Story 8.

The story descriptions incorporate the subsequent code review. In particular, Kessel gRPC
authentication and the separate RBAC workspace client are additions to the pasted audit.
Spring already supports reading nested JSON values; its missing capability is the integrated
V2 endpoint selection, fallback, trust configuration, and client wiring.

Story numbers below are planning labels, not newly created Jira issue keys. Review incoming HCC
PRs against these scopes and record which work they already deliver. Each implementation story
owns its relevant configuration changes and automated tests.

## Story 1 — Spring: consume Clowder V2 dependency metadata

**Relevant audit material:** Finding 1, Spring half; V2 preference, V1 fallback, and TLS cases
from Finding 5.

**Affected services:** `swatch-api` and `swatch-tally`, through shared `swatch-core`.

### Context and work

Current client configuration uses the V1 `endpoints` and `privateEndpoints` arrays and
truststores derived from the global `tlsCAPath`. Extend the existing configuration path to
prefer `dependencyEndpoints.v2` and `privateDependencyEndpoints.v2`, exposing each endpoint's
`uri`, `authenticated`, and `ca_certificate`. Retain the reference PR's V1 fallback behavior.

Relevant code:
[ClowderJsonPropertySource.java](/Users/lburnett/code/rhsm-subscriptions/swatch-core/src/main/java/org/candlepin/subscriptions/clowder/ClowderJsonPropertySource.java),
[ClowderJson.java](/Users/lburnett/code/rhsm-subscriptions/swatch-core/src/main/java/org/candlepin/subscriptions/clowder/ClowderJson.java).

### Acceptance criteria

- [x] Public and private V2 endpoints take precedence when usable V2 metadata is available.
- [x] V1 fallback and existing explicit/local configuration overrides remain supported and tested.
- [x] Endpoint URI and authentication requirement are available to consumers.
- [x] TLS configuration uses the selected endpoint's CA when supplied; V2 endpoints without a CA
  use system trust rather than inheriting an unrelated global CA.
- [x] Automated tests cover V2 selection, V1 fallback, and endpoint-specific trust behavior.

**Dependencies:** None. Can proceed alongside Stories 2 and 3.

## Story 2 — Quarkus: consume Clowder V2 dependency metadata

**Relevant audit material:** Finding 1, Quarkus half; the local config-source audit;
configuration coverage from Finding 5.

**Affected services:** `swatch-contracts` and `swatch-utilization`.

### Context and work

The local `clowder-quarkus-config-source` checkout was inspected on 2026-09-24 at commit
`877107b` (version `2.12.0`), matching SWATCH's pinned dependency. It does not implement V2:

- `ClowderConfig` models only the V1 `endpoints` and `privateEndpoints` arrays. The factory
  ignores unknown JSON properties, so V2 metadata is discarded rather than made available.
- All four required/optional public/private endpoint handlers use those V1 arrays. They
  construct a URL from host and port and expose truststore settings, but no authentication flag.
- `ClowderConfigSource` generates and caches one truststore from the global `tlsCAPath`;
  it cannot select a different CA for each endpoint.

Recommended delivery is two ordered subtasks within this story. These describe work to coordinate
with the library maintainers, not an assumption that SWATCH must implement every upstream change.
The local audit does not establish whether a newer published release or unmerged PR already
supplies some of this support; check that before starting implementation.

#### 2A — Add and release V2 support in the shared configuration library

Model `dependencyEndpoints.v2` and `privateDependencyEndpoints.v2`, preserving the
application/deployment lookup and each endpoint's `uri`, `authenticated`, and `ca_certificate`.
Add the property handlers and factory registration needed to expose those values. Define and
document V2-first selection with V1 fallback, retaining existing V1 property compatibility.
Required and optional lookups must work with V2-only configuration, without first requiring a V1
array. Preserve existing missing-endpoint behavior where applicable.

Extend trust handling so the selected endpoint uses its own CA, with separate cached truststores
when CAs differ. A V2 endpoint without a CA must leave the client on system trust, even when a
global V1 CA exists. Include library tests and property documentation, then obtain a published
version for SWATCH to consume. Expose the authentication requirement only; OAuth token acquisition
and request authentication remain in Stories 3–6.

#### 2B — Adopt and wire the released support in SWATCH

Upgrade the shared Quarkus parent dependency and configure RBAC's public endpoint and Export
Service's private endpoint to consume the new metadata. Preserve explicit/local overrides and
V1 fallback. Verify the actual Quarkus configuration resolves endpoint URI, authentication
requirement, and trust settings correctly, including optional private endpoints used by Contracts.
Run regression coverage for the other Quarkus consumers affected by the shared dependency upgrade.

Relevant code:
[swatch-quarkus-parent/pom.xml](/Users/lburnett/code/rhsm-subscriptions/swatch-quarkus-parent/pom.xml),
[shared security configuration](/Users/lburnett/code/rhsm-subscriptions/swatch-common-security/src/main/resources/application.properties),
[Contracts configuration](/Users/lburnett/code/rhsm-subscriptions/swatch-contracts/src/main/resources/application.properties).

Library code:
[ClowderConfig.java](/Users/lburnett/code/clowder-quarkus-config-source/src/main/java/com/redhat/cloud/common/clowder/configsource/ClowderConfig.java),
[ClowderConfigSourceFactory.java](/Users/lburnett/code/clowder-quarkus-config-source/src/main/java/com/redhat/cloud/common/clowder/configsource/ClowderConfigSourceFactory.java),
[EndpointsClowderPropertyHandler.java](/Users/lburnett/code/clowder-quarkus-config-source/src/main/java/com/redhat/cloud/common/clowder/configsource/handlers/EndpointsClowderPropertyHandler.java),
[OptionalPrivateEndpointsClowderPropertyHandler.java](/Users/lburnett/code/clowder-quarkus-config-source/src/main/java/com/redhat/cloud/common/clowder/configsource/handlers/OptionalPrivateEndpointsClowderPropertyHandler.java),
[ClowderConfigSource.java](/Users/lburnett/code/clowder-quarkus-config-source/src/main/java/com/redhat/cloud/common/clowder/configsource/ClowderConfigSource.java),
[ConfigSourceTest.java](/Users/lburnett/code/clowder-quarkus-config-source/src/test/java/com/redhat/cloud/common/clowder/configsource/ConfigSourceTest.java).

### Acceptance criteria

- [ ] A V2-capable library version is published, adopted by SWATCH, and recorded with its PR/release.
- [x] Public/private V2 endpoint URI, authentication requirement, and CA information are exposed
  through documented properties and resolve correctly in SWATCH.
- [x] Tests cover V1-only, V2-only, and mixed configuration, V2 precedence, V1 fallback, and
  required/optional missing-endpoint behavior.
- [x] Tests cover endpoints with different CAs and V2 system trust without a CA, including when
  a global V1 CA is present. Invalid/unreadable supplied CAs fail clearly without disabling TLS checks.
- [ ] Local, test, and ephemeral configuration overrides continue to work.
- [ ] Other Quarkus consumers of the upgraded shared dependency retain their existing
  configuration behavior.

**Dependencies:** Can proceed alongside Stories 1 and 3. Subtask 2B depends on the published
library support from 2A; SWATCH wiring/tests can be prepared before that release. If an upstream
release cannot be obtained in time, explicitly agree a SWATCH-local adapter as an alternative
and retain the same acceptance criteria except for the upstream publication requirement.

### Local library POC — 2026-09-24

A POC is implemented in the local `clowder-quarkus-config-source` checkout. Its unpublished
snapshot is consumed by the SWATCH local integration POC below, not by a deployed release.
See the [library POC documentation](/Users/lburnett/code/clowder-quarkus-config-source/README.adoc).

- Adds V2 public/private endpoint models, V2-first lookup with per-endpoint V1 fallback, and
  endpoint-specific CA truststores. Existing V1 properties remain unchanged.
- Uses opt-in properties such as `clowder.dependency-endpoints.rbac.service.uri` and
  `clowder.optional-private-dependency-endpoints.export-service.service.authenticated`.
  The new names allow endpoint discovery and client authentication to be adopted together;
  upgrading the library alone does not redirect existing V1 consumers.
- Exposes `uri`, `authenticated`, `ca-certificate`, and `trust-store-*` settings. A V2 endpoint
  without a CA exposes no custom truststore; consumers must use system trust.
- New optional lookups return no value for absent endpoints so typed defaults such as
  `authenticated:false` work. Existing optional V1 properties retain their empty-string behavior.
- All 88 tests pass on Java 17: 59 existing tests and 29 new selection, TLS, and configuration-factory tests.
  Packaging also succeeds with `-Drevision=2.12.0-SWATCH-5598-SNAPSHOT`; the artifact is now
  bundled under `third-party/clowder`, not published to a Maven repository.
- OAuth/token handling and SWATCH client wiring are implemented separately below. Native-image
  verification and live cross-cluster validation remain outside this POC. Property naming/API review and upstream release coordination
  are still required before Story 2 is complete.

### Local SWATCH Quarkus HTTP POC — 2026-09-24

This records the initial extension of the library POC into parts of Stories 2–5. The implementation
has since been committed and pushed; that is not a statement that deployment or multicluster
readiness validation is complete. See the implementation checkpoint above for current coverage.

- **Dependency:** The shared Quarkus parent consumes `2.12.0-SWATCH-5598-SNAPSHOT` through
  `clowder-quarkus-config-source.version`. The temporary vendored bundle installs that coordinate
  for development and CI; replace it with an agreed upstream version before shipping, unless
  the team explicitly approves shipping the vendored dependency.
- **Discovery and TLS:** Shared Quarkus RBAC and Contracts Export configuration use the new
  public/private dependency properties. V1 fallback and development/test endpoint overrides remain.
  `RBAC_TRUST_STORE*` and `EXPORT_SERVICE_TRUST_STORE*` expose the selected endpoint's trust settings;
  an absent V2 CA leaves the clients on system trust.
- **Tokens:** [HccAuthTokenProvider.java](/Users/lburnett/code/rhsm-subscriptions/swatch-common-security/src/main/java/com/redhat/swatch/common/security/HccAuthTokenProvider.java)
  lazily initializes one Kessel SDK credential provider from the existing `KESSEL_AUTH_*` settings.
  The SDK handles token reuse, concurrent callers, and expiry-based renewal. Required authentication
  fails the request when credentials/discovery/token acquisition fail; it does not downgrade to anonymous access.
- **RBAC permissions:** [RbacAuthHeaderProvider.java](/Users/lburnett/code/rhsm-subscriptions/swatch-common-security/src/main/java/com/redhat/swatch/common/security/RbacAuthHeaderProvider.java)
  is registered on the generated `AccessApi`, covering interactive and background export permission
  checks. It uses `RBAC_AUTHENTICATED`, adds workload Bearer authentication, and preserves `x-rh-identity`.
- **RBAC workspaces:** [RbacWorkspaceClient.java](/Users/lburnett/code/rhsm-subscriptions/swatch-common-security/src/main/java/com/redhat/swatch/common/security/RbacWorkspaceClient.java)
  supplies the separate SDK HTTP client with the same token provider and RBAC TLS trust settings.
  Authentication is required by `RBAC_AUTHENTICATED` or `KESSEL_AUTH_ENABLED`; existing
  credential-driven workspace authentication is also preserved. This does not add Kessel gRPC authentication.
- **Export:** [ExportPskHeaderProvider.java](/Users/lburnett/code/rhsm-subscriptions/swatch-contracts/src/main/java/com/redhat/swatch/contract/service/export/ExportPskHeaderProvider.java)
  keeps its historical name but selects Bearer versus PSK from `EXPORT_SERVICE_AUTHENTICATED`.
  Both uploads and error callbacks use the filter. Bearer mode removes the PSK header and needs
  no PSK; legacy mode requires a nonblank PSK and sends no Bearer header. Placeholder PSKs are
  limited to development/test/component-test profiles. The Contracts template's `export-psk`
  secret reference is optional so Bearer-mode pods do not depend on that unused secret.

To reproduce locally, use Java 25 and run `sh bin/install-vendored-clowder.sh` from the SWATCH root
before the test command below. The library's released `2.12.0` artifact is not overwritten.
See the [bundle documentation](../third-party/clowder/README.md) for source provenance,
rebuilding with Java 17, custom Maven settings, and removal after upstream release.

**Verified locally:** 60 focused tests pass on Java 25 (31 new tests and 29 existing authorization
regressions), with formatting and style checks enabled. Coverage includes concurrent token reuse,
expiry-based renewal, authentication failures, actual generated RBAC/Export requests to loopback
mock servers, SDK workspace calls, TLS-context selection, and resolution of SWATCH's application
properties through the library. These are local tests, not live HCC validation.

Focused test command from the SWATCH repository root:

```shell
mvn -pl swatch-common-security,swatch-contracts -am \
  -Dtest=HccAuthTokenProviderTest,RbacAuthHeaderProviderTest,RbacWorkspaceClientTest,ExportPskHeaderProviderTest,ExportAuthClientTest,HccEndpointConfigurationTest,KesselAuthorizationServiceTest,RbacRolesAugmentorTest,KesselRolesAugmentorTest,KesselPropertiesTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

**Follow-up implementation:** Spring RBAC/Export and shared Kessel gRPC authentication/port handling
are now implemented; see the checkpoint above. Remaining gates are upstream library review/release,
full service/component regressions, native-image verification where required by the release pipeline,
live HCC validation, and deployment/cutover coordination. No live HCC requests or deployments were
performed. The gRPC tests now exercise a real local TLS handshake, not just mocked stubs.

## Story 3 — Provide shared HCC workload authentication

**Relevant audit material:** Shared credential/token needs in Findings 2 and 3.
**Review addition:** Reuse authentication across HTTP clients and Kessel gRPC, including token
renewal and failure behavior.

**Affected services:** All four affected deployments.

### Context and work

Spring and Quarkus already initialize SDK OAuth client credentials for RBAC workspace lookups.
Use the accepted Host Inventory contract and existing `KESSEL_AUTH_*` credential settings to
provide reusable token acquisition and renewal for the client integrations in Stories 4–6.

Keep token acquisition separate from the decision about whether a particular endpoint requires
authentication. That decision belongs to the consuming client configuration.

Relevant code:
[Spring KesselConfiguration.java](/Users/lburnett/code/rhsm-subscriptions/src/main/java/org/candlepin/subscriptions/security/KesselConfiguration.java),
[Quarkus KesselAuthorizationService.java](/Users/lburnett/code/rhsm-subscriptions/swatch-common-security/src/main/java/com/redhat/swatch/common/security/KesselAuthorizationService.java).

### Acceptance criteria

- [x] Both runtimes can obtain and renew workload tokens using the existing SDK and accepted contract.
- [x] Tokens can be consumed by HTTP and gRPC authentication integrations.
- [x] Required credentials and settings are wired into each consuming deployment template.
- [x] Authentication failures propagate to the caller; required authentication is not silently
  downgraded to an unauthenticated request or a PSK request.
- [x] Automated tests cover token reuse/renewal and missing or failed credentials.

**Dependencies:** None. Can proceed alongside Stories 1 and 2.

## Story 4 — Make all RBAC call paths multicluster-ready

**Relevant audit material:** Finding 2; RBAC validation requirements.
**Review addition:** Include workspace lookups and background export permission checks.

**Affected services:** All four affected deployments.

### Context and work

Wire the resolved RBAC endpoint URI, authentication requirement, and trust configuration into
Spring and Quarkus clients. Cover three call paths: ordinary permission checks, background export
permission checks, and the RBAC workspace lookup used by Kessel authorization.

Preserve `x-rh-identity` for identity-based permission calls. Workspace lookups use their existing
organization context and must be configured explicitly because they use a separate SDK HTTP client.

Relevant code:
[Spring API configuration](/Users/lburnett/code/rhsm-subscriptions/src/main/resources/application-api.yaml),
[Quarkus security configuration](/Users/lburnett/code/rhsm-subscriptions/swatch-common-security/src/main/resources/application.properties),
[Spring RbacApiClient.java](/Users/lburnett/code/rhsm-subscriptions/swatch-core/src/main/java/org/candlepin/subscriptions/rbac/RbacApiClient.java),
[Quarkus RbacService.java](/Users/lburnett/code/rhsm-subscriptions/clients/quarkus/rbac-client/src/main/java/com/redhat/swatch/clients/rbac/RbacService.java).
The workspace client wiring is in the two files listed under Story 3.

### Acceptance criteria

- [x] All three RBAC call paths use the intended endpoint and applicable TLS configuration.
- [x] Authenticated endpoints receive a workload Bearer token without losing user or organization context.
- [x] Existing unauthenticated/local behavior remains compatible with the reference contract.
- [x] Workspace requests honor their endpoint's trust and authentication requirements.
- [x] Tests cover authenticated/unauthenticated requests, token failures, and workspace lookups
  that are not satisfied from cache.
- [x] Authorization failures do not grant access.

**Dependencies:** Story 1 for Spring, Story 2 for Quarkus, and Story 3 for shared authentication.
Runtime-specific work can progress as its prerequisites become available.

## Story 5 — Make Export Service clients multicluster-ready

**Relevant audit material:** Finding 3; Export Service credential-selection and callback validation.

**Affected services:** `swatch-tally` and `swatch-contracts`.

### Context and work

Tally resolves the V1 private Export Service endpoint through its worker configuration.
Contracts uses V1 optional private-endpoint properties and an unconditional PSK header provider.
Update both clients to consume the V2 private endpoint and choose authentication from
`authenticated`: Bearer when true; the existing `x-rh-exports-psk` flow when false.

Relevant code:
[Tally worker configuration](/Users/lburnett/code/rhsm-subscriptions/src/main/resources/application-worker.yaml),
[Spring ExportApiClientFactory.java](/Users/lburnett/code/rhsm-subscriptions/clients/export-client/src/main/java/com/redhat/swatch/clients/export/api/client/ExportApiClientFactory.java),
[Contracts configuration](/Users/lburnett/code/rhsm-subscriptions/swatch-contracts/src/main/resources/application.properties),
[ExportPskHeaderProvider.java](/Users/lburnett/code/rhsm-subscriptions/swatch-contracts/src/main/java/com/redhat/swatch/contract/service/export/ExportPskHeaderProvider.java).

### Acceptance criteria

- [x] Both clients use the selected private endpoint and its applicable TLS configuration.
- [x] Authenticated requests send a Bearer token and omit the PSK.
- [x] Authenticated client initialization does not require a PSK.
- [x] Unauthenticated endpoints retain the existing PSK behavior.
- [x] Uploads and error callbacks both use the selected authentication mode.
- [x] Tests cover both modes and token failures, without falling back to PSK after an
  authenticated request fails.

**Dependencies:** Stories 1/2 for the respective runtimes and Story 3.
Complete export-flow validation also requires Story 4 because exports perform RBAC permission checks.

## Story 6 — Make Kessel gRPC connections multicluster-ready

**Relevant audit material:** Finding 4; Kessel transport and authorization validation.
**Review addition:** The shared gRPC client currently creates a TLS or insecure channel without
attaching OAuth call credentials.

**Affected services:** All four affected deployments.

### Context and work

Follow the accepted reference contract: retain the existing Kessel dependency lookup and introduce
`KESSEL_INVENTORY_API_PORT`, defaulting to `9000`. Update Spring's fixed-port expression and
Quarkus's URI-to-host conversion to honor this setting while preserving explicit local targets.

Attach workload authentication to the actual Kessel gRPC calls and validate secure transport.
The existing OAuth support for workspace lookups does not authenticate these gRPC calls.

Relevant code:
[Spring API configuration](/Users/lburnett/code/rhsm-subscriptions/src/main/resources/application-api.yaml),
[Quarkus KesselProperties.java](/Users/lburnett/code/rhsm-subscriptions/swatch-common-security/src/main/java/com/redhat/swatch/common/security/KesselProperties.java),
[KesselAuthorizationClient.java](/Users/lburnett/code/rhsm-subscriptions/swatch-common-kessel/src/main/java/com/redhat/swatch/kessel/KesselAuthorizationClient.java).
Deployment wiring spans the four affected services' ClowdApp templates.

### Acceptance criteria

- [x] The configured Kessel port is wired into deployment templates and used in the resolved gRPC target.
- [x] Existing explicit local/test endpoint overrides remain usable.
- [x] Secure Kessel calls carry workload credentials and verify TLS.
- [x] Token renewal and any channel recreation preserve authentication.
- [x] Automated tests exercise an authenticated gRPC endpoint, port overrides, and failure behavior.

**Dependencies:** Story 3 for authentication. Port/configuration work can start independently.
Full authorization-flow validation also requires the workspace path in Story 4.
Kessel does not depend on V2 endpoint adoption under the accepted reference pattern.

## Story 7 — Validate complete multicluster flows in non-production

**Relevant audit material:** Finding 5's environment-level coverage, the non-production readiness
checklist, and validation-environment questions.

**Affected services:** All four affected deployments.

### Context and work

Choose an appropriate non-production environment and validate real service discovery, TLS, and
authentication behavior after the relevant changes are deployed. Use realistic Clowder endpoint
metadata and authentication requirements.

### Acceptance criteria

- [ ] RBAC permission checks succeed for authorized identities and deny unauthorized identities.
- [ ] Kessel authorization exercises both an uncached RBAC workspace lookup and an authenticated
  gRPC check.
- [ ] Tally and Contracts exports complete through permission checks and upload/error callbacks.
- [ ] Both legacy in-cluster and multicluster configurations are exercised, including mixed
  configurations where only some HCC APIs have moved.
- [ ] Test results, deployed revisions, and readiness outcomes are recorded per integration.

**Dependencies:** Relevant completed portions of Stories 1–6. Validate each integration as it
becomes ready; do not defer implementation tests to this story.

**Remaining coordination:** Select the validation environment, test accounts, and owner.

### Execution checklist for the validation owner

1. Record environment, SWATCH revision/image, published library version, HCC endpoints, test
   organization, and owner. Do not record client secrets, bearer tokens, identity headers, or PSKs.
2. Confirm the `swatch-kessel-sa` secret and issuer are present for every consuming deployment;
   verify service-account authorization with HCC. Keep a PSK for integrations still using legacy Export.
3. Run a V1 baseline before migration: API authorization, background export authorization, Contracts
   export upload/error handling, and the currently enabled Kessel authorization path.
4. Enable the target integration independently. For HTTP, check the selected V2 `uri`,
   `authenticated`, and CA file; for Kessel set its host/port, TLS, and auth flag together. Exercise
   a mixed configuration, such as V2 RBAC with legacy Export, before moving the next integration.
5. Test authorized and unauthorized identities in both Spring and Quarkus. Use a fresh pod or
   an uncached organization to ensure Kessel performs an actual workspace request before its gRPC check.
6. Complete one successful export and one deliberate export error through both Tally and Contracts.
   Confirm the correct callback arrived and that bearer-mode requests did not include an Export PSK.
7. Observe a token renewal without restarting the pod. Exercise channel recreation/reconnection.
   In an isolated test deployment, test unavailable credentials/token service, an invalid CA, and
   denied authorization; confirm no anonymous/PSK fallback and no access granted.
8. Restore the saved legacy configuration as an agreed rollback exercise and rerun the baseline.
   Record pass/fail evidence, timestamps, sanitized logs, and defects separately for RBAC, Kessel,
   and Export. Do not declare readiness for an integration whose tests have not passed.

## Story 8 — Deploy readiness changes and coordinate HCC cutovers

**Relevant audit material:** Production deployment, migration checklist, monitoring, rollback,
incoming PR tracking, and scheduling/ownership questions.

**Affected services:** All four affected deployments; readiness tracked separately for RBAC,
Kessel, and Export Service.

### Context and work

Track the incoming HCC PRs and their coverage of Stories 1–6. Review and release the validated
changes through the usual SWATCH process. Once an integration is ready, coordinate HCC's
configuration-only traffic switch, beginning with Kessel as described in the initiative.

### Acceptance criteria

- [ ] HCC PR links, remaining SWATCH work, and review/deployment owners are recorded.
- [ ] Validated readiness changes are deployed to production and readiness is recorded per integration.
- [ ] HCC cutover timing and rollback responsibilities are agreed for each ready integration.
- [ ] After each cutover, verify authorization and affected export flows and monitor authentication,
  TLS, DNS, and outbound request errors.
- [ ] Record the outcome and any follow-up work; separate readiness completion from migration completion.

**Dependencies:** Story 7 evidence for the integration being deployed/cut over.
Independent HCC migrations need not wait for all other integrations.

**Remaining coordination:** Confirm which repositories receive HCC PRs, assign deployment and
monitoring owners, and schedule each switch.

### Deployment and rollback checklist

1. Publish the reviewed configuration-library change and replace the local snapshot pin with that
   release. Run normal CI, component tests, and required native builds; review incoming HCC PRs
   against this implementation to avoid duplicating it.
2. After Story 7 evidence is approved, release the readiness changes with the existing production
   routing preserved. Record image/revision and baseline authorization/export success and latency.
3. Agree the HCC switch window, on-call contacts, rollback owner, and stop conditions for each
   integration. Save its previous endpoint, CA/auth mode, gRPC port/TLS settings, and image reference.
4. HCC performs the agreed configuration change. Verify pod configuration has updated, then repeat
   the relevant permission, uncached workspace, gRPC, and export smoke tests.
5. Watch authentication failures (HTTP 401/403 and gRPC UNAUTHENTICATED/PERMISSION_DENIED), DNS/TLS
   errors, outbound failures/latency, Kessel check/channel metrics, and export task failures/backlog.
   Investigate changes against the baseline; never log workload credentials to diagnose them.
6. If rollback is needed, coordinate restoration of the saved routing and its matching authentication
   and trust settings. Preserve the PSK for a return to legacy Export. Do not work around a failure
   by disabling TLS or authentication against the multicluster endpoint. Roll back the image only
   if necessary and compatible with the restored configuration.
7. Record readiness and migration as separate outcomes for each integration, with follow-up owners.

## Reproducing local verification

Use Java 25 from the SWATCH root and install the bundled snapshot before running Maven directly:

```sh
sh bin/install-vendored-clowder.sh
mvn -pl swatch-core,clients/export-client,swatch-common-security,swatch-contracts -am \
  -Dtest=ClowderDependencyEndpointsTest,ClowderSpringConfigurationTest,ClowderJsonPropertySourceTest,RbacMulticlusterTest,ExportMulticlusterTest,ExportApiClientFactoryTest,KesselAuthenticatedTransportTest,KesselAuthorizationClientTest,HttpClientTest,HccAuthTokenProviderTest,RbacAuthHeaderProviderTest,RbacWorkspaceClientTest,ExportPskHeaderProviderTest,ExportAuthClientTest,HccEndpointConfigurationTest,KesselAuthorizationServiceTest,RbacRolesAugmentorTest,KesselRolesAugmentorTest,KesselPropertiesTest,MockKesselServerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

These tests need permission to bind local ports and attach the JVM test agent. They do not require
HCC credentials or call live HCC services. The TLS transport tests reuse the repository's existing
local test CA and restore process-level trust settings afterward.

```sh
mvn -pl swatch-tally,swatch-contracts,swatch-utilization -am \
  -Dtest=ClowderSpringConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false \
  -Dquarkus.container-image.build=false -Dquarkus.container-image.push=false \
  -Dquarkus.analytics.disabled=true package
```

## Sequencing and ownership notes

Stories 1, 2, and 3 can proceed in parallel. Stories 4 and 5 use the configuration foundation for
their respective runtime and the shared authentication capability. Story 6's port work can start
independently; its authentication uses Story 3, and its complete flow uses Story 4's workspace path.
Story 7 validates completed integrations, and Story 8 deploys and coordinates cutover per integration.

The pasted audit's contract questions about endpoint names, credentials, and Kessel port handling
are resolved for planning by the accepted Host Inventory reference contract. Configuration-library
support, release coordination, and adoption belong to Story 2; validation-environment selection belongs to Story 7; PR ownership
and cutover scheduling belong to Story 8.

The SWATCH audit was performed against the local main branch on 2026-09-22, with library and
SWATCH implementation work on 2026-09-24. Local verification is recorded above; validation against
live multicluster HCC environments has not yet been performed. The implementation branches have
been pushed, but no upstream library release or deployment has been made.
