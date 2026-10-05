# HCC Multicluster Integration Test Plan

This plan covers V1/V2 selection and fallback by the client that makes the outbound call to a Clowder-managed dependency. Its scope is the dependency-calling client, not the framework-specific source of its endpoint properties.

**Note:** V1 fallback is temporary for the multicluster migration. After migration completes, fallback logic will be removed and clients will use V2 only.

Strategy:

- For each scenario, query the consuming service's management `GET /info` endpoint. Its response must expose the effective URI for the dependency under test so it can be compared with the distinct V1 and V2 values in Clowder metadata.
- For scenarios that cover reachability, trigger a representative authorized call and verify its API result against the configured dependency's availability. Use the real dependency endpoints in the test environment; no application log checks or WireMock/stub setup is needed.

## Clowder metadata shape (V1 vs V2)

V1 and V2 are different top-level keys in the Clowder `cdappconfig` JSON. V1 uses `endpoints` / `privateEndpoints` with `hostname` + `port`. V2 uses `dependencyEndpoints.v2` / `privateDependencyEndpoints.v2` with `uri` + `authenticated`. (source [Clowder Dependencies Provider documentation](https://github.com/RedHatInsights/clowder/blob/master/docs/providers/dependencies.md#generated-app-configuration))

```json
{
  "endpoints": [
    {
      "app": "rbac",
      "name": "service",
      "hostname": "rbac.svc",
      "port": 8000
    }
  ],
  "privateEndpoints": [
    {
      "app": "export-service",
      "name": "service",
      "hostname": "export.svc",
      "port": 10000
    }
  ],
  "dependencyEndpoints": {
    "v2": {
      "rbac": {
        "service": {
          "uri": "https://rbac.remote.example:443/base",
          "authenticated": true
        }
      }
    }
  },
  "privateDependencyEndpoints": {
    "v2": {
      "export-service": {
        "service": {
          "uri": "https://export.remote.example:443",
          "authenticated": true
        }
      }
    }
  }
}
```

## multicluster-TC001 - When V2 is absent, the client uses V1

1. **Description:** Clowder metadata has V1 endpoints and no V2 entry. The client uses V1.
2. **Setup:**
   - Test environment metadata: V1 present and reachable, V2 absent.
   - Service under test uses the common library.
3. **Action:** Trigger a representative authorized call that causes the client to resolve the dependency.
4. **Verification:** The service's `/info` response reports the V1 URI. The call succeeds through V1.
5. **Expected Result:** V1 is used when V2 is not present.

## multicluster-TC002 - Explicit V1 mode ignores V2 metadata

1. **Description:** V2 metadata is present, but the service is configured for explicit `v1` mode. The client ignores V2 metadata and uses the existing in-cluster V1 endpoint.
2. **Setup:**
   - Metadata: V1 and V2 present (distinct URIs), and both endpoints are reachable.
   - Start the service with its dependency-resolution mode explicitly set to `v1`.
3. **Action:** Trigger a representative authorized call that causes the client to resolve the dependency.
4. **Verification:** The service's `/info` response reports the V1 URI. The call succeeds.
5. **Expected Result:** Explicit `v1` mode forces V1 endpoint resolution even when V2 metadata is present.

## multicluster-TC003 - When the V2 URI is absent or blank, the client falls back to V1

1. **Description:** The dependency is configured to resolve from Clowder V2 dependency-endpoint properties, but the V2 URI is absent or blank. The client falls back to V1.
2. **Setup:**
   - Metadata: V1 present and reachable. V2 entry is present with an absent or blank URI.
   - Service is configured to read the dependency from Clowder V2 dependency-endpoint properties (not V1-only endpoint keys).
3. **Action:** Trigger a representative authorized call that causes the client to resolve the dependency.
4. **Verification:** The service's `/info` response reports the V1 URI. The call succeeds through V1.
5. **Expected Result:** V1 is used when the V2 URI is absent or blank. The call completes while V1 is healthy.

## multicluster-TC004 - When V2 is unavailable, the client falls back to V1

1. **Description:** A valid V2 URI is configured, but the V2 endpoint is unavailable. The client falls back to V1.
2. **Setup:**
   - Metadata: V1 and valid V2 present (distinct hosts).
   - Service is configured to resolve the dependency from Clowder V2 dependency-endpoint properties.
   - V2 endpoint is unreachable. V1 endpoint is reachable.
3. **Action:** Trigger a representative authorized call that causes the client to resolve the dependency, then query the service's `/info` endpoint.
4. **Verification:**
   - The service's `/info` response reports the V1 URI after fallback.
   - The call succeeds through V1.
5. **Expected Result:** The client uses V1 when V2 is unavailable and V1 is reachable.

## multicluster-TC005 - When V2 metadata is missing `authenticated`, the client falls back to V1

1. **Description:** V2 metadata has a valid URI but is missing the `authenticated` field. The client falls back to V1.
2. **Setup:**
   - Metadata: V1 is present and reachable. The V2 entry has a valid URI but omits `authenticated`.
   - Service is configured to resolve the dependency from Clowder V2 dependency-endpoint properties.
3. **Action:** Trigger a representative authorized call that causes the client to resolve the dependency.
4. **Verification:**
   - The service's `/info` response reports the V1 URI.
   - The call succeeds through V1.
5. **Expected Result:** The client uses V1 when the V2 entry is missing `authenticated` and V1 is reachable.

## multicluster-TC006 - When V2 is present and available, the client uses V2

1. **Description:** Clowder metadata has a valid V2 URI and the V2 host is healthy. The client uses V2.
2. **Setup:**
   - Metadata: V1 and valid V2 present, with V2 reachable.
   - Service is configured to resolve the dependency from Clowder V2 dependency-endpoint properties.
3. **Action:** Trigger a representative authorized call that causes the client to resolve the dependency.
4. **Verification:** The service's `/info` response reports the V2 URI. The call succeeds through V2.
5. **Expected Result:** V2 is used when V2 is present and available.

## multicluster-TC007 - When V2 becomes unavailable, later calls use V1

1. **Description:** After the client has been using V2 successfully, the V2 endpoint becomes unavailable. Later calls use V1.
2. **Setup:**
   - Metadata: valid V2 present and reachable. V1 is reachable.
   - Service is configured to resolve the dependency from Clowder V2 dependency-endpoint properties.
   - Confirm an initial call succeeds and `/info` reports V2.
3. **Action:** Make the V2 endpoint unreachable while keeping V1 reachable. Re-trigger the representative call without changing Clowder metadata, then query `/info`.
4. **Verification:**
   - The service's `/info` response reports the V1 URI after fallback.
   - The call succeeds through V1.
5. **Expected Result:** Later calls use V1 when V2 becomes unavailable and V1 is reachable.

## multicluster-TC008 - When both configured endpoints are unreachable, dependency calls fail

1. **Description:** Both configured V1 and V2 endpoints are unreachable. Before the dependency call, `/info` reports the selected V2 URI; the call fails because neither endpoint is reachable.
2. **Setup:**
   - Metadata contains valid V1 and V2 URIs, but both endpoints are unreachable.
   - Service is configured to resolve the dependency from Clowder V2 dependency-endpoint properties.
3. **Action:** Query `/info` to confirm the dependency URI is resolved, then trigger a representative authorized call.
4. **Verification:** The dependency call fails because both configured endpoints are unreachable.
5. **Expected Result:** The service cannot complete the dependency call when neither V1 nor V2 is usable.
