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

import org.candlepin.subscriptions.tally.facts.FactNormalizer;
import org.candlepin.subscriptions.tally.facts.NormalizedFacts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Collects the max values from all accounts in the inventory.
 */
@Component
public class InventoryAccountUsageCollector {

    private static final Logger log = LoggerFactory.getLogger(InventoryAccountUsageCollector.class);

    private final FactNormalizer factNormalizer;
    private final ClassificationProxyRepository proxyRepository;

    public InventoryAccountUsageCollector(FactNormalizer factNormalizer,
        ClassificationProxyRepository proxyRepository) {
        this.factNormalizer = factNormalizer;
        this.proxyRepository = proxyRepository;
    }

    @SuppressWarnings("squid:S3776")
    @Transactional(value = "inventoryTransactionManager", readOnly = true)
    public Collection<AccountUsageCalculation> collect(Collection<String> products,
        Collection<String> accounts) {
        Map<String, AccountUsageCalculation> calcsByAccount = new HashMap<>();
        try (Stream<ClassifiedInventoryHostFacts> hostFactStream = proxyRepository.getFacts(accounts)) {
            hostFactStream.forEach(hostFacts -> {
                String account = hostFacts.getAccount();
                calcsByAccount.putIfAbsent(account, new AccountUsageCalculation(account));

                AccountUsageCalculation accountCalc = calcsByAccount.get(account);
                NormalizedFacts facts = factNormalizer.normalize(hostFacts);

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

                // Calculate for each product.
                products.forEach(product -> {
                    ProductUsageCalculation prodCalc = accountCalc.getProductCalculation(product);
                    if (prodCalc == null) {
                        prodCalc = new ProductUsageCalculation(product);
                        accountCalc.addProductCalculation(prodCalc);
                    }

                    if (facts.getProducts().contains(product)) {
                        prodCalc.addCores(facts.getCores() != null ? facts.getCores() : 0);
                        prodCalc.addSockets(facts.getSockets() != null ? facts.getSockets() : 0);
                        prodCalc.addInstances(1);
                    }
                });
            });
        }

        if (log.isDebugEnabled()) {
            calcsByAccount.values().forEach(calc -> log.debug("Account Usage: {}", calc));
        }

        return calcsByAccount.values();
    }

}
