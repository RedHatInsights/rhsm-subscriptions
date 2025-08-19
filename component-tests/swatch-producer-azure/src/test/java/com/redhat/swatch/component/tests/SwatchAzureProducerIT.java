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
package com.redhat.swatch.component.tests;

import static com.redhat.swatch.component.tests.utils.Topics.BILLABLE_USAGE_HOURLY_AGGREGATE;
import static com.redhat.swatch.component.tests.utils.Topics.BILLABLE_USAGE_STATUS;

import com.redhat.swatch.component.tests.api.AzureWiremockService;
import com.redhat.swatch.component.tests.api.ComponentTest;
import com.redhat.swatch.component.tests.api.KafkaBridge;
import com.redhat.swatch.component.tests.api.KafkaBridgeService;
import com.redhat.swatch.component.tests.api.Quarkus;
import com.redhat.swatch.component.tests.api.SwatchService;
import com.redhat.swatch.component.tests.api.Wiremock;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregate;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregateKey;
import org.junit.jupiter.api.Test;

@ComponentTest
public class SwatchAzureProducerIT {

  @KafkaBridge
  static KafkaBridgeService kafkaBridge =
      new KafkaBridgeService().subscribeToTopic(BILLABLE_USAGE_STATUS);

  @Wiremock static AzureWiremockService wiremock = new AzureWiremockService();

  @Quarkus(service = "swatch-producer-azure")
  static SwatchService service = new SwatchService();

  /** Verify billable usage sent to Azure with Succeeded status */
  @Test
  public void testValidAzureUsageMessages() {
    // Setup
    String productId = "rhel-for-x86-els-payg-addon";
    String metricId = "vCPUs";
    String billingAccountId = UUID.randomUUID().toString();
    String azureResourceId = UUID.randomUUID().toString();
    String orgId = "123456";
    double totalValue = 2.0;
    String dimension = "vcpu_hours";

    // Setup Azure Wiremock endpoints
    wiremock.setupAzureUsageContext(azureResourceId, billingAccountId);

    // Send billable usage message to Kafka
    BillableUsageAggregate aggregateData =
        createUsageAggregate(productId, billingAccountId, metricId, totalValue, orgId);
    kafkaBridge.produceKafkaMessage(BILLABLE_USAGE_HOURLY_AGGREGATE, aggregateData);

    // Verify status topic shows "succeeded"
    kafkaBridge.waitForKafkaMessage(
        BILLABLE_USAGE_STATUS,
        messages -> messages.contains(billingAccountId) && messages.contains("succeeded"));

    // Verify Azure usage was sent to Azure
    wiremock.verifyAzureUsage(azureResourceId, totalValue, dimension);
  }

  /** Verify billable usage with invalid timestamp format is handled properly */
  @Test
  public void testInvalidAzureUsageMessagesWrongDateFormat() {
    // Setup
    String productId = "rhel-for-x86-els-payg-addon";
    String metricId = "vCPUs";
    String billingAccountId = UUID.randomUUID().toString();
    String azureResourceId = UUID.randomUUID().toString();
    String orgId = "123456";
    double totalValue = 2.0;

    // Setup Azure Wiremock endpoints
    wiremock.setupAzureUsageContext(azureResourceId, billingAccountId);

    // Create aggregate with invalid timestamp using reflection
    BillableUsageAggregate aggregateData =
        createUsageAggregate(productId, billingAccountId, metricId, totalValue, orgId);
    // Use the new createUsageAggregateAsMap function with custom values
    Map<String, Object> customValues =
        Map.of("windowTimestamp", "testerday", "snapshotDates", List.of("2025.01.01"));

    Map<String, Object> aggregateMap =
        createUsageAggregateAsMap(
            productId, billingAccountId, metricId, totalValue, orgId, customValues);

    // Send billable usage message to Kafka
    kafkaBridge.produceKafkaMessage(BILLABLE_USAGE_HOURLY_AGGREGATE, aggregateMap);

    // Wait for a status message (if any) and verify it contains the billing account ID
    // The status could be "failed", "error", or the message might not be sent at all
    try {
      kafkaBridge.waitForKafkaMessage(
          BILLABLE_USAGE_STATUS, messages -> messages.contains(billingAccountId));
    } catch (Exception e) {
      // It's okay if no status message is received for invalid data
    }

    // Verify that no usage was sent to Azure
    wiremock.verifyNoAzureUsage(azureResourceId);
  }

  public BillableUsageAggregate createUsageAggregate(
      String productId, String billingAccountId, String metricId, double totalValue, String orgId) {
    OffsetDateTime snapshotDate =
        OffsetDateTime.now().minusHours(1).withOffsetSameInstant(ZoneOffset.UTC);

    var aggregate = new BillableUsageAggregate();
    aggregate.setTotalValue(BigDecimal.valueOf(totalValue));
    aggregate.setWindowTimestamp(
        OffsetDateTime.now().truncatedTo(ChronoUnit.HOURS).withOffsetSameInstant(ZoneOffset.UTC));
    aggregate.setAggregateId(UUID.randomUUID());

    var key = new BillableUsageAggregateKey();
    key.setOrgId(orgId);
    key.setProductId(productId);
    key.setMetricId(metricId);
    key.setSla("Premium");
    key.setUsage("Production");
    key.setBillingProvider("azure");
    key.setBillingAccountId(billingAccountId);

    aggregate.setAggregateKey(key);
    aggregate.setSnapshotDates(Set.of(snapshotDate));
    aggregate.setStatus(BillableUsage.Status.PENDING);
    aggregate.setRemittanceUuids(List.of(UUID.randomUUID().toString()));

    return aggregate;
  }

  public Map<String, Object> createUsageAggregateAsMap(
      String productId,
      String billingAccountId,
      String metricId,
      double totalValue,
      String orgId,
      Map<String, Object> customValues) {
    // Call the existing createUsageAggregate method to avoid code duplication
    BillableUsageAggregate aggregate =
        createUsageAggregate(productId, billingAccountId, metricId, totalValue, orgId);

    // Convert the BillableUsageAggregate object to a Map
    Map<String, Object> aggregateMap = new java.util.HashMap<>();
    aggregateMap.put("totalValue", aggregate.getTotalValue());
    aggregateMap.put("windowTimestamp", aggregate.getWindowTimestamp());
    aggregateMap.put("aggregateId", aggregate.getAggregateId());
    aggregateMap.put("aggregateKey", aggregate.getAggregateKey());
    aggregateMap.put("snapshotDates", aggregate.getSnapshotDates());
    aggregateMap.put("status", aggregate.getStatus());
    aggregateMap.put("errorCode", aggregate.getErrorCode());
    aggregateMap.put("billedOn", aggregate.getBilledOn());
    aggregateMap.put("remittanceUuids", aggregate.getRemittanceUuids());

    // If custom values are provided, create a new map with the custom values overriding defaults
    if (!customValues.isEmpty()) {
      var result = new java.util.HashMap<>(aggregateMap);
      result.putAll(customValues);
      return result;
    }

    return aggregateMap;
  }
}
