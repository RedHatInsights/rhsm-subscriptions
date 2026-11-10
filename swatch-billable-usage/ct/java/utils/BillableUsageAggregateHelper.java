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
package utils;

import static org.candlepin.subscriptions.billable.usage.BillableUsage.Sla.PREMIUM;
import static org.candlepin.subscriptions.billable.usage.BillableUsage.Usage.PRODUCTION;

import domain.BillingProvider;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregate;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregateKey;

public final class BillableUsageAggregateHelper {

  private BillableUsageAggregateHelper() {}

  public static BillableUsageAggregate buildBillableUsageAggregate(
      String orgId,
      String productId,
      String metricId,
      String billingAccountId,
      BillableUsage.Status status,
      BillableUsage.ErrorCode errorCode,
      OffsetDateTime billedOn,
      List<String> remittanceUuids,
      String licenseId) {
    var aggregateKey = new BillableUsageAggregateKey();
    aggregateKey.setOrgId(orgId);
    aggregateKey.setProductId(productId);
    aggregateKey.setMetricId(metricId);
    aggregateKey.setSla(PREMIUM.value());
    aggregateKey.setUsage(PRODUCTION.value());
    aggregateKey.setBillingProvider(BillingProvider.AWS.name());
    aggregateKey.setBillingAccountId(billingAccountId);

    var aggregate = new BillableUsageAggregate();
    aggregate.setAggregateKey(aggregateKey);
    aggregate.setStatus(status);
    aggregate.setRemittanceUuids(remittanceUuids);
    aggregate.setTotalValue(BigDecimal.valueOf(5.0));
    aggregate.setWindowTimestamp(OffsetDateTime.now(ZoneOffset.UTC).minusDays(3));
    aggregate.setAggregateId(UUID.randomUUID());
    Set<OffsetDateTime> snapshotDateSet = new HashSet<>();
    snapshotDateSet.add(OffsetDateTime.now(ZoneOffset.UTC).minusDays(3));
    aggregate.setSnapshotDates(snapshotDateSet);
    aggregate.setLicenseId(licenseId);
    if (errorCode != null) {
      aggregate.setErrorCode(errorCode);
    }
    if (billedOn != null) {
      aggregate.setBilledOn(billedOn);
    }
    return aggregate;
  }

  public static BillableUsageAggregate mergeHourlyAggregates(
      List<BillableUsageAggregate> aggregates, Set<String> expectedRemittanceUuids) {
    if (aggregates == null || aggregates.isEmpty()) {
      return null;
    }

    List<BillableUsageAggregate> relevant =
        aggregates.stream()
            .filter(a -> a.getRemittanceUuids() != null && !a.getRemittanceUuids().isEmpty())
            .filter(a -> expectedRemittanceUuids.containsAll(a.getRemittanceUuids()))
            .toList();

    for (BillableUsageAggregate aggregate : relevant) {
      if (new HashSet<>(aggregate.getRemittanceUuids()).containsAll(expectedRemittanceUuids)) {
        return aggregate;
      }
    }

    Set<String> seenRemittanceUuids = new LinkedHashSet<>();
    BigDecimal totalValue = BigDecimal.ZERO;
    String licenseId = null;
    BillableUsageAggregateKey aggregateKey = null;
    Set<OffsetDateTime> snapshotDates = new HashSet<>();
    OffsetDateTime windowTimestamp = null;

    for (BillableUsageAggregate partial : relevant) {
      Set<String> partialUuids = new LinkedHashSet<>(partial.getRemittanceUuids());
      if (partialUuids.stream().anyMatch(seenRemittanceUuids::contains)) {
        // Overlapping remittance sets cannot be summed safely; wait for a cleaner split.
        continue;
      }
      seenRemittanceUuids.addAll(partialUuids);
      if (partial.getTotalValue() != null) {
        totalValue = totalValue.add(partial.getTotalValue());
      }
      if (partial.getLicenseId() != null) {
        licenseId = partial.getLicenseId();
      }
      if (aggregateKey == null) {
        aggregateKey = partial.getAggregateKey();
      }
      if (partial.getSnapshotDates() != null) {
        snapshotDates.addAll(partial.getSnapshotDates());
      }
      if (windowTimestamp == null) {
        windowTimestamp = partial.getWindowTimestamp();
      }
    }

    if (!seenRemittanceUuids.containsAll(expectedRemittanceUuids)) {
      return null;
    }

    BillableUsageAggregate merged = new BillableUsageAggregate();
    merged.setAggregateKey(aggregateKey);
    merged.setTotalValue(totalValue);
    merged.setRemittanceUuids(new ArrayList<>(seenRemittanceUuids));
    merged.setLicenseId(licenseId);
    merged.setSnapshotDates(snapshotDates);
    merged.setWindowTimestamp(windowTimestamp);
    return merged;
  }
}
