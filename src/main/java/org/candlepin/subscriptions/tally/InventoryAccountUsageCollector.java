/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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

import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.inventory.db.InventoryRepository;
import org.candlepin.subscriptions.inventory.db.model.InventoryHostFacts;
import org.candlepin.subscriptions.tally.collector.ProductUsageCollector;
import org.candlepin.subscriptions.tally.collector.ProductUsageCollectorFactory;
import org.candlepin.subscriptions.tally.facts.FactNormalizer;
import org.candlepin.subscriptions.tally.facts.NormalizedFacts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Collects the max values from all accounts in the inventory.
 */
@Component
public class InventoryAccountUsageCollector {

    private static final Logger log = LoggerFactory.getLogger(InventoryAccountUsageCollector.class);

    private final FactNormalizer factNormalizer;
    private final InventoryRepository inventoryRepository;
    private final int culledOffsetDays;

    public InventoryAccountUsageCollector(FactNormalizer factNormalizer,
        InventoryRepository inventoryRepository, ApplicationProperties props) {
        this.factNormalizer = factNormalizer;
        this.inventoryRepository = inventoryRepository;
        this.culledOffsetDays = props.getCullingOffsetDays();
    }

    @SuppressWarnings("squid:S3776")
    @Transactional(value = "inventoryTransactionManager", readOnly = true)
    public Collection<AccountUsageCalculation> collect(Collection<String> products,
        Collection<String> accounts) {

        Map<String, String> hypMapping = new HashMap<>();
        Map<String, Set<UsageCalculation.Key>> hypervisorUsageKeys = new HashMap<>();
        Map<String, Map<String, NormalizedFacts>> accountHypervisorFacts = new HashMap<>();
        try (Stream<Object[]> stream = inventoryRepository.getReportedHypervisors(accounts)) {
            stream.forEach(res -> hypMapping.put((String) res[0], (String) res[1]));
        }
        log.info("Found {} reported hypervisors.", hypMapping.size());

        Map<String, AccountUsageCalculation> calcsByAccount = new HashMap<>();
        try (Stream<InventoryHostFacts> hostFactStream =
            inventoryRepository.getFacts(accounts, culledOffsetDays)) {
            hostFactStream.forEach(hostFacts -> {
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
                            String.format("Attempt to set a different owner for an account: %s:%s",
                                currentOwner, owner));
                    }
                    accountCalc.setOwner(owner);
                }

                if (facts.isHypervisor()) {
                    Map<String, NormalizedFacts> idToHypervisorMap = accountHypervisorFacts
                        .computeIfAbsent(account, a -> new HashMap<>());
                    idToHypervisorMap.put(hostFacts.getSubscriptionManagerId(), facts);
                }

                ServiceLevel[] slas = new ServiceLevel[]{facts.getSla(), ServiceLevel.ANY};
                Usage[] usages = new Usage[]{facts.getUsage(), Usage.ANY};

                // Calculate for each UsageKey
                products.forEach(product -> {
                    for (ServiceLevel sla : slas) {
                        for (Usage usage : usages) {
                            UsageCalculation.Key key = new UsageCalculation.Key(product, sla, usage);
                            UsageCalculation calc = getOrCreateCalculation(accountCalc, key);
                            if (facts.getProducts().contains(product)) {
                                try {
                                    String hypervisorUuid = facts.getHypervisorUuid();
                                    if (hypervisorUuid != null) {
                                        Set<UsageCalculation.Key> keys = hypervisorUsageKeys
                                            .computeIfAbsent(hypervisorUuid, uuid -> new HashSet<>());
                                        keys.add(key);
                                    }
                                    ProductUsageCollectorFactory.get(product).collect(calc, facts);
                                }
                                catch (Exception e) {
                                    log.error("Unable to collect usage data for host: {} product: {}",
                                        hostFacts.getSubscriptionManagerId(), product, e);
                                }
                            }
                        }
                    }
                });
            });
        }

        accountHypervisorFacts.forEach((account, accountHypervisors) -> {
            AccountUsageCalculation accountCalc = calcsByAccount.get(account);
            accountHypervisors.forEach((hypervisorUuid, hypervisor) -> {
                Set<UsageCalculation.Key> usageKeys = hypervisorUsageKeys.getOrDefault(hypervisorUuid,
                    Collections.emptySet());

                usageKeys.forEach(key -> {
                    UsageCalculation usageCalc = getOrCreateCalculation(accountCalc, key);
                    ProductUsageCollector productUsageCollector = ProductUsageCollectorFactory
                        .get(key.getProductId());
                    productUsageCollector.collectForHypervisor(account, usageCalc, hypervisor);
                });
            });
        });

        if (log.isDebugEnabled()) {
            calcsByAccount.values().forEach(calc -> log.debug("Account Usage: {}", calc));
        }

        return calcsByAccount.values();
    }

    private UsageCalculation getOrCreateCalculation(AccountUsageCalculation accountCalc,
        UsageCalculation.Key key) {

        UsageCalculation calc = accountCalc.getCalculation(key);
        if (calc == null) {
            calc = new UsageCalculation(key);
            accountCalc.addCalculation(calc);
        }
        return calc;
    }
}
