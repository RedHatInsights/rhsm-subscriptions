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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.tally.UsageCalculation;
import org.candlepin.subscriptions.tally.facts.NormalizedFacts;
import org.junit.jupiter.api.Test;

class DefaultProductUsageCollectorTest {

  private final ProductUsageCollector collector = new DefaultProductUsageCollector();

  @Test
  void testBucketsHaveAsHypervisorFalse() {
    var facts = new NormalizedFacts();
    var bucket = collector.buildBucket(createUsageKey(), facts);
    assertTrue(bucket.isPresent());
    assertFalse(bucket.get().getKey().getAsHypervisor());
  }

  private UsageCalculation.Key createUsageKey() {
    return new UsageCalculation.Key(
        "NON_RHEL", ServiceLevel.EMPTY, Usage.EMPTY, BillingProvider.EMPTY, "_ANY");
  }
}
