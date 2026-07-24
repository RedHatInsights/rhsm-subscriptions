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
package org.candlepin.subscriptions.util;

import com.redhat.swatch.configuration.registry.ProductId;
import java.util.ArrayList;
import java.util.List;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.HostBucketKey;
import org.candlepin.subscriptions.db.model.HostTallyBucket;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;

public class TallyHostBucketFactory {
  /**
   * Creates bucket tuples using the cartesian product pattern. Automatically creates buckets with
   * the provided values AND _ANY variants (matching production tally behavior). Uses a Set to
   * deduplicate when input values are already _ANY.
   *
   * @param productId the product ID for the buckets
   * @param sla the SLA value (will create buckets with this value and _ANY)
   * @param usage the usage value (will create buckets with this value and _ANY)
   * @param billingProvider the billing provider (will create buckets with this value and _ANY)
   * @param billingAccountId the billing account ID (will create buckets with this value and _ANY if
   *     not null/blank)
   * @param sockets the number of sockets
   * @param cores the number of cores
   * @return list of HostTallyBucket instances representing all dimension combinations
   */
  public static List<HostTallyBucket> createBucketTuples(
      ProductId productId,
      HardwareMeasurementType hmt,
      ServiceLevel sla,
      Usage usage,
      BillingProvider billingProvider,
      String billingAccountId,
      double sockets,
      double cores) {

    // Use Sets to automatically handle deduplication when values are already _ANY
    // e.g., if sla = _ANY, Set.of(_ANY, _ANY) becomes Set.of(_ANY) with 1 element
    var slas = new java.util.LinkedHashSet<>(List.of(sla, ServiceLevel._ANY));
    var usages = new java.util.LinkedHashSet<>(List.of(usage, Usage._ANY));
    var providers = new java.util.LinkedHashSet<>(List.of(billingProvider, BillingProvider._ANY));

    // Billing account ID handling matches production: always include _ANY, conditionally add actual
    // See: MetricUsageCollector.getBillingAccountIds()
    var accountIds = new java.util.LinkedHashSet<String>();
    accountIds.add("_ANY");
    if (billingAccountId != null && !billingAccountId.isBlank()) {
      accountIds.add(billingAccountId);
    }

    List<HostTallyBucket> buckets = new ArrayList<>();

    // Create cartesian product of all dimensions
    for (ServiceLevel slaValue : slas) {
      for (Usage usageValue : usages) {
        for (BillingProvider providerValue : providers) {
          for (String accountIdValue : accountIds) {
            HostTallyBucket bucket = new HostTallyBucket();
            bucket.setKey(
                new HostBucketKey(
                    null, // host ID - will be set when added to host
                    productId.toString(),
                    slaValue,
                    usageValue,
                    providerValue,
                    accountIdValue,
                    false)); // asHypervisor
            bucket.setSockets((int) sockets);
            bucket.setCores((int) cores);
            bucket.setMeasurementType(hmt);
            bucket.setPrimary(PrimaryRecordUtils.isPrimaryRecord(bucket));
            buckets.add(bucket);
          }
        }
      }
    }

    return buckets;
  }
}
