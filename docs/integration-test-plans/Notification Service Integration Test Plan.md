# Stage Test Plan for Notification Service Integration
Intra-swatch service integration is already covered [here](https://gitlab.cee.redhat.com/insights-qe/iqe-rhsm-subscriptions-plugin/-/tree/master/iqe_rhsm_subscriptions/tests/integration/swatch_utilization). Lower level tests are already covered as component tests on Java.

This test plan will focus on testing the integration with notification service. Some of the areas covered by these tests are overlapping with some under notification ownership. For example email template and deduplication logic is thoroughly tested on notification backend side. But we could not have control over changes that could happen on both sides (swatch-utilization payload and how notification backend handles that).  
We want a happy path covered anyway. The trade off between value brought and maintainability/sustainability, given the low number of test cases, is strongly in favor of value.  
We decided to not implement the following test Ephemeral to get rid of most of the mechanisms that separate the target environment from production. 

What will be covered here that cannot be covered by other lower level tests:

- Messages are received correctly by the notification service.  
- Email is received with the email template correctly populated with "real data".  
- The payload sent is coherent with what the deduplication logic expects.

Assumptions:

- The organization under test is onboarded to swatch.  
- The organization under test has instant notification enabled for Subscription Usage.  
- The organization under test has a behavior group with email integration, capable of receiving emails.  
- The over usage threshold is set and fixed (5%).  
- A new organization is used every time, so we don't need to take into account race conditions and side effects.

Strategy:

- We achieve observability over the inbox check. This exposes us to potential flakiness, given the inbox neither the token to access it are under our control.  
- We test with a new organization, created with Ethel API, on every run. This will overcome deduplication logic. Also "no prior notification" is granted by construction.  
- The usage tally sync is triggered with the internal API.

Limitation:

- Deduplication logic is also based on the **event timestamp**. This is hard to control during Stage tests, so we will trust the implementation (it is using [basic timestamp extraction](http://github.com/gwenneg/notifications-backend/blob/master/engine/src/main/java/com/redhat/cloud/notifications/events/deduplication/SubscriptionsDeduplicationConfig.java#L44)).  
- stage-notification-deduplication-TC001 could fail if the month rolls over between the 2 notifications.  
- The first test is redundant because it overlaps with the setup of the second one. But in case of malfunctioning we would see the precondition fail, not a stand-alone test.

## stage-notification-receipt-TC001

1. **Description:** An opted-in organization that is using more than the threshold for a metric of a product receives a notification. For this calendar month, no prior notifications for the same combination product-metric has been sent.  
2. **Setup:**  
   - The organization is opted-in for notifications.  
   - The organization has a contract for a product.  
   - Usage is added for that product, crossing the threshold.  
   - The organization didn't receive a notification for that product-metric in this calendar month.  
3. **Action:** Trigger the hourly tally sync job.  
4. **Verification:** Check the organization's email inbox for the expected:  
   - Organization ID  
   - Product  
   - Exact usage percentage
   - Absence of SLA and Usage
5. **Expected Result:** A notification for the over usage with the correct data is received.

## stage-notification-deduplication-TC001

1. **Description:** An opted-in organization that is using more than the threshold for a metric of a product doesn't receive a notification if, for this calendar month, a prior notification for the same combination product-metric has already been sent.  
2. **Setup:**  
- The organization is opted-in for notifications.  
- The organization has a contract for a product.  
- Usage is added for that product, crossing the threshold.  
- The organization correctly received a notification for the over usage in this calendar month.  
3. **Action:** Trigger the daily tally sync job.  
4. **Verification:** Check the organization's email inbox.  
   * To verify that the notification service is not down after the setup (that would result in the second assertion always passing), a new over usage condition can be injected and finally asserted.   
5. **Expected Result:** No new notification for the same over usage is received.


## stage-notification-deduplication-TC002

1. **Description:** Exercises the **SLA** and **usage** elements of the deduplication key. Uses a non-PAYG product with separate subscriptions per SLA/usage combination so each has its own capacity row. The metric dimension is not exercised here.
2. **Setup:**
   - The organization is opted-in for notifications.
   - Separate subscriptions are added for each SLA/usage combination under test, so each has its own capacity row.
   - Hosts are registered via Candlepin and synced via conduit to produce usage above threshold.
3. **Actions and Verifications (in sequence):**
   1. Inject baseline hosts for one SLA/usage combo → sync tally → verify first email received.
   2. Sync tally again (identical combo) → assert **exactly 1 email** (baseline deduplication).
   3. Inject hosts for a different SLA → sync → verify **new email** (SLA is deduplication key).
   4. Inject hosts for a different usage → sync → verify **new email** (usage is deduplication key).
4. **Expected Result:**
   - Deduplication fires for identical `product + metric + SLA + usage` combo.
   - A new notification is produced when SLA changes and when usage changes, confirming both are part of the deduplication key.

## test_over_usage_notification_requires_payg_sla_aligned_with_offering

1. **Description:** Over-usage notifications for PAYG products require that the SLA of the PAYG usage aligns with the contract offering's SLA. Usage at a mismatched SLA tier must not trigger an alert, even if the volume exceeds capacity.
2. **Setup:**
   - A contract exists for a PAYG product (e.g., ansible-aap-managed on AWS) with capacity tied to a specific SLA (Premium).
3. **Actions and Verifications (in sequence):**
   1. Emit PAYG usage at a mismatched SLA (Standard) that exceeds contract capacity → trigger hourly tally sync → verify **no** over-usage notification for Standard SLA.
   2. Emit PAYG usage at the aligned SLA (Premium) that exceeds contract capacity → trigger hourly tally sync → verify over-usage notification is sent for Premium SLA with the correct utilization percentage.
4. **Expected Result:**
   - No notification is emitted when the PAYG SLA does not match the offering.
   - A notification is emitted when the PAYG SLA matches the offering and usage exceeds the threshold.

## test_over_usage_notification_requires_payg_usage_aligned_with_offering

1. **Description:** Over-usage notifications for PAYG products require that the syspurpose usage type of the PAYG usage aligns with the contract offering's usage type. Usage at a mismatched usage type must not trigger an alert, even if the volume exceeds capacity.
2. **Setup:**
   - The organization is opted-in for notifications.
   - A contract exists for a PAYG product (e.g., rhel-for-x86-els-payg on AWS) with capacity tied to a specific usage type (Production).
3. **Actions and Verifications (in sequence):**
   1. Emit PAYG usage at a mismatched usage type (Development/Test) that exceeds contract capacity → trigger hourly tally sync → verify **no** over-usage notification for Development/Test.
   2. Emit PAYG usage at the aligned usage type (Production) that exceeds contract capacity → trigger hourly tally sync → verify over-usage notification is sent for Production with the correct utilization percentage.
4. **Expected Result:**
   - No notification is emitted when the PAYG usage type does not match the offering.
   - A notification is emitted when the PAYG usage type matches the offering and usage exceeds the threshold.
