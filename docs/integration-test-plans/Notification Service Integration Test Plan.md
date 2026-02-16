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
3. **Action:** Trigger the daily tally sync job.  
4. **Verification:** Check the organization's email inbox for the expected:  
   - Organization ID  
   - Product  
   - Exact usage percentage  
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
