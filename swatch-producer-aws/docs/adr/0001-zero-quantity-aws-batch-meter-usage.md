### 0001 - Zero-quantity AWS BatchMeterUsage

* **Status:** Accepted
* **Date:** 2026-08-24

---

### Context

AWS Marketplace metering allows zero quantity. `UsageRecord.Quantity` is optional, minimum 0, and defaults to 0 if omitted.

AWS Marketplace **seller guidance** recommends metering on an hourly basis for all customers. When there is nothing to report for an hour, sellers may submit **quantity 0**. Stated reasons include:

- Buyer visibility of usage and cost in AWS Billing during the cycle
- Reducing the risk of falling more than one hour behind if there is an outage
- For SaaS **subscription** pricing, a one-hour window after `unsubscribe-pending` to submit unreported usage before `unsubscribe-success`

### Decision

**SWATCH adheres to AWS Marketplace seller guidance for hourly metering, including `BatchMeterUsage` with `Quantity=0` when an hourly aggregate has nothing billable to report.**

`swatch-producer-aws` continues to send zero-quantity records for eligible aggregates rather than skipping the API call when `totalValue` is 0.

### Consequences

#### Positive

- Matches AWS documented practice for staying current and giving buyers hourly visibility.
- Preserves the unsubscribe-pending window behavior AWS describes for subscription products.
- Avoids a SWATCH-specific exception to documented marketplace metering.

#### Negative

- Steady-state hourly API traffic for idle customers with zero usage in a given hour.
- Metering records must use entitled ExternallyMetered dimensions or AWS may reject the call.

### References

- [UsageRecord](https://docs.aws.amazon.com/marketplace/latest/APIReference/API_marketplace-metering_UsageRecord.html)
- [BatchMeterUsage](https://docs.aws.amazon.com/marketplacemetering/latest/APIReference/API_BatchMeterUsage.html)
- [Configuring metering for usage (Meter on an hourly basis)](https://docs.aws.amazon.com/marketplace/latest/userguide/metering-for-usage.html)
- [SaaS subscription integration (Scenario: Meter usage)](https://docs.aws.amazon.com/marketplace/latest/userguide/saas-integrate-subscription.html)
- [SaaS contract with consumption integration (Scenario: Meter usage)](https://docs.aws.amazon.com/marketplace/latest/userguide/saas-integrate-contract-with-pay.html)
