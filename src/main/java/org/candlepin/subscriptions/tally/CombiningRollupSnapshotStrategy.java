/*
 * Copyright (c) 2021 Red Hat, Inc.
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

import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.TallyMeasurementKey;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.files.ProductProfileRegistry;
import org.candlepin.subscriptions.util.ApplicationClock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.DoubleBinaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This strategy makes a snapshot out of AccountCalculation for the finest granularity, and then uses the
 * output snapshots as an input for creation of snapshots of the next granularity.
 *
 * Today, this strategy assumes that the Swatch Product ID in AccountUsage records passed to this class
 * have only a SwatchProductId that has a finestGranularity of Granularity.HOURLY
 */
@Service
public class CombiningRollupSnapshotStrategy {

    private static final Logger log = LoggerFactory.getLogger(CombiningRollupSnapshotStrategy.class);
    private static final Granularity[] GRANULARITIES = { Granularity.HOURLY, Granularity.DAILY };

    private final TallySnapshotRepository tallyRepo;
    private final ProductProfileRegistry registry;
    private final SnapshotSummaryProducer summaryProducer;
    private final ApplicationClock clock;

    @Autowired
    public CombiningRollupSnapshotStrategy(TallySnapshotRepository tallyRepo, ProductProfileRegistry registry,
        SnapshotSummaryProducer summaryProducer, ApplicationClock clock) {

        this.tallyRepo = tallyRepo;
        this.registry = registry;
        this.summaryProducer = summaryProducer;
        this.clock = clock;
    }

    /**
     * @param accountCalcs Map of times and account calculations at that time
     * @param reductionFunction how to reduce the set of lower granularity snapshots its higher granularity
     *                         rollup
     */
    @Transactional
    public void produceSnapshotsFromCalculations(String accountNumber, OffsetDateTime startDateTime,
        OffsetDateTime endDateTime, Map<OffsetDateTime, AccountUsageCalculation> accountCalcs,
        DoubleBinaryOperator reductionFunction) {

        String swatchProductId = getSwatchProductId(accountCalcs);
        Set<String> swatchProductIds = getSwatchProductIds(accountCalcs);

        Granularity finestGranularity = lookupFinestGranularity(swatchProductId);

        Map<TallySnapshotNaturalKey, TallySnapshot> totalExistingSnapshots = new HashMap<>();
        Map<TallySnapshotNaturalKey, List<TallySnapshot>> derivedExistingSnapshots = new HashMap<>();

        catalogExistingSnapshots(accountNumber, startDateTime, endDateTime, totalExistingSnapshots,
            derivedExistingSnapshots, swatchProductIds);

        List<TallySnapshot> finestGranularitySnapshots = produceFinestGranularitySnapshots(
            totalExistingSnapshots, accountCalcs, finestGranularity);

        Map<TallySnapshotNaturalKey, List<TallySnapshot>> groupedFinestSnapshots = finestGranularitySnapshots
            .stream().collect(Collectors
            .groupingBy(s -> calculateRollupKey(calculateNextGranularity(finestGranularity), s)));

        List<TallySnapshot> rollupSnapshots = Arrays.stream(GRANULARITIES)
            .filter(g -> !Objects.equals(g, finestGranularity))
            .map(granularity -> produceRollups(totalExistingSnapshots, derivedExistingSnapshots, granularity,
            groupedFinestSnapshots, reductionFunction)).flatMap(List::stream).collect(Collectors.toList());

        Map<String, List<TallySnapshot>> totalSnapshots = Stream
            .of(finestGranularitySnapshots, rollupSnapshots).flatMap(List::stream)
            .collect(Collectors.groupingBy(TallySnapshot::getAccountNumber));

        summaryProducer.produceTallySummaryMessages(totalSnapshots);

        log.info("Finished producing finestGranularitySnapshots for all accounts.");
    }

    private void catalogExistingSnapshots(String accountNumber, OffsetDateTime startDateTime,
        OffsetDateTime endDateTime, Map<TallySnapshotNaturalKey, TallySnapshot> totalExistingSnapshots,
        Map<TallySnapshotNaturalKey, List<TallySnapshot>> derivedExistingSnapshots,
        Set<String> swatchProductIds) {
        for (Granularity granularity : GRANULARITIES) {
            Granularity rollupGranularity = calculateNextGranularity(granularity);

            boolean granularityWillBeRolledUp = Arrays.asList(GRANULARITIES).contains(rollupGranularity) &&
                Objects.nonNull(rollupGranularity);

            OffsetDateTime effectiveStartTime;
            OffsetDateTime effectiveEndTime;

            if (granularityWillBeRolledUp) {
                // need to fetch all component snapshots of the rollups affected
                effectiveStartTime = clock.calculateStartOfRange(startDateTime, rollupGranularity);
                effectiveEndTime = clock.calculateEndOfRange(endDateTime, rollupGranularity);
            }
            else {
                effectiveStartTime = clock.calculateStartOfRange(startDateTime, granularity);
                effectiveEndTime = endDateTime;
            }

            var existingSnapshots = getCurrentSnapshotsByAccount(List.of(accountNumber),
                swatchProductIds, granularity, effectiveStartTime, effectiveEndTime)
                .getOrDefault(accountNumber, Collections.emptyList());

            existingSnapshots
                .forEach(snap -> totalExistingSnapshots.put(new TallySnapshotNaturalKey(snap), snap));

            if (granularityWillBeRolledUp) {
                derivedExistingSnapshots.putAll(existingSnapshots.stream()
                    .collect(Collectors.groupingBy(s -> calculateRollupKey(rollupGranularity, s))));
            }
        }
    }

