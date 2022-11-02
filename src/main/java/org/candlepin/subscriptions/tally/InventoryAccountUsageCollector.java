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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.db.AccountServiceInventoryRepository;
import org.candlepin.subscriptions.db.model.*;
import org.candlepin.subscriptions.inventory.db.InventoryDatabaseOperations;
import org.candlepin.subscriptions.inventory.db.model.InventoryHostFacts;
import org.candlepin.subscriptions.json.Measurement;
import org.candlepin.subscriptions.tally.UsageCalculation.Key;
import org.candlepin.subscriptions.tally.collector.ProductUsageCollector;
import org.candlepin.subscriptions.tally.collector.ProductUsageCollectorFactory;
import org.candlepin.subscriptions.tally.facts.FactNormalizer;
import org.candlepin.subscriptions.tally.facts.NormalizedFacts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Collects the max values from all accounts in the inventory. */
@Component
public class InventoryAccountUsageCollector {

  private static final Logger log = LoggerFactory.getLogger(InventoryAccountUsageCollector.class);
  public static final String HBI_INSTANCE_TYPE = "HBI_HOST";

  private final FactNormalizer factNormalizer;
  private final InventoryDatabaseOperations inventory;
  private final AccountServiceInventoryRepository accountServiceInventoryRepository;
  private final int culledOffsetDays;
  private final Counter totalHosts;

  public InventoryAccountUsageCollector(
      FactNormalizer factNormalizer,
      InventoryDatabaseOperations inventory,
      AccountServiceInventoryRepository accountServiceInventoryRepository,
      ApplicationProperties props,
      MeterRegistry meterRegistry) {
    this.factNormalizer = factNormalizer;
    this.inventory = inventory;
    this.accountServiceInventoryRepository = accountServiceInventoryRepository;
    this.culledOffsetDays = props.getCullingOffsetDays();
    this.totalHosts = meterRegistry.counter("rhsm-subscriptions.tally.hbi_hosts");
  }

