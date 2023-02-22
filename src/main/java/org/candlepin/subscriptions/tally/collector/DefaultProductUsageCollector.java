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
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.HostTallyBucket;
import org.candlepin.subscriptions.tally.UsageCalculation;
import org.candlepin.subscriptions.tally.facts.NormalizedFacts;

/** The default product usage collection rules. */
public class DefaultProductUsageCollector implements ProductUsageCollector {

  @Override
  public void collect(UsageCalculation prodCalc, NormalizedFacts normalizedFacts) {
    int appliedCores = Optional.ofNullable(normalizedFacts.getCores()).orElse(0);
    int appliedSockets = Optional.ofNullable(normalizedFacts.getSockets()).orElse(0);

    if (normalizedFacts.getCloudProviderType() != null) {
      appliedSockets = normalizedFacts.isMarketplace() ? 0 : 1;
      prodCalc.addCloudProvider(
          normalizedFacts.getCloudProviderType(), appliedCores, appliedSockets, 1);
    }
    // Accumulate for physical systems.
    else if (!normalizedFacts.isVirtual()) {
      appliedSockets = normalizedFacts.isMarketplace() ? 0 : appliedSockets;
      prodCalc.addPhysical(appliedCores, appliedSockets, 1);
    }
    // Any other system is considered virtual
    else {
      if (normalizedFacts.isMarketplace()) {
        appliedSockets = 0;
      }
      prodCalc.addVirtual(appliedCores, appliedSockets, 1);
    }
  }

  @Override
  public Optional<HostTallyBucket> buildBucket(
      UsageCalculation.Key key, NormalizedFacts normalizedFacts) {
    int appliedCores = Optional.ofNullable(normalizedFacts.getCores()).orElse(0);
    int appliedSockets = Optional.ofNullable(normalizedFacts.getSockets()).orElse(0);

    HardwareMeasurementType appliedType = null;
    // Cloud provider hosts only account for a single socket.
    if (normalizedFacts.getCloudProviderType() != null) {
      appliedSockets = normalizedFacts.isMarketplace() ? 0 : 1;
      appliedType = normalizedFacts.getCloudProviderType();
    }
    // Accumulate for physical systems.
    else if (!normalizedFacts.isVirtual()) {
      appliedSockets = normalizedFacts.isMarketplace() ? 0 : appliedSockets;
      appliedType = HardwareMeasurementType.PHYSICAL;
    }
    // Any other system is considered virtual
    else {
      if (normalizedFacts.isMarketplace()) {
        appliedSockets = 0;
      }
      appliedType = HardwareMeasurementType.VIRTUAL;
    }

    HostTallyBucket appliedBucket =
        new HostTallyBucket(
            null,
            key.getProductId(),
            key.getSla(),
            key.getUsage(),
            key.getBillingProvider(),
            key.getBillingAccountId(),
            true,
            appliedCores,
            appliedSockets,
            appliedType);

    return Optional.of(appliedBucket);
  }

  @Override
  public Optional<HostTallyBucket> buildBucketForHypervisor(
      UsageCalculation.Key key, NormalizedFacts hypervisorFacts) {
    /* do nothing for hypervisor-guest mappings by default */
    return Optional.empty();
  }

  @Override
  public void collectForHypervisor(UsageCalculation prodCalc, NormalizedFacts hypervisorFacts) {
    // Intentionally left blank
  }
}
