/*
 * Copyright Red Hat, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Red Hat trademarks are not licensed under GPLv3. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class PrometheusMetricData {

  private final String instanceId;
  private final String orgId;
  private final String sla;
  private final String usage;
  private final String billingProvider;
  private final String billingAccountId;
  private final String displayName;
  private final String product;
  private final Boolean conversionsSuccess;
  private final String accountNumber;
  @Builder.Default private final List<TimeValuePair> values = new ArrayList<>();

  public Map<String, Object> toResultMap() {
    Map<String, Object> result = new HashMap<>();

    Map<String, String> metric = new HashMap<>();
    metric.put("billing_marketplace_instance_id", instanceId);
    metric.put("support", sla);
    metric.put("usage", usage);
    metric.put("external_organization", orgId);
    metric.put("billing_marketplace", billingProvider);
    metric.put("billing_marketplace_account", billingAccountId);
    metric.put("billing_model", "marketplace");
    metric.put("display_name", displayName);
    metric.put("product", product);
    metric.put("conversions_success", String.valueOf(conversionsSuccess));

    result.put("metric", metric);
    result.put("values", values.stream().map(TimeValuePair::toList).toList());

    return result;
  }

  public String toOpenMetrics(String clusterId, String metricName) {
    StringBuilder sb = new StringBuilder();

    for (TimeValuePair value : values) {
      long timestampSeconds = (long) value.getTimestamp();

      // Write metadata metric (e.g., system_cpu_logical_count for RHEL PAYG)
      // Format matches Python implementation in prometheus_importer.py lines 196-204
      sb.append("system_cpu_logical_count{");
      sb.append("_id=\"").append(clusterId).append("\",");
      sb.append("billing_model=\"marketplace\",");
      sb.append("ebs_account=\"").append(accountNumber != null ? accountNumber : "").append("\",");
      sb.append("display_name=\"").append(displayName).append("\",");
      sb.append("external_organization=\"").append(orgId).append("\",");
      sb.append("support=\"").append(sla).append("\",");
      sb.append("billing_marketplace=\"").append(billingProvider).append("\",");
      sb.append("billing_marketplace_instance_id=\"").append(instanceId).append("\",");
      sb.append("conversions_success=\"").append(conversionsSuccess).append("\",");
      sb.append("billing_marketplace_account=\"").append(billingAccountId).append("\",");
      sb.append("product=\"").append(product).append("\",");
      sb.append("metered_by_rh=\"true\"");
      sb.append("} 1.0 ").append(timestampSeconds).append("\n");

      // Write the actual metric value (e.g., subscription_labels_vcpus) - Python line 211-212
      sb.append(metricName).append("{");
      sb.append("_id=\"").append(clusterId).append("\"");
      sb.append("} ").append(value.getValue()).append(" ").append(timestampSeconds).append("\n");
    }

    sb.append("# EOF\n");
    return sb.toString();
  }

  public String toPrometheusFormat(String clusterId, String metricName) {
    StringBuilder sb = new StringBuilder();

    for (TimeValuePair value : values) {
      // Prometheus format uses milliseconds (OpenMetrics uses seconds)
      long timestampMillis = (long) (value.getTimestamp() * 1000);

      // Write metadata metric (e.g., system_cpu_logical_count for RHEL PAYG)
      sb.append("system_cpu_logical_count{");
      sb.append("_id=\"").append(clusterId).append("\",");
      sb.append("billing_model=\"marketplace\",");
      sb.append("ebs_account=\"").append(accountNumber != null ? accountNumber : "").append("\",");
      sb.append("display_name=\"").append(displayName).append("\",");
      sb.append("external_organization=\"").append(orgId).append("\",");
      sb.append("support=\"").append(sla).append("\",");
      sb.append("billing_marketplace=\"").append(billingProvider).append("\",");
      sb.append("billing_marketplace_instance_id=\"").append(instanceId).append("\",");
      sb.append("conversions_success=\"").append(conversionsSuccess).append("\",");
      sb.append("billing_marketplace_account=\"").append(billingAccountId).append("\",");
      sb.append("product=\"").append(product).append("\",");
      sb.append("metered_by_rh=\"true\"");
      sb.append("} 1.0 ").append(timestampMillis).append("\n");

      // Write the actual metric value (e.g., subscription_labels_vcpus)
      sb.append(metricName).append("{");
      sb.append("_id=\"").append(clusterId).append("\"");
      sb.append("} ").append(value.getValue()).append(" ").append(timestampMillis).append("\n");
    }

    return sb.toString();
  }

  public static PrometheusMetricData buildRhelPaygAddonMetric(
      String orgId,
      String instanceId,
      String billingProvider,
      String billingAccountId,
      double metricValue) {
    // Generate data going backward in time from now
    // This matches what the metering service expects (recent historical data)
    long now = System.currentTimeMillis() / 1000;
    List<TimeValuePair> dataPoints = new ArrayList<>();

    // Generate data points from 1 hour ago up to now, with 1-minute intervals
    // This ensures the metering service will find the data in its query range
    for (int i = 60; i >= 0; i--) {
      long timestamp = now - (i * 60); // Go back i minutes from now
      dataPoints.add(new TimeValuePair(timestamp, metricValue));
    }

    return PrometheusMetricData.builder()
        .orgId(orgId)
        .instanceId(instanceId)
        .sla("Premium")
        .usage("Production")
        .billingProvider(billingProvider)
        .billingAccountId(billingAccountId)
        .accountNumber(null) // Can be set if needed
        .displayName(instanceId + "-display")
        .product("204")
        .conversionsSuccess(false)
        .values(dataPoints)
        .build();
  }

  @Getter
  public static class TimeValuePair {
    private final double timestamp;
    private final double value;

    public TimeValuePair(double timestamp, double value) {
      this.timestamp = timestamp;
      this.value = value;
    }

    public List<Double> toList() {
      return List.of(timestamp, value);
    }
  }
}
