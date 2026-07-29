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

- **Description**: Verify that the tally data shows increase count and system table show the correct data
- **Setup**: Component test environment with swatch-tally is running and an instance of insights db is up, your org is 
     opted in and you have the current sockets count
- **Action**: Insert a rhel host into the HBI database and run the tally 
- **Verification**:
  - verify that initial sockets count plus the expected reported sockets equals the current sockets count 
  - verify that the instance report, instance's 'measurements' is not null
  - verify that the following are as expected: 
    - Display name 
    - category
    - expected reported sockets count 
- **Expected Result**: The tally data shows increase count and system table show the correct data
