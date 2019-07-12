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
package org.candlepin.subscriptions.tally.roller;

import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.AccountMaxValues;
import org.candlepin.subscriptions.db.model.TallyGranularity;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.inventory.db.InventoryRepository;
import org.candlepin.subscriptions.inventory.db.model.InventoryHost;
import org.candlepin.subscriptions.tally.ProductUsageCalculation;
import org.candlepin.subscriptions.tally.facts.FactNormalizer;
import org.candlepin.subscriptions.tally.facts.NormalizedFacts;
import org.candlepin.subscriptions.util.ApplicationClock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Produces daily usage snapshots based on data stored in the inventory service.
 */
public class DailySnapshotRoller extends BaseSnapshotRoller {

    private static final Logger log = LoggerFactory.getLogger(DailySnapshotRoller.class);

    private InventoryRepository inventoryRepository;
    private FactNormalizer factNormalizer;

    public DailySnapshotRoller(String product, InventoryRepository inventoryRepository,
        TallySnapshotRepository tallyRepo, FactNormalizer factNormalizer, ApplicationClock clock) {
        super(product, tallyRepo, clock);
        this.inventoryRepository = inventoryRepository;
        this.factNormalizer = factNormalizer;
    }

    @Override
    @Transactional
    public void rollSnapshots(List<String> accounts) {
        log.info("Producing daily snapshots for {} account.", accounts.size());
        produceDailySnapshots(accounts, calculateMaxValuesFromInventory(accounts));
    }

    @SuppressWarnings("squid:S3776")
    @Transactional(value = "inventoryTransactionManager", readOnly = true)
    public Collection<ProductUsageCalculation> calculateMaxValuesFromInventory(List<String> accounts) {
        Map<String, ProductUsageCalculation> calcsByAccount = new HashMap<>();
        try (Stream<InventoryHost> hostStream = inventoryRepository.findByAccountIn(accounts)) {
            hostStream.forEach(host -> {
                String account = host.getAccount();
                if (!calcsByAccount.containsKey(account)) {
                    calcsByAccount.put(account, new ProductUsageCalculation(account, this.product));
                }

                ProductUsageCalculation calc = calcsByAccount.get(account);
                NormalizedFacts facts = factNormalizer.normalize(host);

                if (facts.getProducts().contains(calc.getProductId())) {
                    calc.addCores(facts.getCores() != null ? facts.getCores() : 0);
                    calc.addSockets(facts.getSockets() != null ? facts.getSockets() : 0);
                    calc.addInstance();
                }

                // Validate and set the owner.
                String owner = facts.getOwner();
                if (owner == null) {
                    // Don't set null owner as it may overwrite an existing value.
                    // Likely won't happen, but there could be stale data in inventory
                    // with no owner set.
                    return;
                }

                String currentOwner = calc.getOwner();
                if (currentOwner != null && !currentOwner.equalsIgnoreCase(owner)) {
                    throw new IllegalStateException(
                        String.format("Attempt to set a different owner for an account: %s:%s",
                            currentOwner, owner));
                }
                calc.setOwner(owner);
            });
        }

        if (log.isDebugEnabled()) {
            for (ProductUsageCalculation calc : calcsByAccount.values()) {
                log.info("Account: {}, Cores: {}, Sockets: {}, Instances: {}", calc.getAccount(),
                    calc.getTotalCores(), calc.getTotalSockets(), calc.getInstanceCount());
            }
        }
        return calcsByAccount.values();
    }

    private void produceDailySnapshots(List<String> accounts,
        Collection<ProductUsageCalculation> calculations) {

        Map<String, AccountMaxValues> maxValues = calculations.stream()
            .map(AccountMaxValues::new)
            .collect(Collectors.toMap(AccountMaxValues::getAccountNumber, Function.identity()));

        Map<String, TallySnapshot> existingSnapsForToday = getCurrentSnapshotsByAccount(accounts,
            TallyGranularity.DAILY, clock.startOfToday(), clock.endOfToday());

        updateSnapshots(existingSnapsForToday, maxValues, TallyGranularity.DAILY);
    }

}
