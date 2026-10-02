# HCC Multicluster Integration Test Plan

Clowder V2 dependency-endpoint behavior is covered upstream ([clowder-quarkus-config-source](https://github.com/RedHatInsights/clowder-quarkus-config-source)). SWATCH will consume that via a **common library**. This plan validates the client’s V1/V2 resolution and fallback for **any** Clowder external dependency (for example export-service, RBAC, or Kessel). The same cases apply regardless of which dependency is wired. Run them in local component tests and on ephemeral under mocked multicluster rollout, using any SWATCH service that consumes the common library.

**Note:** V1 fallback is temporary for the multicluster migration. After migration completes, fallback logic will be removed and clients will use V2 only.

Strategy:

- Confirm whether the client used the V1 or V2 host by checking application logs and which mock received the outbound traffic.

## multicluster-TC001 - When V2 is not present, the client uses V1

1. **Description:** Clowder metadata has V1 endpoints and no V2 entry. The client uses V1.
2. **Setup:**
   - Test environment metadata: V1 present, V2 absent. V1 stub healthy.
   - Service under test uses the common library.
3. **Action:** Trigger a representative authorized call that causes the client to resolve the dependency.
4. **Verification:** Logs show the V1 host. The V1 stub receives traffic. No V2 endpoint used.
5. **Expected Result:** V1 is used when V2 is not present.

## multicluster-TC002 - When V2 is present but not active, the client uses V1

1. **Description:** Clowder metadata includes V2, but V2 is not active for the client. The client uses V1.
2. **Setup:**
   - Metadata: V1 and V2 present (distinct hosts). Both stubs healthy.
   - Service is configured to force V1 resolution while V2 metadata remains present.
3. **Action:** Trigger a representative authorized call that causes the client to resolve the dependency.
4. **Verification:** Logs show the V1 host. The V1 stub receives traffic. The V2 stub does not.
5. **Expected Result:** V1 is used when V2 is present but not active.

## multicluster-TC003 - When the V2 URI is absent or blank, the client falls back to V1

1. **Description:** The dependency is configured to resolve from Clowder V2 dependency-endpoint properties, but the V2 URI is absent or blank. The client falls back to V1.
2. **Setup:**
   - Metadata: V1 healthy. V2 entry present with absent/blank URI.
   - Service is configured to read the dependency from Clowder V2 dependency-endpoint properties (not V1-only endpoint keys).
3. **Action:** Trigger a representative authorized call that causes the client to resolve the dependency.
4. **Verification:** Logs show V1 fallback. The V1 stub receives traffic.
5. **Expected Result:** V1 is used when the V2 URI is absent or blank. The call completes while V1 is healthy.

## multicluster-TC004 - When V2 is unavailable, the client uses V1 and logs an error

1. **Description:** A valid V2 URI is configured, but the V2 host is down or unreachable. The client uses V1 and logs an error.
2. **Setup:**
   - Metadata: V1 and valid V2 present (distinct hosts).
   - Service is configured to resolve the dependency from Clowder V2 dependency-endpoint properties.
   - V2 stub unavailable. V1 stub healthy.
3. **Action:** Trigger a representative authorized call that causes the client to resolve the dependency.
4. **Verification:**
   - Error is logged indicating V2 was unavailable / fallback to V1.
   - Logs show the V1 host for the successful path.
   - The V1 stub receives traffic. The call succeeds.
5. **Expected Result:** V1 is used and an error is logged when V2 is unavailable. No sustained outage while V1 is healthy.

## multicluster-TC005 - When V2 metadata is invalid, the client falls back to V1 and logs an error

1. **Description:** V2 metadata has an invalid URI or is missing `authenticated`. The client falls back to V1 and logs an error.
2. **Setup:**
   - Metadata: invalid V2 URI and/or missing `authenticated`. V1 present and healthy.
   - Service is configured to resolve the dependency from Clowder V2 dependency-endpoint properties.
3. **Action:** Trigger a representative authorized call that causes the client to resolve the dependency.
4. **Verification:**
   - Error is logged indicating V2 metadata was invalid / fallback to V1.
   - Logs show the V1 host for the successful path.
   - The V1 stub receives traffic. The call succeeds.
5. **Expected Result:** V1 is used and an error is logged when V2 metadata is invalid or missing `authenticated`. No sustained outage while V1 is healthy.

## multicluster-TC006 - When V2 is present and available, the client uses V2

1. **Description:** Clowder metadata has a valid V2 URI and the V2 host is healthy. The client uses V2.
2. **Setup:**
   - Metadata: V1 and valid V2 present. V2 stub healthy.
   - Service is configured to resolve the dependency from Clowder V2 dependency-endpoint properties.
3. **Action:** Trigger a representative authorized call that causes the client to resolve the dependency.
4. **Verification:** Logs show the V2 host. The V2 stub receives traffic.
5. **Expected Result:** V2 is used when V2 is present and available.

## multicluster-TC007 - When V2 becomes unavailable, the client uses V1 and logs an error

1. **Description:** After the client has been using V2 successfully, the V2 host becomes unavailable. Later calls use V1 and log an error.
2. **Setup:**
   - Metadata: valid V2 present and healthy. V1 stub healthy.
   - Service is configured to resolve the dependency from Clowder V2 dependency-endpoint properties.
   - Confirm an initial call succeeds on V2.
3. **Action:** Make the V2 stub unavailable (stop or block). Keep V1 healthy. Re-trigger the representative call without changing Clowder metadata.
4. **Verification:**
   - Error is logged indicating V2 became unavailable / fallback to V1.
   - Logs show the V1 host after the outage.
   - The V1 stub receives traffic. The call succeeds.
5. **Expected Result:** V1 is used and an error is logged when V2 becomes unavailable. No sustained outage while V1 is healthy.

## multicluster-TC008 - When neither V1 nor V2 is usable, resolution fails

1. **Description:** Neither V1 nor V2 can be used. Resolution fails.
2. **Setup:**
   - Metadata/stubs: no usable V1 or V2.
   - Service is configured to resolve the dependency from Clowder V2 dependency-endpoint properties where applicable.
3. **Action:** Trigger a representative authorized call (or start the service if config fails at startup).
4. **Verification:** Failure is logged. Stubs receive no successful dependency traffic.
5. **Expected Result:** Resolution fails when neither V1 nor V2 is usable.
