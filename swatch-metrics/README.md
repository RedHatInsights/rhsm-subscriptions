# swatch-metrics

## Running in "default" mode

Without additional configuration values, swatch-metrics will use "telemeter" as the EVENT_SOURCE, which indicates metrics are sourced from the OpenShift Telemeter observatorium instance.

```
EVENT_SOURCE=telemeter PROM_URL="http://localhost:8082/api/v1" ./gradlew :swatch-metrics:quarkusDev
```

## Running in "rhelemeter" mode

```
EVENT_SOURCE=rhelemeter PROM_URL="http://localhost:8082/api/v1" ./gradlew :swatch-metrics:quarkusDev
```

## PromQL Templates

The [application.yaml](./src/main/resources/application.yaml) file defines templates for promql queries used when fetching metrics from `PROM_URL` under the `rhsm-subscriptions.metering.prometheus.metric` section.

### accountQueryTemplates vs queryTemplates

**accountQueryTemplates** are used to determine which org ids have metrics for the timeframe we care about for a product we care about.

**queryTemplates** are used to fetch the actual metrics and values we care about, filtered by a bunch of criteria.

Parameterized values come from [swatch-product-configuration](../swatch-product-configuration/src/main/resources/subscription_configs) yaml files.

### PromQL breakdown

#### queryTemplates.rhelemeter

Example
```promql
sum_over_time((max by (_id) (system_cpu_logical_count))[1h:10m]) / scalar(count_over_time(vector(1)[1h:10m]))
* on (_id) group_right topk by (_id) (
  1,
    group without (swatch_placeholder_label) (
      min_over_time(
      system_cpu_logical_count{
      product=~".*(^|,)(69)($|,).*",
      external_organization="11789772",
      billing_model="marketplace",
      support=~"Premium|Standard|Self-Support|None|"
      }[1h]
    )
  )
)
```

It's easiest to think about this query by breaking it in half at the join.  The left side provides us with the usage values we care about, and the right side provides us with the associated metadata labels we care about while tallying.  

## "Left Side" of the join

It calculates the maximum sum of `system_cpu_logical_count` over 1-hour windows (shifted by 10 minutes) for each `_id`, and normalizes this value by the number of 10-minute intervals in an hour.  This is a way to find the maximum average `system_cpu_logical_count` per 10-minute interval for each `_id` in the last hour.

### Numerator

`sum_over_time((max by (_id) (system_cpu_logical_count))[1h:10m])`

1. **`max by (_id) (system_cpu_logical_count)`:**
    - This gets all the `system_cpu_logical_count` for all the `_id` and does a max on it for overlapping 1-hour windows.

2. **`sum_over_time (...[1h:10m])`:**
    - This sums the result of above metric over a 1-hr window, using data sampled every 10 minutes.

### Denominator: `scalar(count_over_time(vector(1)[1h:10m]))`

1. **`vector(1)`:**
    - This creates a constant vector with the value 1.

2. **`count_over_time(vector(1)[1h:10m])`:**
    - Counts how many samples are in each 10-minute interval over the last hour.
    - Since `vector(1)` is a constant value, this effectively counts the number of 10-minute intervals in an hour.

3. **`scalar(...)`:**
    - Converts the count, which is a vector, into a scalar (a single numeric value).

## "Right Side" of the join
- The query retrieves the minimum value of `system_cpu_logical_count`, with the specified filters, over a 1-hour period for each `_id`, then selects the top result for each `_id`.
- This identifies the `_id` with the lowest `system_cpu_logical_count` under the specified filters conditions.

### `topk` Function

**`topk by (_id) (1, ...)`**
- The `topk` function is used to return the top `k` elements for each group. In this case, `k` is set to 1, which means it returns the highest value for each group specified by `_id`.

## Grouping and Aggregation

- **Grouping Modifier:** `group without (swatch_placeholder_label)`
    - We need to `group` here to prevent multiplying metric values during the join.
    - The `without` modifier indicates that the grouping should be done on all labels except for `swatch_placeholder_label`.
    - The label `swatch_placeholder_label` doesn't actually exist, so it doesn't actually DO anything.  If it did, it would remove the `swatch_placeholder_label` label from consideration in the grouping process.
    - All we're using it for here is to avoid having to explicitly list out every label that we care about in a `by (_id,...)` clause with the `group`.

## `min_over_time` Function
- This calculates the minimum value of the `system_cpu_logical_count` metric over a 1-hour window (`[1h]`).

## Metric Filtering

- The metric `system_cpu_logical_count` is filtered based on these labels:
    1. **`product`**
    2. **`external_organization`**
    3. **`billing_model`**
    4. **`support`**