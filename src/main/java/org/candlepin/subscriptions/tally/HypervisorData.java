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
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.candlepin.subscriptions.db.model.Host;
import org.candlepin.subscriptions.db.model.HostBucketKey;
import org.candlepin.subscriptions.db.model.HostTallyBucket;
import org.candlepin.subscriptions.inventory.db.InventoryDatabaseOperations;
import org.candlepin.subscriptions.tally.UsageCalculation.Key;
import org.candlepin.subscriptions.tally.collector.ProductUsageCollector;
import org.candlepin.subscriptions.tally.collector.ProductUsageCollectorFactory;
import org.candlepin.subscriptions.tally.facts.NormalizedFacts;

@Data
@RequiredArgsConstructor
public class HypervisorData {

  @NonNull private final String orgId;

  private Map<String, String> hypervisorMapping = new HashMap<>();
  private Map<String, Set<Key>> hypervisorUsageKeys = new HashMap<>();
  private Map<String, NormalizedFacts> hypervisorFacts = new HashMap<>();
  private Map<String, Integer> hypervisorGuestCounts = new HashMap<>();

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  /* This map is the important end result of all the work this class does.  I want to give it a
  more semantically meaningful name than just getHypervisorHosts */
  Map<String, Host> hypervisorHosts = new HashMap<>();

  /* Obviously the caller could just call fetchReportedHypervisors themselves since they already
   * have to have an InventoryDatabaseOperations object, orgId, and HypervisorData object to make
   * this call.  But I'm adding this bit of indirection to make it exceedingly obvious in the
   * calling code that fetchReportedHypervisors is an operation that modifies the HypervisorData
   * object */
  public void addReportedHypervisors(InventoryDatabaseOperations inventory, String orgId) {
    inventory.fetchReportedHypervisors(orgId, this);
  }

  public void addHostMapping(String hypervisorUuid, String subscriptionManagerId) {
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

  public void addHypervisorFacts(String hypervisorUuid, NormalizedFacts facts) {
    hypervisorFacts.put(hypervisorUuid, facts);
  }

  public void addHost(String hypervisorUuid, Host host) {
    hypervisorHosts.put(hypervisorUuid, host);
  }

  public Map<String, Host> hostMap() {
    return hypervisorHosts;
  }

  public void collectGuestData(
      AccountUsageCalculation accountCalc, Map<String, Set<HostBucketKey>> hostBucketKeys) {
    hypervisorFacts.forEach(
        (hypervisorUuid, normalizedFacts) ->
            enhanceUsageKeys(orgId, accountCalc, hypervisorUuid, normalizedFacts, hostBucketKeys));
  }

  private void enhanceUsageKeys(
      String orgId,
      AccountUsageCalculation accountCalc,
      String hypervisorUuid,
      NormalizedFacts normalizedFacts,
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
          productUsageCollector.collectForHypervisor(orgId, usageCalc, normalizedFacts);

      // addBucket changes bucket.key.hostId, so do that first to avoid mutating the item in the set
      appliedBucket.ifPresent(
          bucket -> {
            host.addBucket(bucket);
            bucketKeys.add(bucket.getKey());
          });
    }
  }
}