  @SuppressWarnings("squid:S3776")
  @Transactional
  public Map<String, AccountUsageCalculation> collect(
      Collection<String> products, String account, String orgId) {
    log.info("Finding HBI hosts for account={} org={}", account, orgId);

    AccountServiceInventory accountServiceInventory =
        accountServiceInventoryRepository
            .findById(new AccountServiceInventoryId(account, HBI_INSTANCE_TYPE))
            .orElse(new AccountServiceInventory(account, HBI_INSTANCE_TYPE));

    Set<String> duplicateInstanceIds = new HashSet<>();
    Map<String, Host> inventoryHostMap =
        accountServiceInventory.getServiceInstances().values().stream()
            .filter(host -> host.getInventoryId() != null)
            .collect(
                Collectors.toMap(
                    Host::getInventoryId,
                    Function.identity(),
                    (h1, h2) -> handleDuplicateHost(duplicateInstanceIds, h1, h2)));
    duplicateInstanceIds.forEach(accountServiceInventory.getServiceInstances()::remove);

    Map<String, String> hypMapping = new HashMap<>();
    Map<String, Set<UsageCalculation.Key>> hypervisorUsageKeys = new HashMap<>();
    Map<String, Map<String, NormalizedFacts>> accountHypervisorFacts = new HashMap<>();
    Map<String, Host> hypervisorHosts = new HashMap<>();
    Map<String, Integer> hypervisorGuestCounts = new HashMap<>();

    inventory.reportedHypervisors(
        List.of(orgId), reported -> hypMapping.put((String) reported[0], (String) reported[1]));
    log.info("Found {} reported hypervisors.", hypMapping.size());

    Map<String, Set<HostBucketKey>> hostSeenBucketKeysLookup = new HashMap<>();
    Map<String, AccountUsageCalculation> calcsByAccount = new HashMap<>();
    inventory.processHostFacts(
        List.of(orgId),
        culledOffsetDays,
        hostFacts -> {
          calcsByAccount.putIfAbsent(account, new AccountUsageCalculation(account));

          AccountUsageCalculation accountCalc = calcsByAccount.get(account);
          NormalizedFacts facts = factNormalizer.normalize(hostFacts, hypMapping);

          // Validate and set the orgId.
          // Don't set null orgId as it may overwrite an existing value.
          // Likely won't happen, but there could be stale data in inventory
          // with no org set.
          String org = facts.getOrgId();
          if (org != null) {
            String currentOrg = accountCalc.getOrgId();
            if (currentOrg != null && !currentOrg.equalsIgnoreCase(org)) {
              throw new IllegalStateException(
                  String.format(
                      "Attempt to set a different org for an account: %s:%s", currentOrg, org));
            }
            accountCalc.setOrgId(org);
          }

          Host existingHost = inventoryHostMap.remove(hostFacts.getInventoryId().toString());
          Host host = existingHost == null ? hostFromHbiFacts(hostFacts, facts) : existingHost;
          if (existingHost != null) {
            populateHostFieldsFromHbi(host, hostFacts, facts);
          }
          Set<HostBucketKey> seenBucketKeys =
              hostSeenBucketKeysLookup.computeIfAbsent(host.getInstanceId(), h -> new HashSet<>());

          if (facts.isHypervisor()) {
            Map<String, NormalizedFacts> idToHypervisorMap =
                accountHypervisorFacts.computeIfAbsent(account, a -> new HashMap<>());
            idToHypervisorMap.put(hostFacts.getSubscriptionManagerId(), facts);
            hypervisorHosts.put(hostFacts.getSubscriptionManagerId(), host);
          } else if (facts.isVirtual() && StringUtils.hasText(facts.getHypervisorUuid())) {
            Integer guests = hypervisorGuestCounts.getOrDefault(host.getHypervisorUuid(), 0);
            hypervisorGuestCounts.put(host.getHypervisorUuid(), ++guests);
          }

          ServiceLevel[] slas = new ServiceLevel[] {facts.getSla(), ServiceLevel._ANY};
          Usage[] usages = new Usage[] {facts.getUsage(), Usage._ANY};

          // Calculate for each UsageKey
          // review current implementation of default values, and determine if factnormalizer needs
          // to handle billingAcctId & BillingProvider
          products.forEach(
              product -> {
                for (ServiceLevel sla : slas) {
                  for (Usage usage : usages) {
                    UsageCalculation.Key key =
                        new UsageCalculation.Key(product, sla, usage, BillingProvider._ANY, "_ANY");
                    UsageCalculation calc = accountCalc.getOrCreateCalculation(key);
                    if (facts.getProducts().contains(product)) {
                      try {
                        String hypervisorUuid = facts.getHypervisorUuid();
                        if (hypervisorUuid != null) {
                          Set<UsageCalculation.Key> keys =
                              hypervisorUsageKeys.computeIfAbsent(
                                  hypervisorUuid, uuid -> new HashSet<>());
                          keys.add(key);
                        }
                        Optional<HostTallyBucket> appliedBucket =
                            ProductUsageCollectorFactory.get(product).collect(calc, facts);
                        appliedBucket.ifPresent(
                            bucket -> {
                              // host.addBucket changes bucket.key.hostId, so we do that first; to
                              // avoid mutating the item in the set
                              host.addBucket(bucket);
                              seenBucketKeys.add(bucket.getKey());
                            });
                      } catch (Exception e) {
                        log.error(
                            "Unable to collect usage data for host: {} product: {}",
                            hostFacts.getSubscriptionManagerId(),
                            product,
                            e);
                      }
                    }
                  }
                }
              });
          // Save the host now that the buckets have been determined. Hypervisor hosts will
          // be persisted once all potential guests have been processed.
          if (!facts.isHypervisor()) {
            accountServiceInventory.getServiceInstances().put(host.getInstanceId(), host);
          }

          totalHosts.increment();
        });

    // apply data from guests to hypervisor records
    collectHypervisorGuestData(
        hypervisorUsageKeys,
        accountHypervisorFacts,
        hypervisorHosts,
        hypervisorGuestCounts,
        calcsByAccount,
        hostSeenBucketKeysLookup);

    log.info(
        "Removing {} stale host records (HBI records no longer present).", inventoryHostMap.size());
    inventoryHostMap.values().stream()
        .map(Host::getInstanceId)
        .forEach(accountServiceInventory.getServiceInstances()::remove);

    log.info("Removing stale buckets");
    accountServiceInventory
        .getServiceInstances()
        .values()
        .forEach(
            host -> {
              Set<HostBucketKey> seenBucketKeys =
                  hostSeenBucketKeysLookup.computeIfAbsent(
                      host.getInstanceId(), h -> new HashSet<>());
              host.getBuckets().removeIf(b -> !seenBucketKeys.contains(b.getKey()));
            });

    if (hypervisorHosts.size() > 0) {
      log.info("Persisting {} hypervisor hosts.", hypervisorHosts.size());
      hypervisorHosts
          .values()
          .forEach(
              host ->
                  accountServiceInventory.getServiceInstances().put(host.getInstanceId(), host));
    }

    if (log.isDebugEnabled()) {
      calcsByAccount.values().forEach(calc -> log.debug("Account Usage: {}", calc));
    }

    accountServiceInventory.setOrgId(orgId);
    accountServiceInventoryRepository.save(accountServiceInventory);

    return calcsByAccount;
  }

