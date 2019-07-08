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
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.TallyGranularity;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.exception.SnapshotProducerException;
import org.candlepin.subscriptions.files.AccountListSource;
import org.candlepin.subscriptions.inventory.db.InventoryRepository;
import org.candlepin.subscriptions.tally.facts.FactNormalizer;
import org.candlepin.subscriptions.tally.facts.NormalizedFacts;

import com.google.common.collect.Iterables;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Produces UsageSnapshots for an organization.
 */
@Component
public class UsageSnapshotProducer {

    private static final Logger log = LoggerFactory.getLogger(UsageSnapshotProducer.class);

    private final FactNormalizer factNormalizer;
    private final AccountListSource accountListSource;
    private final InventoryRepository inventoryRepository;
    private final TallySnapshotRepository tallyRepo;
    private final Clock clock;
    private final int accountBatchSize;

    @Autowired
    public UsageSnapshotProducer(FactNormalizer factNormalizer, AccountListSource accountListSource,
        InventoryRepository inventoryRepository, TallySnapshotRepository tallyRepo, Clock clock,
        ApplicationProperties applicationProperties) {
        this.factNormalizer = factNormalizer;
        this.accountListSource = accountListSource;
        this.inventoryRepository = inventoryRepository;
        this.tallyRepo = tallyRepo;
        this.clock = clock;
        this.accountBatchSize = applicationProperties.getAccountBatchSize();
    }

    @Transactional
    public void produceSnapshots() {
        try {
            // Partition the account list to help reduce memory usage while performing
            // the calculations.
            log.info("Batch producing snapshots.");
            Iterables.partition(accountListSource.list(), accountBatchSize).forEach(accounts -> {
                log.info("Processing snapshots for the next {} accounts.", accounts.size());
                batchProduceDailySnapshots(getCalculationsForAccounts(accounts));
            });
            log.info("Finished producing snapshots for all configured accounts.");
        }
        catch (IOException ioe) {
            throw new SnapshotProducerException(
                "Unable to read account listing while producing usage snapshots.", ioe);
        }
    }

    @Transactional(value = "inventoryTransactionManager", readOnly = true)
    public List<ProductUsageCalculation> getCalculationsForAccounts(List<String> accounts) {
        List<ProductUsageCalculation> calculations = new LinkedList<>();
        for (String accountNumber : accounts) {
            // NOTE: When we start counting for other products, consider adding additional
            //       calculators to a list and applying the host to each.
            ProductUsageCalculation rhelUsageCalculation = new ProductUsageCalculation(accountNumber, "RHEL");
            inventoryRepository.findByAccount(accountNumber)
                .forEach(host -> {
                    NormalizedFacts facts = factNormalizer.normalize(host);
                    if (facts.getProducts().contains(rhelUsageCalculation.getProductId())) {
                        rhelUsageCalculation.addCores(facts.getCores() != null ? facts.getCores() : 0);
                        rhelUsageCalculation.addInstance();
                    }

                    // Validate and set the owner.
                    String owner = facts.getOwner();
                    if (owner == null) {
                        // Don't set null owner as it may overwrite an existing value.
                        // Likely won't happen, but there could be stale data in inventory
                        // with no owner set.
                        return;
                    }

                    String currentOwner = rhelUsageCalculation.getOwner();
                    if (currentOwner != null && !currentOwner.equalsIgnoreCase(owner)) {
                        throw new IllegalStateException(
                            String.format("Attempt to set a different owner for an account: %s:%s",
                                currentOwner, owner));
                    }
                    rhelUsageCalculation.setOwner(owner);
                });

            log.debug("Found {} cores of RHEL for account: {}", rhelUsageCalculation.getTotalCores(),
                rhelUsageCalculation.getAccount());
            calculations.add(rhelUsageCalculation);
        }
        return calculations;
    }

    private void batchProduceDailySnapshots(List<ProductUsageCalculation> calculations) {
        // Update snapshots that need updating.
        Set<String> snapshotAccounts = calculations.stream()
            .map(ProductUsageCalculation::getAccount)
            .collect(Collectors.toSet());

        log.debug("Fetching existing daily snapshots for {} accounts.", snapshotAccounts.size());
        Map<String, TallySnapshot> existingRhelSnapsForToday = getSnapshotsForToday(snapshotAccounts, "RHEL");
        log.debug("Found {} existing snapshots for today.", existingRhelSnapsForToday.size());

        List<TallySnapshot> toPersist = new LinkedList<>();
        calculations.forEach(calc -> {
            TallySnapshot snapshot = existingRhelSnapsForToday.containsKey(calc.getAccount()) ?
                existingRhelSnapsForToday.get(calc.getAccount()) : new TallySnapshot();
            snapshot.setProductId(calc.getProductId());
            snapshot.setCores(calc.getTotalCores());
            snapshot.setGranularity(TallyGranularity.DAILY);
            snapshot.setAccountNumber(calc.getAccount());
            snapshot.setOwnerId(calc.getOwner());
            snapshot.setInstanceCount(calc.getInstanceCount());
            snapshot.setSnapshotDate(OffsetDateTime.now(clock));
            toPersist.add(snapshot);
        });

        log.debug("Persisting {} snapshots.", toPersist.size());
        tallyRepo.saveAll(toPersist);
        log.debug("Snapshots persisted.");
    }

    private Map<String, TallySnapshot> getSnapshotsForToday(Set<String> accounts, String productId) {
        Map<String, TallySnapshot> accountToSnap = new HashMap<>();
        tallyRepo.findByAccountNumberInAndProductIdAndGranularityAndSnapshotDateBetween(accounts,
            productId, TallyGranularity.DAILY, startOfToday(), endOfToday()).forEach(s -> {
                if (accountToSnap.containsKey(s.getAccountNumber())) {
                    log.warn("Multiple daily snapshots have been found for account: {}. " +
                        "Last one found will be used.", s.getAccountNumber());
                }
                accountToSnap.put(s.getAccountNumber(), s);
            }
        );
        return accountToSnap;
    }

    private OffsetDateTime startOfToday() {
        return OffsetDateTime.from(LocalTime.MIDNIGHT.adjustInto(OffsetDateTime.now(clock)));
    }

    private OffsetDateTime endOfToday() {
        return OffsetDateTime.from(LocalTime.MAX.adjustInto(OffsetDateTime.now(clock)));
    }
}
