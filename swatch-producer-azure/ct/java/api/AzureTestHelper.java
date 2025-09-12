package api;

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

public final class AzureTestHelper {

  private AzureTestHelper() {}

  public static BillableUsageAggregate createUsageAggregate(
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
