# Stage Test Plan for Notification Service Integration
Intra-swatch service integration is already covered [here](https://gitlab.cee.redhat.com/insights-qe/iqe-rhsm-subscriptions-plugin/-/tree/master/iqe_rhsm_subscriptions/tests/integration/swatch_utilization). Lower level tests are already covered as component tests on Java.

This test plan will focus on testing the integration with notification service. Email template rendering and deduplication logic are owned and tested by notifications-backend (`SubscriptionsDeduplicationConfigTest`, `EventDeduplicatorTest`). Stage tests here cover end-to-end delivery only.

We decided to not implement the following test Ephemeral to get rid of most of the mechanisms that separate the target environment from production.

What will be covered here that cannot be covered by other lower level tests:

- Messages are received correctly by the notification service.
- Email is received with the email template correctly populated with "real data".

Assumptions:

- The organization under test is onboarded to swatch.
- The organization under test has instant notification enabled for Subscription Usage.
- The organization under test has a behavior group with email integration, capable of receiving emails.
- The over usage threshold is set and fixed (5%).
- A new organization is used every time, so we don't need to take into account race conditions and side effects.

Strategy:

- We achieve observability over the inbox check. This exposes us to potential flakiness, given the inbox neither the token to access it are under our control.
- We test with a new organization, created with Ethel API, on every run. "No prior notification" is granted by construction.
- The usage tally sync is triggered with the internal API.

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

## stage-custom-threshold-notification-receipt-TC001

1. **Description:** An opted-in organization with a custom threshold configured receives a proactive notification when utilization crosses that threshold. For this calendar month, no prior custom-threshold notification for the same product-metric has been sent.
2. **Setup:**
   - The organization is opted-in for notifications.
   - The organization has a custom threshold configured.
   - The organization has a contract for a product.
   - Usage is added for that product, crossing the custom threshold but not the over-usage threshold.
3. **Action:** Trigger the hourly tally sync job.
4. **Verification:** Check the organization's email inbox for the expected organization ID, product, metric, and exact usage percentage.
5. **Expected Result:** A custom-threshold notification with the correct data is received.
