**Goal:** Analyze the setup and execution of the test method named **$arg** to generate both SQL data setup, any WireMock mappings the test uses, and any relevant API requests as runnable curl commands.

**Instructions for the AI:**

1.  **Locate Context:** Find the definition and all relevant setup code for the test method `$arg`.
2.  **Generate SQL Setup:**
    * Analyze all **data object creation** (e.g., ORM initialization, repository saves) in the setup phase.
    * Generate a single, executable **SQL script** to replicate this data.
    * **Format:** Use `INSERT` statements with explicit columns. Include `DELETE FROM` statements for affected tables before the `INSERT`s.
3.  **Detect and Generate WireMock Mappings (if the test uses WireMock):**
    * **Detection:** Look for WireMock usage in the test or its helpers: e.g. `wiremock.`, `WiremockService`, `forRhsmApi()`, `stubConsumersForOrg`, `stub*`, or any code that posts to `/__admin/mappings`. Trace from the test method into base classes and API/stub classes (e.g. `RhsmApiStubs`, `ConduitWiremockService`). Example: `swatch-system-conduit/ct/java/tests/PublicCloudConduitComponentTest.java` uses `wiremock.forRhsmApi().stubConsumersForOrg(orgId, List.of(consumer))`; the mapping is built in `swatch-system-conduit/ct/java/api/RhsmApiStubs.java`.
    * **Mapping format:** Produce WireMock mapping JSON matching the project’s format. Each mapping has:
      * `request`: `method`, `urlPathPattern` (or `urlPath`), and `headers` (e.g. `X-RhsmApi-AccountID` with `equalTo` for org-scoped stubs). Use the same structure as in the stub code (e.g. `RhsmApiStubs.stubConsumersForOrg` builds a GET to `/candlepin/consumers/feeds.*` with header `X-RhsmApi-AccountID`).
      * `response`: `status`, `headers` (e.g. `Content-Type: application/json`), and either inline `body` (JSON string) or `bodyFileName` pointing to a file under `__files/`.
    * **Response body:** Derive the response body from the test’s stub data (e.g. consumer list, pagination). If the stub uses a builder like `RhsmApiStubs.buildFullConsumer` / `buildMinimalConsumer`, replicate the same field structure and the values used in the test (constants, `orgId`, etc.).
    * **Output both:**
      * **Static mapping file(s):** JSON suitable for `config/wiremock/mappings/` (and, if using `bodyFileName`, a separate body file in `config/wiremock/__files/`). Name files clearly (e.g. `rhsm-consumers-feeds-<test-or-org>.json`).
      * **Optional curl to register at runtime:** A `curl` command that POSTs the same mapping JSON to WireMock’s admin API (e.g. `http://localhost:8006/__admin/mappings` when using the project’s docker-compose WireMock port), so the stub can be added after WireMock is running.
4.  **Generate cURL Commands:**
    * Analyze the **execution phase** of `$arg` for any direct or indirect API/HTTP requests
    * For each request found, generate a corresponding **`curl` command**.
    * **Include:** Method (`-X POST`), URL, Headers (especially `Authorization` or `Content-Type`), and the Request Body (`-d`).
    * **Crucial Context for Identity Header:** When the test setup includes an identity or user context, ensure the correct `x-rh-identity` header is included in the cURL command. The value for this header is a Base64-encoded JSON payload, which can take one of the following example formats:

    **Example Identity Header Payloads:**

    ```json
    {
      "identity": {
        "type": "Associate",
        "associate" : {
          "email": "test@example.com"
        }
      }
    }
    ```
    ```json
    {
      "identity": {
        "type": "X509",
        "x509" : {
          "subject_dn": "CN=test.example.com"
        }
      }
    }
    ```
    ```json
    {
      "identity": {
        "type": "User",
        "org_id": "org123"
      }
    }
    ```

5.  **Output Structure:**
    * **Section 1: Initial Environment Setup** (The preparatory steps below).
    * **Section 2: SQL Data Setup** (The generated SQL script).
    * **Section 3: WireMock Mappings** (Only if the test uses WireMock.) Static mapping JSON file(s) for `config/wiremock/mappings/`, any `__files` body file(s), and optionally a `curl` command to POST the mapping to `http://localhost:8006/__admin/mappings`.
    * **Section 4: API Command Execution** (The list of generated `curl` commands).

**Example cURL Analysis (for context, do not include in final output):**
If the test calls `POST /api/rhsm-subscriptions/v1/instances/billing_account_ids` with a JSON body, the output should include:
```bash
curl -X GET 'http://localhost:8000/api/rhsm-subscriptions/v1/instances/billing_account_ids?org_id=org1&product_tag=rhel-for-arm&billing_provider=aws' \
  -H 'Accept: application/json' \
  -H 'x-rh-identity: eyJpZGVudGl0eSI6eyJvcmdfaWQiOiJvcmcxIiwidHlwZSI6IkFzc29jaWF0ZSIsImF1dGhfdHlwZSI6ImJhc2ljLWF1dGgiLCJpbnRlcm5hbCI6eyJhdXRoX3RpbWUiOjAsIm9yZ19pZCI6Im9yZzEifX19'
```

---

## **Output**

### **Section 1: Initial Environment Setup**

These steps are required to start the local environment before executing the data setup or API commands. Replace `swatch-tally` with the correct project name (e.g., `swatch-contracts`, `swatch-metrics`).

1.  **Start Pods**
    ```bash
    podman-compose up -d
    ```


2.  **Start Service**
    ```bash
    make swatch-tally
    ```

---

### **Section 2: SQL Data Setup**

* *Insert generated SQL script here*

---

### **Section 3: WireMock Mappings** *(omit if the test does not use WireMock)*

If the test stubs external APIs via WireMock (e.g. `wiremock.forRhsmApi().stubConsumersForOrg(...)`), provide:

1. **Mapping file(s)** for `config/wiremock/mappings/` (WireMock request/response JSON).
2. **Body file(s)** for `config/wiremock/__files/` if the mapping uses `bodyFileName`.
3. **Optional:** A `curl` command to register the mapping at runtime, e.g.:
   ```bash
   curl -X POST 'http://localhost:8006/__admin/mappings' \
     -H 'Content-Type: application/json' \
     -d @config/wiremock/mappings/<mapping-file>.json
   ```

* *Insert WireMock mapping JSON and any curl command here, or state "This test does not use WireMock."*

---

### **Section 4: API Command Execution**

* *Insert generated `curl` commands here*
