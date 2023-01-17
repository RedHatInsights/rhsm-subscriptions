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

import com.google.common.collect.Sets;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
import org.candlepin.subscriptions.db.model.AccountServiceInventory;
import org.candlepin.subscriptions.db.model.AccountServiceInventoryId;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.Host;
import org.candlepin.subscriptions.db.model.HostBucketKey;
import org.candlepin.subscriptions.db.model.HostTallyBucket;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.inventory.db.InventoryDatabaseOperations;
import org.candlepin.subscriptions.inventory.db.model.InventoryHostFacts;
import org.candlepin.subscriptions.json.Measurement;
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
  private final int tallyMaxHbiAccountSize;
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
    this.tallyMaxHbiAccountSize = props.getTallyMaxHbiAccountSize();
    this.totalHosts = meterRegistry.counter("rhsm-subscriptions.tally.hbi_hosts");
  }

  @SuppressWarnings("squid:S3776")
  @Transactional
  public OrgHostsData collect(Set<String> products, String account, String orgId) {
    int inventoryCount = inventory.activeSystemCountForOrgId(orgId, culledOffsetDays);
    if (inventoryCount > tallyMaxHbiAccountSize) {
      throw new SystemThresholdException(orgId, tallyMaxHbiAccountSize, inventoryCount);
    }
    AccountServiceInventory accountServiceInventory = fetchAccountServiceInventory(orgId, account);
    Map<String, Host> inventoryHostMap = buildInventoryHostMap(accountServiceInventory);

    OrgHostsData orgHostsData = new OrgHostsData(orgId);
    Map<String, Set<HostBucketKey>> hostSeenBucketKeysLookup = new HashMap<>();

    orgHostsData.addReportedHypervisors(inventory);

    inventory.processHost(
        orgId,
        culledOffsetDays,
        hostFacts -> {
          NormalizedFacts facts = factNormalizer.normalize(hostFacts, orgHostsData);

          Host existingHost = inventoryHostMap.remove(hostFacts.getInventoryId().toString());
          Host host;

          if (existingHost == null) {
            host = hostFromHbiFacts(hostFacts, facts);
          } else {
            host = existingHost;
            populateHostFieldsFromHbi(host, hostFacts, facts);
          }
          orgHostsData.addHostWithNormalizedFacts(host, facts);

          Set<HostBucketKey> seenBucketKeys =
              hostSeenBucketKeysLookup.computeIfAbsent(host.getInstanceId(), h -> new HashSet<>());

          if (facts.isHypervisor()) {
            orgHostsData.addHypervisorFacts(hostFacts.getSubscriptionManagerId(), facts);
            orgHostsData.addHostToHypervisor(hostFacts.getSubscriptionManagerId(), host);
          } else if (facts.isVirtual() && StringUtils.hasText(facts.getHypervisorUuid())) {
            orgHostsData.incrementGuestCount(host.getHypervisorUuid());
          }

          Set<List<Object>> usageTuples =
              Sets.cartesianProduct(
                  products,
                  Set.of(facts.getSla(), ServiceLevel._ANY),
                  Set.of(facts.getUsage(), Usage._ANY));

          // Calculate for each UsageKey
          // review current implementation of default values, and determine if factnormalizer needs
          // to handle billingAcctId & BillingProvider
          for (List<Object> tuple : usageTuples) {
            String product = (String) tuple.get(0);
            ServiceLevel sla = (ServiceLevel) tuple.get(1);
            Usage usage = (Usage) tuple.get(2);

            UsageCalculation.Key key =
                new UsageCalculation.Key(product, sla, usage, BillingProvider._ANY, "_ANY");

            if (!facts.getProducts().contains(product)) {
              continue;
            }

            try {
              String hypervisorUuid = facts.getHypervisorUuid();
              if (hypervisorUuid != null) {
                orgHostsData.addHypervisorKey(hypervisorUuid, key);
              }
              Optional<HostTallyBucket> appliedBucket =
                  ProductUsageCollectorFactory.get(product).buildBucket(key, facts);
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
          // Save the host now that the buckets have been determined. Hypervisor hosts will
          // be persisted once all potential guests have been processed.
          if (!facts.isHypervisor()) {
            accountServiceInventory.getServiceInstances().put(host.getInstanceId(), host);
          }

          totalHosts.increment();
        });

    log.info(
        "Removing {} stale host records (HBI records no longer present).", inventoryHostMap.size());
    inventoryHostMap.values().stream()
        .map(Host::getInstanceId)
        .forEach(accountServiceInventory.getServiceInstances()::remove);

    // apply data from guests to hypervisor records
    orgHostsData.collectGuestData(hostSeenBucketKeysLookup);

    log.info("Removing stale buckets");
    for (Host host : accountServiceInventory.getServiceInstances().values()) {
      Set<HostBucketKey> seenBucketKeys =
          hostSeenBucketKeysLookup.computeIfAbsent(host.getInstanceId(), h -> new HashSet<>());
      host.getBuckets().removeIf(b -> !seenBucketKeys.contains(b.getKey()));
    }

    var hypervisorHostMap = orgHostsData.hypervisorHostMap();
    if (hypervisorHostMap.size() > 0) {
      log.info("Persisting {} hypervisor hosts.", hypervisorHostMap.size());
      for (Host host : hypervisorHostMap.values()) {
        accountServiceInventory.getServiceInstances().put(host.getInstanceId(), host);
      }
    }
    accountServiceInventory.setOrgId(orgId);
    accountServiceInventoryRepository.save(accountServiceInventory);
    return orgHostsData;
  }

  @SuppressWarnings("squid:S3776")
  @Transactional
  public AccountUsageCalculation tally(Set<String> products, OrgHostsData orgHostsData) {
    AccountUsageCalculation accountCalc = new AccountUsageCalculation(orgHostsData.getOrgId());
    for (var entry : orgHostsData.getHostNormalizedFactsMap().entrySet()) {
      Host host = entry.getKey();
      NormalizedFacts facts = entry.getValue();

      // Validate and set the account number.
      // Don't set null account as it may overwrite an existing value.
      // Likely won't happen, but there could be stale data in inventory with no account set.
      String hostAccount = facts.getAccount();
      if (hostAccount != null) {
        String currentAccount = accountCalc.getAccount();
        if (currentAccount != null && !currentAccount.equalsIgnoreCase(hostAccount)) {
          throw new IllegalStateException(
              String.format(
                  "Attempt to set a different account for an org: %s:%s",
                  currentAccount, hostAccount));
        }
        accountCalc.setAccount(hostAccount);
      }

      Set<List<Object>> usageTuples =
          Sets.cartesianProduct(
              products,
              Set.of(facts.getSla(), ServiceLevel._ANY),
              Set.of(facts.getUsage(), Usage._ANY));

      // Calculate for each UsageKey
      // review current implementation of default values, and determine if factnormalizer needs
      // to handle billingAcctId & BillingProvider
      for (List<Object> tuple : usageTuples) {
        String product = (String) tuple.get(0);
        ServiceLevel sla = (ServiceLevel) tuple.get(1);
        Usage usage = (Usage) tuple.get(2);
        UsageCalculation.Key key =
            new UsageCalculation.Key(product, sla, usage, BillingProvider._ANY, "_ANY");
        UsageCalculation calc = accountCalc.getOrCreateCalculation(key);
        if (!facts.getProducts().contains(product)) {
          continue;
        }
        try {
          ProductUsageCollectorFactory.get(product).collect(calc, facts);
        } catch (Exception e) {
          log.error(
              "Unable to tally usage data for host: {} product: {}",
              host.getSubscriptionManagerId(),
              product,
              e);
        }
      }
    }
    orgHostsData.tallyGuestData(accountCalc);
    log.debug("Account Usage: {}", accountCalc);
    return accountCalc;
  }

  private AccountServiceInventory fetchAccountServiceInventory(String orgId, String account) {
    log.info("Finding HBI hosts for account={} org={}", account, orgId);
    AccountServiceInventoryId inventoryId =
        AccountServiceInventoryId.builder().orgId(orgId).serviceType(HBI_INSTANCE_TYPE).build();
    AccountServiceInventory accountServiceInventory =
        accountServiceInventoryRepository
            .findById(inventoryId)
            .orElse(new AccountServiceInventory(inventoryId));
    if (account != null) {
      accountServiceInventory.setAccountNumber(account);
    }

    return accountServiceInventory;
  }

  private Map<String, Host> buildInventoryHostMap(AccountServiceInventory accountServiceInventory) {
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
    return inventoryHostMap;
  }

  private Host handleDuplicateHost(Set<String> duplicateInstanceIds, Host host1, Host host2) {
    log.warn("Removing duplicate host record w/ inventory ID: {}", host2.getInventoryId());
    duplicateInstanceIds.add(host2.getInstanceId());
    return host1;
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
