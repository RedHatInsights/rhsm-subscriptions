### 0001 - Use UMB Message End Date for Contract Lifecycle Management

* **Status:** Accepted
* **Deciders:** @karshah
* **Date:** 2025-09-10

---

### Context

The SWATCH service needs to correctly manage the contract lifecycle (creation, updates, and termination) based on messages received via the UMB message bus from the IT partner gateway. A key challenge is determining the appropriate `end_date` for the contract record. This decision is critical for the test plan, implementation, and the accuracy of downstream services like Billable Usage. The service is not the source of truth for contract state, and must rely on messages from the IT partner gateway.

Note: Although the partner entitlement message schema includes a `status` field (e.g., `SUBSCRIBED`, `UNSUBSCRIBED`), the swatch-contracts backend does not use this field in any business logic. Contract state (active or terminated) is determined exclusively by the `start_date` and `end_date` from the message. A contract is considered active when `start_date <= now` and (`end_date >= now` or `end_date` is null), and terminated when `end_date < now`.

### Decision

We will use the entitlement dates (`start_date` and `end_date`) provided in the UMB message as the source of truth for contract lifecycle management. When a UMB message is received for an existing contract, the `end_date` will be updated with the value from the message, functioning as an upsert that overwrites any previous end date. When a message is received for a non-existing contract, a new contract will be created with the dates from the message. This approach avoids impacting other services.

### Consequences

* **Positive:**
    * **Data Integrity:** The `end_date` in our system will always be based on the most recent, authoritative dates from the source, reducing data discrepancies.
    * **Simplicity:** The logic is simplified by directly using the provided dates, avoiding complex assumptions about status semantics or manual calculations.
    * **Robustness:** The system will gracefully handle messages for both existing and non-existing contracts without causing errors or negatively impacting other services.
    * **Test Alignment:** This decision directly addresses the open points in our test plan and provides a clear path for unit and API level testing.

* **Negative:**
    * **Dependency:** This approach creates a strong dependency on the IT partner gateway to consistently provide correct `start_date` and `end_date` values in its messages.