    /**
     * Get the effective Swatch Product ID for the account calculations.  Null is returned if there are no
     * calculations for the account or if there are no product ids for a calculation
     * <p>
     * NOTE: this method grabs the first AccountUsageCalculation's first Swatch Product ID (we assume that
     * the Swatch Product ID is representative. (If this changes there will be bugs).
     *
     * @param accountCalcs calculations
     * @return Swatch Product ID
     */
    protected String getSwatchProductId(Map<OffsetDateTime, AccountUsageCalculation> accountCalcs) {
        Optional<AccountUsageCalculation> accountUsageCalculation = accountCalcs.values().stream()
            .findFirst();

        if (accountUsageCalculation.isEmpty()) {
            return null;
        }

        return accountUsageCalculation.get().getProducts().stream().findFirst().orElse(null);
    }

    private Set<String> getSwatchProductIds(Map<OffsetDateTime, AccountUsageCalculation> accountCalcs) {
        return accountCalcs.values().stream().map(AccountUsageCalculation::getProducts).flatMap(Set::stream)
            .collect(Collectors.toSet());
    }

    protected Granularity lookupFinestGranularity(String swatchProductId) {
        return registry.findProfileForSwatchProductId(swatchProductId).getFinestGranularity();
    }

    @SuppressWarnings("indentation")
    protected Map<String, List<TallySnapshot>> getCurrentSnapshotsByAccount(Collection<String> accounts,
        Collection<String> products, Granularity granularity, OffsetDateTime begin, OffsetDateTime end) {
        try (Stream<TallySnapshot> snapStream = tallyRepo
            .findByAccountNumberInAndProductIdInAndGranularityAndSnapshotDateBetween(accounts, products,
                granularity, begin, end)) {
            return snapStream.collect(Collectors.groupingBy(TallySnapshot::getAccountNumber));
        }
    }

    protected void populateSnapshotFromProductUsageCalculation(TallySnapshot snapshot, String account,
        String owner, UsageCalculation productCalc, Granularity granularity) {
        snapshot.setProductId(productCalc.getProductId());
        snapshot.setServiceLevel(productCalc.getSla());
        snapshot.setUsage(productCalc.getUsage());
        snapshot.setGranularity(granularity);
        snapshot.setOwnerId(owner);
        snapshot.setAccountNumber(account);

        // Copy the calculated hardware measurements to the snapshots
        for (HardwareMeasurementType type : HardwareMeasurementType.values()) {
            UsageCalculation.Totals calculatedTotals = productCalc.getTotals(type);
            updateSnapshotWithHardwareMeasurements(snapshot, type, calculatedTotals);
        }
    }

    private Granularity calculateNextGranularity(Granularity granularity) {
        switch (granularity) {
            case HOURLY:
                return Granularity.DAILY;
            case DAILY:
                return Granularity.WEEKLY;
            case WEEKLY:
                return Granularity.MONTHLY;
            case MONTHLY:
                return Granularity.YEARLY;
            case YEARLY:
                return null;
            default:
                throw new IllegalArgumentException(String.format("Unsupported granularity: %s", granularity));
        }
    }

    private TallySnapshotNaturalKey calculateRollupKey(Granularity rollupGranularity,
        TallySnapshot snapshot) {
        TallySnapshotNaturalKey key = new TallySnapshotNaturalKey(snapshot);
        key.setReferenceDate(clock.calculateStartOfRange(snapshot.getSnapshotDate(), rollupGranularity));
        key.setGranularity(rollupGranularity);
        return key;
    }

    private List<TallySnapshot> produceFinestGranularitySnapshots(
        Map<TallySnapshotNaturalKey, TallySnapshot> existingSnapshotLookup,
        Map<OffsetDateTime, AccountUsageCalculation> accountCalcs, Granularity granularity) {

        List<TallySnapshot> saved = new ArrayList<>();

        accountCalcs.forEach((offset, accountCalc) ->  {
            for (UsageCalculation.Key usageKey : accountCalc.getKeys()) {
                var snapshotKey = new TallySnapshotNaturalKey(accountCalc.getAccount(),
                    usageKey.getProductId(), granularity, usageKey.getSla(), usageKey.getUsage(), offset);

                TallySnapshot existing = existingSnapshotLookup.get(snapshotKey);
                TallySnapshot snapshot = Objects.requireNonNullElseGet(existing, TallySnapshot::new);

                UsageCalculation productCalc = accountCalc.getCalculation(usageKey);

                populateSnapshotFromProductUsageCalculation(snapshot, accountCalc.getAccount(),
                    accountCalc.getOwner(), productCalc, granularity);

                snapshot.setSnapshotDate(offset);
                saved.add(tallyRepo.save(snapshot));
            }
        });
        return saved;
    }

