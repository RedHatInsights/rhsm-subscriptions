# **Feature: Terminating Contracts via UMB Messages**

The system should correctly terminate a contract, based on the `status` field in a received UMB message from the IT partner gateway, by setting its end date.

## **Test cases at unit level:**

* A UMB unsubscribe message should not be discarded.
* After an unsubscribe message is processed, the contract is updated populating the `end_date` field.

## **Test cases at API level:**

1. `contracts-termination-TC001` **A contract remains active after receiving a non-termination event.**
    1. ***Description:*** Verify that a UMB message with a non-terminating status (i.e., `"status": "SUBSCRIBED"`) does not cause a contract to be terminated.
    2. ***Setup:*** Ensure a contract exists and is currently in an active state.
    3. ***Action:*** Simulate a UMB message from the IT partner gateway for the active contract, with the field "status": "SUBSCRIBED".
    4. ***Verification:*** Check the contract using the GET API.
    5. ***Expected Result:***
        1. The end date is empty or higher than now.
2. `contracts-termination-TC002` **Terminate an existing and active contract.**
    1. ***Description:*** Verify that an active contract can be successfully terminated when a UMB message with a status: "UNSUBSCRIBED" is received.
    2. ***Setup:*** Ensure a contract exists and is currently in an active state.
    3. ***Action:*** Simulate a UMB message from the IT partner gateway for the active contract, with the field `"status": "UNSUBSCRIBED"`.
    4. ***Verification:*** Check the contract using the GET API.
    5. ***Expected Result:***
        1. The end date should be the one contained in the UMB message.
3. `contracts-termination-TC003` **Receive a termination event for an already terminated contract.**
    1. ***Description:*** Verify that receiving a UMB message with `"status": "UNSUBSCRIBED"` for a contract that is already in a terminated state does not cause an error or change its status.
    2. ***Setup:*** Ensure a contract exists and its status is already 'TERMINATED'.
    3. ***Action:*** Simulate a UMB message from the IT partner gateway for the terminated contract, with the field `"status": "UNSUBSCRIBED"`.
    4. ***Verification:*** Check the contract using the GET API.
    5. ***Expected Result:***
        1. No errors should be logged.
        2. The contract's status should remain “TERMINATED”.
        3. The end date is updated.
4. `contracts-termination-TC004` **Receive a termination event for a non-existing contract.**
    1. ***Description:*** Verify that a contract with a subscription\_number but no corresponding record in the Subscriptions table can be correctly terminated when a UMB message with a `"status": "UNSUBSCRIBED"` is received.
    2. ***Setup:*** Ensure a contract does not exist in the Contract table.
    3. ***Action:*** Simulate a UMB message from the IT partner gateway for the contract unexisting contract, and with the field `"status": "UNSUBSCRIBED"`.
    4. ***Verification:*** Check the contract using the GET API.
    5. ***Expected result:***
        1. The contract is created.
        2. Its end date is empty or higher than now.

## Testing strategy:

Test cases should be only testable locally and in an ephemeral environment.

* UMB messages can be injected.
* The persistence layer can be mocked.

There is no additional value in testing in an upper environment (i.e. stage). The method of contract termination should not influence downstream component testing. From an integration perspective, the crucial factor is whether the contract is terminated, not the specific means by which it occurs.
