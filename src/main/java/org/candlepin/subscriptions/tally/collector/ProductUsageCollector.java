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
package org.candlepin.subscriptions.tally.collector;

import java.util.Optional;
import org.candlepin.subscriptions.db.model.HostTallyBucket;
import org.candlepin.subscriptions.tally.UsageCalculation;
import org.candlepin.subscriptions.tally.facts.NormalizedFacts;

/** Given a host's facts, collects the usage tally for a product. */
public interface ProductUsageCollector {

  /**
   * Collect collect and append usage data to the provided product calculation based on the facts
   * for the given host.
   *
   * @param prodCalc the existing calculations for this product.
   * @param normalizedHostFacts the normalized view of the facts from inventory.
   * @return HostTallyBucket the bucket representing the counts applied by the specified host
   */
  Optional<HostTallyBucket> collect(UsageCalculation prodCalc, NormalizedFacts normalizedHostFacts);

  /**
   * Collect and append usage data based on hypervisor-guest mappings.
   *
   * @param orgId the org ID.
   * @param prodCalc which usage key's calculation to update
   * @param hypervisorFacts facts about the hypervisor
   * @return HostTallyBucket the bucket representing the counts applied by the specified host
   */
  Optional<HostTallyBucket> collectForHypervisor(
      String orgId, UsageCalculation prodCalc, NormalizedFacts hypervisorFacts);
}
