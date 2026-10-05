# System & Infrastructure

**Functional Area:** System-level features, integrations, and health checks

This test plan covers system-level functionality including version information endpoints and database integrations.

**Test Coverage:**
- Version API endpoint
- HBI (Host-Based Inventory) database integration
- Build metadata exposure

---

## Version API

**version-TC001 - Build metadata from version endpoint**

- **Description**: Verify `GET /v1/version` returns service metadata for the deployed swatch-tally application
- **Setup**: Component test environment with swatch-tally running
- **Action**: Call the public version endpoint
- **Verification**:
    - HTTP 200
    - Response includes non-empty `build.version`, `build.artifact`, `build.name`, and
      `build.group`
    - `build.version` is the git commit SHA (7–40 hex chars)
    - `build.artifact` is `swatch-tally`
- **Expected Result**: Version endpoint is available and exposes build info suitable for test-run traceability

## Nightly Tally with the HBI database

**nightly-tally-TC001 - Validate tally on physical RHEL sockets with socket increase mapping**

- **Description**: Verify that nightly tally correctly processes a physical RHEL host from HBI and increments socket counts
- **Setup**: 
  - Component test environment with swatch-tally running and HBI database available
  - Organization is opted in
  - Record baseline socket count for the organization (before test)
  - Define fixture host with known socket count (e.g., 4 sockets)
- **Action**: 
  - Insert the fixture RHEL host into the HBI database with specified socket count
  - Run nightly tally
  - Query instance report by host ID or display name
  - Query current organization socket totals
- **Verification**:
  - Instance report contains the fixture host with correct display name
  - Instance measurements are not null
  - Instance category is PHYSICAL
  - Instance socket measurement matches fixture socket count (e.g., 4)
  - Organization total sockets = baseline + fixture sockets (e.g., baseline + 4)
- **Expected Result**: Nightly tally correctly processes the specific host from HBI and increments organization totals by the host's socket count

**nightly-tally-TC002 - Validate tally and sum of instances match for non-payg product**

- **Description**: Verify the unfiltered daily tally for OpenShift Container Platform matches the sum of its unfiltered instance measurements for sockets and cores
- **Setup**:
  - Organization is opted in
  - Insert one virtual OpenShift Container Platform host into HBI with 2 sockets, 7 cores per socket, Premium SLA, and Development/Test usage
  - Use the current UTC day as the query window
- **Action**:
  - Run nightly tally for the organization
  - Query the unfiltered instance report for OpenShift Container Platform
  - Query the unfiltered daily tally report for Sockets and Cores
- **Verification**:
  - Instance report contains exactly one instance
  - Instance measurements total 2 Sockets and 7 Cores
  - Latest daily tally values for Sockets and Cores match the corresponding instance measurement totals
- **Expected Result**:
  - Unfiltered daily tally values match the sums of unfiltered instance measurements for the same product and UTC day
