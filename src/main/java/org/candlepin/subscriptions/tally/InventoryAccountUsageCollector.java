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
import org.candlepin.subscriptions.db.HostRepository;
import org.candlepin.subscriptions.db.model.Host;
import org.candlepin.subscriptions.db.model.HostTallyBucket;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.inventory.db.InventoryDatabaseOperations;
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

  private final FactNormalizer factNormalizer;
  private final InventoryDatabaseOperations inventory;
  private final HostRepository hostRepository;
  private final int culledOffsetDays;
  private final Counter totalHosts;

  public InventoryAccountUsageCollector(
      FactNormalizer factNormalizer,
      InventoryDatabaseOperations inventory,
      HostRepository hostRepository,
      ApplicationProperties props,
      MeterRegistry meterRegistry) {
    this.factNormalizer = factNormalizer;
    this.inventory = inventory;
    this.hostRepository = hostRepository;
    this.culledOffsetDays = props.getCullingOffsetDays();
    this.totalHosts = meterRegistry.counter("rhsm-subscriptions.tally.hbi_hosts");
  }

  @SuppressWarnings("squid:S3776")
  @Transactional
  public Map<String, AccountUsageCalculation> collect(
      Collection<String> products, Collection<String> accounts) {

    List<Host> existing = getAccountHosts(accounts);
    Map<String, Host> inventoryHostMap =
        existing.stream()
            .filter(host -> host.getInventoryId() != null)
            .collect(
                Collectors.toMap(
                    Host::getInventoryId, Function.identity(), this::handleDuplicateHost));

    Map<String, String> hypMapping = new HashMap<>();
    Map<String, Set<UsageCalculation.Key>> hypervisorUsageKeys = new HashMap<>();
    Map<String, Map<String, NormalizedFacts>> accountHypervisorFacts = new HashMap<>();
    Map<String, Host> hypervisorHosts = new HashMap<>();
    Map<String, Integer> hypervisorGuestCounts = new HashMap<>();

    inventory.reportedHypervisors(
        accounts, reported -> hypMapping.put((String) reported[0], (String) reported[1]));
    log.info("Found {} reported hypervisors.", hypMapping.size());

    Map<String, AccountUsageCalculation> calcsByAccount = new HashMap<>();
    inventory.processHostFacts(
        accounts,
        culledOffsetDays,
        hostFacts -> {
          String account = hostFacts.getAccount();

          calcsByAccount.putIfAbsent(account, new AccountUsageCalculation(account));

          AccountUsageCalculation accountCalc = calcsByAccount.get(account);
          NormalizedFacts facts = factNormalizer.normalize(hostFacts, hypMapping);

          // Validate and set the owner.
          // Don't set null owner as it may overwrite an existing value.
          // Likely won't happen, but there could be stale data in inventory
          // with no owner set.
          String owner = facts.getOwner();
          if (owner != null) {
            String currentOwner = accountCalc.getOwner();
            if (currentOwner != null && !currentOwner.equalsIgnoreCase(owner)) {
              throw new IllegalStateException(
                  String.format(
                      "Attempt to set a different owner for an account: %s:%s",
                      currentOwner, owner));
            }
            accountCalc.setOwner(owner);
          }

          Host existingHost = inventoryHostMap.remove(hostFacts.getInventoryId().toString());
          Host host = existingHost == null ? new Host(hostFacts, facts) : existingHost;
          if (existingHost != null) {
            host.getBuckets().clear(); // ensure we recalculate to remove any stale buckets
            host.populateFieldsFromHbi(hostFacts, facts);
          }

          if (facts.isHypervisor()) {
            Map<String, NormalizedFacts> idToHypervisorMap =
                accountHypervisorFacts.computeIfAbsent(account, a -> new HashMap<>());
            idToHypervisorMap.put(hostFacts.getSubscriptionManagerId(), facts);
            hypervisorHosts.put(hostFacts.getSubscriptionManagerId(), host);
          } else if (facts.isVirtual() && !StringUtils.isEmpty(facts.getHypervisorUuid())) {
            Integer guests = hypervisorGuestCounts.getOrDefault(host.getHypervisorUuid(), 0);
            hypervisorGuestCounts.put(host.getHypervisorUuid(), ++guests);
          }

          ServiceLevel[] slas = new ServiceLevel[] {facts.getSla(), ServiceLevel._ANY};
          Usage[] usages = new Usage[] {facts.getUsage(), Usage._ANY};

          // Calculate for each UsageKey
          products.forEach(
              product -> {
                for (ServiceLevel sla : slas) {
                  for (Usage usage : usages) {
                    UsageCalculation.Key key = new UsageCalculation.Key(product, sla, usage);
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
                        appliedBucket.ifPresent(host::addBucket);
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
            hostRepository.save(host);
          }

          totalHosts.increment();
        });

    // apply data from guests to hypervisor records
    collectHypervisorGuestData(
        hypervisorUsageKeys,
        accountHypervisorFacts,
        hypervisorHosts,
        hypervisorGuestCounts,
        calcsByAccount);

    log.info(
        "Removing {} stale host records (HBI records no longer present).", inventoryHostMap.size());
    hostRepository.deleteAll(inventoryHostMap.values());

    if (hypervisorHosts.size() > 0) {
      log.info("Persisting {} hypervisor hosts.", hypervisorHosts.size());
      hostRepository.saveAll(hypervisorHosts.values());
    }

    if (log.isDebugEnabled()) {
      calcsByAccount.values().forEach(calc -> log.debug("Account Usage: {}", calc));
    }

    return calcsByAccount;
  }

  private Host handleDuplicateHost(Host host1, Host host2) {
    log.warn("Removing duplicate host record w/ inventory ID: {}", host2.getInventoryId());
    hostRepository.delete(host2);
    return host1;
  }

  private void collectHypervisorGuestData(
      Map<String, Set<UsageCalculation.Key>> hypervisorUsageKeys,
      Map<String, Map<String, NormalizedFacts>> accountHypervisorFacts,
      Map<String, Host> hypervisorHosts,
      Map<String, Integer> hypervisorGuestCounts,
      Map<String, AccountUsageCalculation> calcsByAccount) {
    accountHypervisorFacts.forEach(
        (account, accountHypervisors) -> {
          AccountUsageCalculation accountCalc = calcsByAccount.get(account);
          accountHypervisors.forEach(
              (hypervisorUuid, hypervisor) -> {
                Host hypHost = hypervisorHosts.get(hypervisorUuid);
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
                      appliedBucket.ifPresent(hypHost::addBucket);
                    });
              });
        });
  }

  private List<Host> getAccountHosts(Collection<String> accounts) {
    return accounts.stream()
        .map(hostRepository::findByAccountNumber)
        .flatMap(List::stream)
        .collect(Collectors.toList());
  }
}
