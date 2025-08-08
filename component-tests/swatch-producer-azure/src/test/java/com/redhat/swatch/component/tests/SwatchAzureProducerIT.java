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
}
