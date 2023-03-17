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
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.persistence.EntityManager;
import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.db.AccountServiceInventoryRepository;
import org.candlepin.subscriptions.db.HostRepository;
import org.candlepin.subscriptions.db.HostTallyBucketRepository;
import org.candlepin.subscriptions.db.model.AccountBucketTally;
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
import org.candlepin.subscriptions.tally.UsageCalculation.Key;
import org.candlepin.subscriptions.tally.collector.ProductUsageCollectorFactory;
import org.candlepin.subscriptions.tally.facts.FactNormalizer;
import org.candlepin.subscriptions.tally.facts.NormalizedFacts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
  private final HostTallyBucketRepository tallyBucketRepository;
  private final HostRepository hostRepository;
  private final EntityManager entityManager;
  private final int culledOffsetDays;
  private final int tallyMaxHbiAccountSize;
  private final Counter totalHosts;
  private final Long hbiReconciliationFlushInterval;
  private final InventorySwatchDataCollator collator;

  @Autowired
  public InventoryAccountUsageCollector(
      FactNormalizer factNormalizer,
      InventoryDatabaseOperations inventory,
      AccountServiceInventoryRepository accountServiceInventoryRepository,
      HostRepository hostRepository,
      EntityManager entityManager,
      HostTallyBucketRepository tallyBucketRepository,
      ApplicationProperties props,
      MeterRegistry meterRegistry,
      InventorySwatchDataCollator collator) {
    this.factNormalizer = factNormalizer;
    this.inventory = inventory;
    this.accountServiceInventoryRepository = accountServiceInventoryRepository;
    this.collator = collator;
    this.hostRepository = hostRepository;
    this.entityManager = entityManager;
    this.tallyBucketRepository = tallyBucketRepository;
    this.culledOffsetDays = props.getCullingOffsetDays();
    this.tallyMaxHbiAccountSize = props.getTallyMaxHbiAccountSize();
    this.hbiReconciliationFlushInterval = props.getHbiReconciliationFlushInterval();
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

          Set<Key> usageKeys =
              createKeyCombinations(
                  products,
                  Set.of(facts.getSla(), ServiceLevel._ANY),
                  Set.of(facts.getUsage(), Usage._ANY),
                  Set.of(BillingProvider._ANY),
                  Set.of("_ANY"));

          // Calculate for each UsageKey
          // review current implementation of default values, and determine if factnormalizer needs
          // to handle billingAcctId & BillingProvider
          for (Key key : usageKeys) {
            var product = key.getProductId();
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

  @Transactional
  public AccountUsageCalculation tally(String orgId) {
    log.info("Running tally via DB for orgId={}", orgId);
    AccountUsageCalculation calculation = new AccountUsageCalculation(orgId);
    try (Stream<AccountBucketTally> tallyStream =
        tallyBucketRepository.tallyHostBuckets(orgId, HBI_INSTANCE_TYPE)) {
      tallyStream.forEach(
          bucketTally -> {
            String currentAccount = calculation.getAccount();
            String hostAccount = bucketTally.getAccountNumber();

            // Set the account number if it is available
            if (Objects.isNull(currentAccount) && Objects.nonNull(hostAccount)) {
              calculation.setAccount(bucketTally.getAccountNumber());
            }

            UsageCalculation usageCalc =
                calculation.getOrCreateCalculation(
                    new Key(
                        bucketTally.getProductId(),
                        bucketTally.getSla(),
                        bucketTally.getUsage(),
                        bucketTally.getBillingProvider(),
                        bucketTally.getBillingAccountId()));
            usageCalc.add(
                bucketTally.getMeasurementType(),
                bucketTally.getCores(),
                bucketTally.getSockets(),
                bucketTally.getInstances());
          });
      return calculation;
    }
  }

  /**
   * Create all possible combinations of product, SLA, usage, billing provider, and account ID.
   *
   * @param products productIds
   * @param slas a set of SLAs
   * @param usages a set of usages
   * @param billingProviders a set of BillingProviders
   * @param billingAccountIds a set of billing account IDs
   * @return a set of UsageCalculation.Key representing all possible combinations of the input
   *     parameters
   */
  public static Set<Key> createKeyCombinations(
      Set<String> products,
      Set<ServiceLevel> slas,
      Set<Usage> usages,
      Set<BillingProvider> billingProviders,
      Set<String> billingAccountIds) {
    Set<List<Object>> usageTuples =
        Sets.cartesianProduct(products, slas, usages, billingProviders, billingAccountIds);
    return usageTuples.stream()
        .map(
            tuple -> {
              String product = (String) tuple.get(0);
              ServiceLevel sla = (ServiceLevel) tuple.get(1);
              Usage usage = (Usage) tuple.get(2);
              BillingProvider billingProvider = (BillingProvider) tuple.get(3);
              String billingAccountId = (String) tuple.get(4);

              return new UsageCalculation.Key(
                  product, sla, usage, billingProvider, billingAccountId);
            })
        .collect(Collectors.toSet());
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

  /**
   * Reconcile HBI data with swatch data.
   *
   * <p>This method also performs flushing of the created or updated records using a configured
   * batch size. This enables configurable control over the memory characteristics of system data
   * reconciliation.
   *
   * @param orgId orgId to reconcile
   * @param applicableProducts products to update tally buckets for
   */
  @Transactional
  @Timed("swatch_hbi_system_reconcile")
  public void reconcileSystemDataWithHbi(String orgId, Set<String> applicableProducts) {
    if (!accountServiceInventoryRepository.existsById(
        AccountServiceInventoryId.builder().orgId(orgId).serviceType(HBI_INSTANCE_TYPE).build())) {
      accountServiceInventoryRepository.save(new AccountServiceInventory(orgId, HBI_INSTANCE_TYPE));
    }
    int systemsUpdatedForOrg =
        collator.collateData(
            orgId,
            culledOffsetDays,
            (hbiSystem, swatchSystem, hypervisorData, iterationCount) -> {
              reconcileHbiSystemWithSwatchSystem(
                  hbiSystem, swatchSystem, hypervisorData, applicableProducts);
              if (iterationCount % hbiReconciliationFlushInterval == 0) {
                log.debug("Flushing system changes w/ count={}", iterationCount);
                hostRepository.flush();
                entityManager.clear();
              }
            });
    log.info("Reconciled {} records for orgId={}", systemsUpdatedForOrg, orgId);
  }

  /**
   * Reconciles an HBI system record with a swatch system record.
   *
   * <p>Performs the proper create, update or delete operation based on the state of the HBI record
   * and the state of the swatch record.
   *
   * @param hbiSystem HBI system record, or null
   * @param swatchSystem swatch system record, or null
   * @param orgHostsData container for data gathered from guests, expected to contain an entry for
   *     the hbi system being processed if it is a hypervisor
   * @param applicableProducts set of product tags to process
   */
  public void reconcileHbiSystemWithSwatchSystem(
      InventoryHostFacts hbiSystem,
      Host swatchSystem,
      OrgHostsData orgHostsData,
      Set<String> applicableProducts) {
    log.debug(
        "Reconciling HBI inventoryId={} & swatch inventoryId={}",
        Optional.ofNullable(hbiSystem).map(InventoryHostFacts::getInventoryId),
        Optional.ofNullable(swatchSystem).map(Host::getInventoryId));
    if (hbiSystem == null && swatchSystem == null) {
      log.debug("Unexpected, both HBI & Swatch system records are empty");
    } else if (hbiSystem == null) {
      log.debug("Deleting system w/ inventoryId={}", swatchSystem.getInventoryId());
      hostRepository.delete(swatchSystem);
    } else {
      NormalizedFacts normalizedFacts = factNormalizer.normalize(hbiSystem, orgHostsData);
      Set<Key> usageKeys = createHostUsageKeys(applicableProducts, normalizedFacts);

      if (swatchSystem != null) {
        log.debug("Updating system w/ inventoryId={}", hbiSystem.getInventoryId());
        updateSwatchSystem(hbiSystem, normalizedFacts, swatchSystem, usageKeys);
      } else {
        log.debug("Creating system w/ inventoryId={}", hbiSystem.getInventoryId());
        swatchSystem = createSwatchSystem(hbiSystem, normalizedFacts, usageKeys);
      }
      reconcileHypervisorData(normalizedFacts, swatchSystem, orgHostsData, usageKeys);
    }
  }

  private void reconcileHypervisorData(
      NormalizedFacts normalizedFacts, Host system, OrgHostsData orgHostsData, Set<Key> usageKeys) {
    if (system.getHypervisorUuid() != null
        && orgHostsData.hasHypervisorUuid(system.getHypervisorUuid())) {
      // system is a guest w/ known hypervisor, we should add its buckets to hypervisor-guest data
      usageKeys.forEach(
          usageKey -> orgHostsData.addHypervisorKey(system.getHypervisorUuid(), usageKey));
      orgHostsData.incrementGuestCount(system.getHypervisorUuid());
      orgHostsData.collectGuestData(new HashMap<>());
    } else if (system.getSubscriptionManagerId() != null) {
      // this is a potential hypervisor record
      Host placeholder = orgHostsData.hypervisorHostMap().get(system.getSubscriptionManagerId());
      if (placeholder != null) {
        // this system is a hypervisor, transfer buckets & counts to it
        log.debug("Applying buckets and guest-count from orgHostsData.");
        system.setHypervisor(true);
        system.setNumOfGuests(placeholder.getNumOfGuests());
        Set<HostBucketKey> seenBucketKeys = new HashSet<>();
        placeholder
            .getBuckets()
            .forEach(
                bucket -> {
                  if (normalizedFacts.getCores() != null) {
                    bucket.setCores(normalizedFacts.getCores());
                  }
                  if (normalizedFacts.getSockets() != null) {
                    bucket.setSockets(normalizedFacts.getSockets());
                  }
                  system.addBucket(bucket);
                  seenBucketKeys.add(bucket.getKey());
                });
        // remove any buckets for guests no longer present
        system
            .getBuckets()
            .removeIf(b -> b.getKey().getAsHypervisor() && !seenBucketKeys.contains(b.getKey()));
      }
    }
  }

  private Host createSwatchSystem(
      InventoryHostFacts inventoryHostFacts, NormalizedFacts normalizedFacts, Set<Key> usageKeys) {
    Host host = new Host();
    host.setInstanceType(HBI_INSTANCE_TYPE);
    populateHostFieldsFromHbi(host, inventoryHostFacts, normalizedFacts);
    applyNonHypervisorBuckets(host, normalizedFacts, usageKeys);
    hostRepository.save(host);
    return host;
  }

  private Set<Key> createHostUsageKeys(Set<String> products, NormalizedFacts facts) {
    return createKeyCombinations(
        products.stream().filter(facts.getProducts()::contains).collect(Collectors.toSet()),
        Set.of(facts.getSla(), ServiceLevel._ANY),
        Set.of(facts.getUsage(), Usage._ANY),
        Set.of(BillingProvider._ANY),
        Set.of("_ANY"));
  }

  private void applyNonHypervisorBuckets(Host host, NormalizedFacts facts, Set<Key> usageKeys) {
    Set<HostBucketKey> seenBucketKeys = new HashSet<>();

    // Calculate for each UsageKey
    // review current implementation of default values, and determine if factnormalizer needs
    // to handle billingAcctId & BillingProvider
    for (Key key : usageKeys) {
      var product = key.getProductId();
      if (!facts.getProducts().contains(product)) {
        continue;
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
    }
    // Remove any *non-hypervisor* keys that weren't seen this time.
    // Hypervisor keys need to be evaluated against hypervisor-guest data and are handled by
    // reconcileHypervisorData
    host.getBuckets()
        .removeIf(b -> !b.getKey().getAsHypervisor() && !seenBucketKeys.contains(b.getKey()));
  }

  private void updateSwatchSystem(
      InventoryHostFacts inventoryHostFacts,
      NormalizedFacts normalizedFacts,
      Host host,
      Set<Key> usageKeys) {
    populateHostFieldsFromHbi(host, inventoryHostFacts, normalizedFacts);
    applyNonHypervisorBuckets(host, normalizedFacts, usageKeys);
    hostRepository.save(host);
  }
}
