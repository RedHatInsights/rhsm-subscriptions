### 0001 - Use UMB Message End Date for Contract Termination

* **Status:** Accepted
* **Deciders:** @karshah
* **Date:** 2025-09-10

---

### Context

The SWATCH service needs to correctly terminate contracts based on `UNSUBSCRIBED` events received via the UMB message bus from the IT partner gateway. A key open point and challenge is determining the appropriate `end_date` for the contract record, as the `UNSUBSCRIBED` UMB message may not always provide this field directly, particularly for pay-as-you-go (PAYGO) contracts. This decision is critical for the test plan, implementation, and the accuracy of downstream services like Billable Usage. The service is not the source of truth for contract status, and must rely on messages from the IT partner gateway.

### Decision

We will use the end date provided in the UMB message as the `end_date` for contract termination. The UMB message should be treated as the source of truth for termination. If a `UNSUBSCRIBED` message is received for an existing contract, the `end_date` will be updated with the timestamp from the message. Ideally, the end date won’t be present for that contract. However, if an event is "UNSUBSCRIBED" this will function as an upsert, overwriting any previous termination date for that contract. In the scenario where a termination event is received for a non-existing contract, a new contract will be created in a terminated state, populated with the `end_date` from the message. This approach avoids impact to other services.

### Consequences

* **Positive:**
    * **Data Integrity:** The `end_date` in our system will always be based on the most recent, authoritative timestamp from the source, reducing data discrepancies.
    * **Simplicity:** The logic for termination is simplified by directly using the provided timestamp, avoiding complex assumptions or manual calculations.
    * **Robustness:** The system will gracefully handle termination events for both existing and non-existing contracts without causing errors or negatively impacting other services.
    * **Test Alignment:** This decision directly addresses the open points in our test plan and provides a clear path for unit and API level testing.

* **Negative:**
    * **Dependency:** This approach creates a strong dependency on the IT partner gateway to consistently provide an `end_date` within `UNSUBSCRIBED` messages.
    * **Workaround:** A temporary workaround may be required to handle messages with empty dimensions (which may represent pure PAYGO contracts) until the IT partner team logs and fixes the issue on their end.