  private Host handleDuplicateHost(Set<String> duplicateInstanceIds, Host host1, Host host2) {
    log.warn("Removing duplicate host record w/ inventory ID: {}", host2.getInventoryId());
    duplicateInstanceIds.add(host2.getInstanceId());
    return host1;
  }

  private void collectHypervisorGuestData(
      Map<String, Set<Key>> hypervisorUsageKeys,
      Map<String, Map<String, NormalizedFacts>> accountHypervisorFacts,
      Map<String, Host> hypervisorHosts,
      Map<String, Integer> hypervisorGuestCounts,
      Map<String, AccountUsageCalculation> calcsByAccount,
      Map<String, Set<HostBucketKey>> hostSeenBucketKeyLookup) {
    accountHypervisorFacts.forEach(
        (account, accountHypervisors) -> {
          AccountUsageCalculation accountCalc = calcsByAccount.get(account);
          accountHypervisors.forEach(
              (hypervisorUuid, hypervisor) -> {
                Host hypHost = hypervisorHosts.get(hypervisorUuid);
                Set<HostBucketKey> hostBucketKeys =
                    hostSeenBucketKeyLookup.computeIfAbsent(
                        hypHost.getInstanceId(), h -> new HashSet<>());
                hypHost.setNumOfGuests(hypervisorGuestCounts.getOrDefault(hypervisorUuid, 0));
                Set<UsageCalculation.Key> usageKeys =
                    hypervisorUsageKeys.getOrDefault(hypervisorUuid, Collections.emptySet());

                usageKeys.forEach(
                    key -> {
                      UsageCalculation usageCalc = accountCalc.getOrCreateCalculation(key);
                      ProductUsageCollector productUsageCollector =
                          ProductUsageCollectorFactory.get(key.getProductId());
                      Optional<HostTallyBucket> appliedBucket =
                          productUsageCollector.collectForHypervisor(
                              account, usageCalc, hypervisor);
                      appliedBucket.ifPresent(
                          bucket -> {
                            // hypHost.addBucket changes bucket.key.hostId, so we do that first; to
                            // avoid mutating the item in the set
                            hypHost.addBucket(bucket);
                            hostBucketKeys.add(bucket.getKey());
                          });
                    });
              });
        });
  }

  public static void populateHostFieldsFromHbi(
      Host host, InventoryHostFacts inventoryHostFacts, NormalizedFacts normalizedFacts) {
    if (inventoryHostFacts.getInventoryId() != null) {
      host.setInventoryId(inventoryHostFacts.getInventoryId().toString());
      // We assume that the instance ID for any given HBI host record is the inventory ID; compare
      // to an OpenShift Cluster from Prometheus data, where we use the cluster ID.
      if (host.getInstanceId() == null) {
        // Don't overwrite the instanceId if already set, since that would cause potential
        // duplicates in the serviceInstances map in AccountServiceInventory.
        host.setInstanceId(inventoryHostFacts.getInventoryId().toString());
      }
    }

    host.setInsightsId(inventoryHostFacts.getInsightsId());
    host.setAccountNumber(inventoryHostFacts.getAccount());
    host.setOrgId(inventoryHostFacts.getOrgId());
    host.setDisplayName(inventoryHostFacts.getDisplayName());
    host.setSubscriptionManagerId(inventoryHostFacts.getSubscriptionManagerId());
    host.setGuest(normalizedFacts.isVirtual());
    host.setHypervisorUuid(normalizedFacts.getHypervisorUuid());

    if (normalizedFacts.getCores() != null) {
      host.getMeasurements().put(Measurement.Uom.CORES, normalizedFacts.getCores().doubleValue());
    }

    if (normalizedFacts.getSockets() != null) {
      host.getMeasurements()
          .put(Measurement.Uom.SOCKETS, normalizedFacts.getSockets().doubleValue());
    }

    host.setHypervisor(normalizedFacts.isHypervisor());
    host.setUnmappedGuest(normalizedFacts.isVirtual() && normalizedFacts.isHypervisorUnknown());
    host.setCloudProvider(
        normalizedFacts.getCloudProviderType() == null
            ? null
            : normalizedFacts.getCloudProviderType().name());

    host.setLastSeen(inventoryHostFacts.getModifiedOn());
    host.setHardwareType(normalizedFacts.getHardwareType());
  }

  public static Host hostFromHbiFacts(
      InventoryHostFacts inventoryHostFacts, NormalizedFacts normalizedFacts) {
    Host host = new Host();
    host.setInstanceType(HBI_INSTANCE_TYPE);
    populateHostFieldsFromHbi(host, inventoryHostFacts, normalizedFacts);
    return host;
  }
}
