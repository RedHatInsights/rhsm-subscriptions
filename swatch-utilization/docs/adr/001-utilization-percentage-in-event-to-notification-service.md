# ADR-0001: Utilization Percentage in Event to Notification Service

* **Status:** Accepted
* **Deciders:** @jprevatt (Docs), @tlencion (Eng)
* **Date:** 2025-10-17

---

## Context

Notifications sent through the Notification Service must effectively communicate resource utilization overage to users. The initial design conveyed only the "threshold percentage" (the percentage above 100% usage for resources in overage), such as `5%` for a resource that is 105% utilized.

However, this approach presents communication challenges:
- Users may misinterpret threshold-only percentages as total utilization.
- The overage amount alone lacks context about the actual resource consumption level.
- Clear, unambiguous communication is essential for effective resource management.

## Decision

**We will include the complete resource utilization percentage in notification events.**

The full percentage value (`100% + overage`) must be calculated and passed to the Notification Service in the event payload. This approach ensures notifications display the total utilization rather than just the overage amount.

**Example:** If a resource has 7% overage (107% total utilization), the notification message will display `107%` instead of `7%`.

## Consequences

### Positive
* **Enhanced clarity:** Displaying total utilization percentage (e.g., 107%) provides immediate, unambiguous understanding compared to threshold-only values (e.g., 7%).
* **Better user experience:** Users can quickly assess the full scope of resource consumption without mental calculation.
* **Reduced confusion:** Eliminates potential misinterpretation of percentage values in notifications.

### Negative
* **Template limitations:** Notification templates cannot perform the `100% + threshold` calculation dynamically, requiring pre-calculated values in the event payload.
* **Backend processing overhead:** The complete utilization percentage must be calculated within the swatch-utilization service before event generation.