    private List<TallySnapshot> produceRollups(
        Map<TallySnapshotNaturalKey, TallySnapshot> existingSnapshotLookup,
        Map<TallySnapshotNaturalKey, List<TallySnapshot>> rollupLookup,
        Granularity granularity,
        Map<TallySnapshotNaturalKey, List<TallySnapshot>> snapshotMapping,
        DoubleBinaryOperator reductionFunction) {

        List<TallySnapshot> rollupsProduced = new ArrayList<>();
        snapshotMapping.forEach((rollupKey, newAndUpdatedSnapshots) -> {
            List<TallySnapshot> existingContributingSnapshots = rollupLookup
                .getOrDefault(rollupKey, Collections.emptyList());
            rollupsProduced.addAll(
                produceRollups(existingSnapshotLookup, granularity, existingContributingSnapshots,
                newAndUpdatedSnapshots, reductionFunction));
        });

        return rollupsProduced;
    }

    private List<TallySnapshot> produceRollups(
        Map<TallySnapshotNaturalKey, TallySnapshot> existingSnapshotLookup,
        Granularity granularity,
        List<TallySnapshot> existingContributingSnapshots,
        List<TallySnapshot> newAndUpdatedSnapshots,
        DoubleBinaryOperator reductionFunction) {

        Map<UsageCalculation.Key, Map<TallyMeasurementKey, Double>> reducedMeasurements = new HashMap<>();

        TallySnapshot firstFinestGranularitySnapshot = newAndUpdatedSnapshots.stream().findFirst()
            .orElseThrow();

        // set used for deduping
        Set<TallySnapshot> allContributingSnapshots = new HashSet<>();
        allContributingSnapshots.addAll(existingContributingSnapshots);
        allContributingSnapshots.addAll(newAndUpdatedSnapshots);

        allContributingSnapshots.forEach(snapshot -> {
            var identifier = new UsageCalculation.Key(snapshot.getProductId(), snapshot.getServiceLevel(),
                snapshot.getUsage());
            reducedMeasurements.computeIfAbsent(identifier, i -> new HashMap<>());
            Map<TallyMeasurementKey, Double> measurements = reducedMeasurements.get(identifier);

            snapshot.getTallyMeasurements().forEach((tallyMeasurementKey, measurementValue) -> {
                Double currentTotal = measurements.getOrDefault(tallyMeasurementKey, 0.0);
                measurements
                    .put(tallyMeasurementKey,
                    reductionFunction.applyAsDouble(currentTotal, measurementValue));
            });
        });

        return updateTallySnapshots(existingSnapshotLookup, granularity, reducedMeasurements,
            firstFinestGranularitySnapshot);
    }

    private void updateSnapshotWithHardwareMeasurements(TallySnapshot snapshot,
        HardwareMeasurementType type, UsageCalculation.Totals calculatedTotals) {
        if (calculatedTotals != null) {
            log.debug("Updating snapshot with hardware measurement: {}", type);
            calculatedTotals.getMeasurements()
                .forEach((uom, value) -> snapshot.setMeasurement(type, uom, value));
        }
    }

    private List<TallySnapshot> updateTallySnapshots(
        Map<TallySnapshotNaturalKey, TallySnapshot> existingSnapshotLookup, Granularity granularity,
        Map<UsageCalculation.Key, Map<TallyMeasurementKey, Double>> reducedMeasurements,
        TallySnapshot firstFinestGranularitySnapshot) {
        List<TallySnapshot> saved = new ArrayList<>();

        reducedMeasurements.forEach((usageKey, measurements) -> {
            OffsetDateTime snapshotDate = clock.calculateStartOfRange(
                firstFinestGranularitySnapshot.getSnapshotDate(), granularity);
            var snapshotKey = new TallySnapshotNaturalKey(firstFinestGranularitySnapshot.getAccountNumber(),
                firstFinestGranularitySnapshot.getProductId(), granularity, usageKey.getSla(),
                usageKey.getUsage(), snapshotDate);
            TallySnapshot existing = existingSnapshotLookup.get(snapshotKey);
            TallySnapshot snapshot = Objects.requireNonNullElseGet(existing, TallySnapshot::new);

            snapshot.setAccountNumber(firstFinestGranularitySnapshot.getAccountNumber());
            snapshot.setOwnerId(firstFinestGranularitySnapshot.getOwnerId());
            snapshot.setProductId(firstFinestGranularitySnapshot.getProductId());

            snapshot.setSnapshotDate(snapshotDate);
            snapshot.setGranularity(granularity);
            snapshot.setServiceLevel(usageKey.getSla());
            snapshot.setUsage(usageKey.getUsage());
            measurements.forEach((measurementKey, value) -> snapshot
                .setMeasurement(measurementKey.getMeasurementType(), measurementKey.getUom(), value));

            saved.add(tallyRepo.save(snapshot));
        });

        return saved;
    }

}
