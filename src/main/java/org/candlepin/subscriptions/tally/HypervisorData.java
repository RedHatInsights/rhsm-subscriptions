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
package org.candlepin.subscriptions.tally;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.candlepin.subscriptions.db.model.Host;
import org.candlepin.subscriptions.db.model.HostBucketKey;
import org.candlepin.subscriptions.db.model.HostTallyBucket;
import org.candlepin.subscriptions.tally.UsageCalculation.Key;
import org.candlepin.subscriptions.tally.collector.ProductUsageCollector;
import org.candlepin.subscriptions.tally.collector.ProductUsageCollectorFactory;
import org.candlepin.subscriptions.tally.facts.NormalizedFacts;

@Data
@NoArgsConstructor
public class HypervisorData {

  private Map<String, String> hypervisorMapping = new HashMap<>();
  private Map<String, Set<Key>> hypervisorUsageKeys = new HashMap<>();
  private Map<String, Map<String, NormalizedFacts>> orgHypervisorFacts = new HashMap<>();
  private Map<String, Host> hypervisorHosts = new HashMap<>();
  private Map<String, Integer> hypervisorGuestCounts = new HashMap<>();

  public void putMapping(String hypervisorUuid, String subscriptionManagerId) {
    hypervisorMapping.put(hypervisorUuid, subscriptionManagerId);
  }

  public boolean hasHypervisorUuid(String id) {
    return hypervisorMapping.containsKey(id);
  }

  public boolean isUnmappedHypervisor(String hypervisorUuid) {
    return !hypervisorMapping.containsKey(hypervisorUuid)
        || hypervisorMapping.get(hypervisorUuid) == null;
  }

  public void addUsageKey(String hypervisorUuid, UsageCalculation.Key key) {
    hypervisorUsageKeys.computeIfAbsent(hypervisorUuid, uuid -> new HashSet<>()).add(key);
  }

  public void incrementGuestCount(String hypervisorUuid) {
    Integer guests = hypervisorGuestCounts.getOrDefault(hypervisorUuid, 0);
    hypervisorGuestCounts.put(hypervisorUuid, ++guests);
  }

  public void addHypervisorFactsForOrg(String orgId, String hypervisorUuid, NormalizedFacts facts) {
    Map<String, NormalizedFacts> hypervisorFacts =
        orgHypervisorFacts.computeIfAbsent(orgId, a -> new HashMap<>());
    hypervisorFacts.put(hypervisorUuid, facts);
  }

  public void addHost(String hypervisorUuid, Host host) {
    hypervisorHosts.put(hypervisorUuid, host);
  }

  public void collectGuestData(
      Map<String, AccountUsageCalculation> calcsByOrgId,
      Map<String, Set<HostBucketKey>> hostBucketKeys) {
    orgHypervisorFacts.forEach(
        (orgId, uuidToHypervisor) -> {
          AccountUsageCalculation accountCalc = calcsByOrgId.get(orgId);
          uuidToHypervisor.forEach(
              (hypervisorUuid, hypervisor) ->
                  enhanceUsageKeys(orgId, accountCalc, hypervisorUuid, hypervisor, hostBucketKeys));
        });
  }

  private void enhanceUsageKeys(
      String orgId,
      AccountUsageCalculation accountCalc,
      String hypervisorUuid,
      NormalizedFacts hypervisor,
      Map<String, Set<HostBucketKey>> hostBucketKeys) {
    Host host = hypervisorHosts.get(hypervisorUuid);
    host.setNumOfGuests(hypervisorGuestCounts.getOrDefault(hypervisorUuid, 0));

    Set<HostBucketKey> bucketKeys =
        hostBucketKeys.computeIfAbsent(host.getInstanceId(), h -> new HashSet<>());
    Set<UsageCalculation.Key> usageKeys =
        hypervisorUsageKeys.getOrDefault(hypervisorUuid, Collections.emptySet());

    for (Key key : usageKeys) {
      UsageCalculation usageCalc = accountCalc.getOrCreateCalculation(key);
      ProductUsageCollector productUsageCollector =
          ProductUsageCollectorFactory.get(key.getProductId());
      Optional<HostTallyBucket> appliedBucket =
          productUsageCollector.collectForHypervisor(orgId, usageCalc, hypervisor);

      // addBucket changes bucket.key.hostId, so do that first to avoid mutating the item in the set
      appliedBucket.ifPresent(
          bucket -> {
            host.addBucket(bucket);
            bucketKeys.add(bucket.getKey());
          });
    }
  }
}
