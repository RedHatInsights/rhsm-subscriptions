# swatch-utilization

swatch-utilization is a service deployed within the Subscription Watch ecosystem that monitors
customer usage against their contracted capacity and sends notifications when usage exceeds
predefined thresholds. The service helps customers proactively manage their subscriptions by
alerting them when they are approaching or exceeding their purchased capacity.

The service receives utilization summaries from swatch-contracts via Kafka, validates the data,
and checks if current usage exceeds the contracted capacity by more than a configured threshold.
When over-usage is detected, the service increments monitoring metrics and sends notifications to
customers through the Red Hat platform notifications service.

An incoming utilization summary contains current usage measurements and capacity information for
a specific organization, product, and metric. The service compares current total usage against
capacity to calculate utilization percentage and determine if the customer is over their threshold.

An outgoing notification message will contain:
  - Organization ID for targeting the notification
  - Product ID and metric ID identifying what exceeded the threshold
  - Utilization percentage showing how much capacity is being used
  - Event metadata for the notifications service to format and deliver the alert

In short, swatch-utilization acts as an early warning system that helps customers avoid unexpected
capacity issues by proactively alerting them when their usage patterns indicate they may need to
purchase additional capacity or optimize their resource consumption.

## Utilization Monitoring

### Utilization Summary Consumer
The Utilization Summary Consumer is the main entry point into this service. It is a Kafka consumer
that consumes utilization summary messages from the utilization topic
(platform.rhsm-subscriptions.utilization-summary). These summaries are produced by swatch-contracts
after aggregating subscription capacity and current usage data.

#### Utilization Summary Processing
When a utilization summary is received:
1. The service validates the payload structure and ensures it contains valid measurements.
2. For each measurement in the summary, the service validates:
   - The metric ID is recognized and valid
   - Capacity is a positive value (unlimited capacity measurements are skipped)
   - Current total usage is available
3. The service calculates the utilization percentage:
   - `utilization_percent = (current_total / capacity) * 100`
4. The service determines the applicable threshold for the product:
   - First checks product-specific threshold from product configuration
   - Falls back to system default threshold (CUSTOMER_OVER_USAGE_DEFAULT_THRESHOLD_PERCENT, default 5%)
   - Negative thresholds disable over-usage detection for that product
5. The service calculates the overage percentage:
   - `overage_percent = utilization_percent - 100`
6. If overage exceeds the threshold:
   - Logs over-usage detection with full details
   - Increments monitoring counter for alerting and dashboards
   - Sends notification to the platform notifications service (if feature flag is enabled)

### Over-Usage Detection Logic
The service implements threshold-based over-usage detection:
- **Normal Usage**: utilization <= 100% (usage within capacity) - no alert
- **Slight Over-Usage**: 100% < utilization <= (100 + threshold)% - no alert (within tolerance)
- **Significant Over-Usage**: utilization > (100 + threshold)% - alert triggered

For example, with a 5% threshold:
- 95% utilization: No alert (under capacity)
- 103% utilization: No alert (over capacity but within 5% threshold)
- 107% utilization: Alert triggered (exceeds capacity by more than 5%)

This threshold mechanism prevents alert fatigue from minor capacity overages while ensuring
customers are notified of significant over-usage situations.

### Threshold Configuration
The service supports flexible threshold configuration at multiple levels:

**Product-Specific Thresholds**: Configured in the swatch-product-configuration library, each
product can define its own over-usage threshold percentage. This allows different products to have
different tolerances based on their pricing models and usage patterns.

**System Default Threshold**: When a product doesn't specify a threshold, the service uses the
system default configured via the CUSTOMER_OVER_USAGE_DEFAULT_THRESHOLD_PERCENT environment
variable (defaults to 5%).

**Disabling Detection**: Setting a product's threshold to a negative value (e.g., -1) disables
over-usage detection entirely for that product. This is useful for products that shouldn't generate
over-usage alerts.

### Granularity Filtering
The service processes utilization summaries regardless of their granularity (HOURLY, DAILY, MONTHLY).
All granularities are checked for over-usage, allowing the service to detect capacity issues at
different time scales.

### Notification Integration
When over-usage is detected, the service sends notifications through the Red Hat platform
notifications service:

**Notification Format**: The service creates an Action message containing:
- **Bundle**: "subscription-services" - identifies the service category
- **Application**: "subscriptions" - identifies the specific application
- **Event Type**: "exceeded-utilization-threshold" - identifies the alert type
- **Organization ID**: targets the notification to the correct customer
- **Context**: includes product ID and metric ID for reference
- **Payload**: includes the utilization percentage for display in the notification
- **Recipients**: configured to send to all users (not just admins) respecting user preferences

**Feature Flag Control**: Notifications can be disabled via the SEND_NOTIFICATIONS feature flag,
allowing the service to run in metrics-only mode for testing or gradual rollout.

**Notification Delivery**: The platform notifications service handles:
- Formatting the notification message using templates
- Determining delivery channels (email, console notifications, etc.)
- Respecting user notification preferences
- Managing notification history and acknowledgments

### Validation
The service performs comprehensive validation on incoming utilization summaries:

**Payload Validation**:
- Organization ID is present
- Product ID is present and recognized
- Granularity is specified
- Snapshot date is present
- At least one measurement exists

**Measurement Validation**:
- Metric ID is present and valid
- Capacity is present (null capacity is invalid)
- Current total usage is present
- For over-usage checks: capacity must be positive and not unlimited

Invalid summaries or measurements are logged and skipped without processing.

### Monitoring and Metrics
The service exposes several metrics for monitoring and alerting:

**swatch_utilization_over_usage**: Counter tracking detected over-usage events, tagged by:
- `product`: Product ID
- `metric_id`: Metric ID
- `billing`: Billing provider (if present)

These metrics enable:
- Dashboards showing utilization monitoring coverage
- Alerts when over-usage is detected frequently
- Trend analysis of customer usage patterns
- SLO tracking for notification delivery
