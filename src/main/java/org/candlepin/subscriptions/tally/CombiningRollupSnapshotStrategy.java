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

import com.redhat.swatch.configuration.registry.MetricId;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.DoubleBinaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.TallyMeasurementKey;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.candlepin.subscriptions.util.DateRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * This strategy makes a snapshot out of AccountCalculation for the finest granularity, and then
 * uses the output snapshots as an input for creation of snapshots of the next granularity.
 *
 * <p>Today, this strategy assumes that the Swatch Product ID in AccountUsage records passed to this
 * class have only a SwatchProductId that has a finestGranularity of Granularity.HOURLY
 */
@Service
public class CombiningRollupSnapshotStrategy {

  private static final Logger log = LoggerFactory.getLogger(CombiningRollupSnapshotStrategy.class);
  private static final Granularity[] GRANULARITIES = {Granularity.HOURLY, Granularity.DAILY};

  private final TallySnapshotRepository tallyRepo;
  private final ApplicationClock clock;

  @Autowired
  public CombiningRollupSnapshotStrategy(
      TallySnapshotRepository tallyRepo, ApplicationClock clock) {
    this.tallyRepo = tallyRepo;
    this.clock = clock;
  }

  /**
   * @param orgId The ID of the target org
   * @param affectedRange the overall date range that we looked for calculations
   * @param affectedProductTags the set of product tags that are applicable
   * @param accountCalcs Map of times and account calculations at that time
   * @param finestGranularity the base granularity to be used for the calculations
   * @param reductionFunction how to reduce the set of lower granularity snapshots its higher
   *     granularity
   */
  @Transactional
  public Map<String, List<TallySnapshot>> produceSnapshotsFromCalculations(
      String orgId,
      DateRange affectedRange,
      Set<String> affectedProductTags,
      Map<OffsetDateTime, AccountUsageCalculation> accountCalcs,
      Granularity finestGranularity,
      DoubleBinaryOperator reductionFunction) {

    Map<TallySnapshotNaturalKey, TallySnapshot> totalExistingSnapshots = new HashMap<>();
    Map<TallySnapshotNaturalKey, List<TallySnapshot>> derivedExistingSnapshots = new HashMap<>();

    catalogExistingSnapshots(
        orgId,
        affectedRange,
        totalExistingSnapshots,
        derivedExistingSnapshots,
        affectedProductTags);

    List<TallySnapshot> finestGranularitySnapshots =
        produceFinestGranularitySnapshots(
            totalExistingSnapshots, accountCalcs, finestGranularity, affectedRange);

    Map<TallySnapshotNaturalKey, List<TallySnapshot>> groupedFinestSnapshots =
        finestGranularitySnapshots.stream()
            .collect(
                Collectors.groupingBy(
                    s -> calculateRollupKey(calculateNextGranularity(finestGranularity), s)));

    List<TallySnapshot> rollupSnapshots =
        Arrays.stream(GRANULARITIES)
            .filter(g -> !Objects.equals(g, finestGranularity))
            .map(
                granularity ->
                    produceRollups(
                        totalExistingSnapshots,
                        derivedExistingSnapshots,
                        granularity,
                        groupedFinestSnapshots,
                        reductionFunction))
            .flatMap(List::stream)
            .collect(Collectors.toList());

    // Only want to send messages for finest granularity snapshots that are within affected range
    var finestGranularitySnapshotsInRange =
        finestGranularitySnapshots.stream()
            .filter(snapshot -> affectedRange.contains(snapshot.getSnapshotDate()))
            .collect(Collectors.toList());

    Map<String, List<TallySnapshot>> totalSnapshotsToSend =
        Stream.of(finestGranularitySnapshotsInRange, rollupSnapshots)
            .flatMap(List::stream)
            .collect(Collectors.groupingBy(TallySnapshot::getOrgId));

    log.info("Finished producing finestGranularitySnapshots for orgId={}.", orgId);
    return totalSnapshotsToSend;
  }

  private void catalogExistingSnapshots(
      String orgId,
      DateRange reportDateRange,
      Map<TallySnapshotNaturalKey, TallySnapshot> totalExistingSnapshots,
      Map<TallySnapshotNaturalKey, List<TallySnapshot>> derivedExistingSnapshots,
      Set<String> swatchProductIds) {
    for (Granularity granularity : GRANULARITIES) {
      Granularity rollupGranularity = calculateNextGranularity(granularity);

      boolean granularityWillBeRolledUp =
          Arrays.asList(GRANULARITIES).contains(rollupGranularity)
              && Objects.nonNull(rollupGranularity);

      OffsetDateTime effectiveStartTime;
      OffsetDateTime effectiveEndTime;

      if (granularityWillBeRolledUp) {
        // need to fetch all component snapshots of the rollups affected
        effectiveStartTime =
            clock.calculateStartOfRange(reportDateRange.getStartDate(), rollupGranularity);
        effectiveEndTime =
            clock.calculateEndOfRange(reportDateRange.getEndDate(), rollupGranularity);
      } else {
        effectiveStartTime =
            clock.calculateStartOfRange(reportDateRange.getStartDate(), granularity);
        effectiveEndTime = reportDateRange.getEndDate();
      }

      var snapMap =
          getCurrentSnapshotsByOrgId(
              orgId, swatchProductIds, granularity, effectiveStartTime, effectiveEndTime);

      List<TallySnapshot> existingSnapshots = snapMap.getOrDefault(orgId, Collections.emptyList());

      existingSnapshots.forEach(
          snap -> {
            TallySnapshotNaturalKey key = new TallySnapshotNaturalKey(snap);
            if (totalExistingSnapshots.containsKey(key)) {
              log.warn("A snapshot with this key already exists. {}", snap);
            }
            totalExistingSnapshots.put(key, snap);
          });

      if (granularityWillBeRolledUp) {
        derivedExistingSnapshots.putAll(
            existingSnapshots.stream()
                .collect(Collectors.groupingBy(s -> calculateRollupKey(rollupGranularity, s))));
      }
    }
  }

  @SuppressWarnings("indentation")
  protected Map<String, List<TallySnapshot>> getCurrentSnapshotsByOrgId(
      String orgId,
      Collection<String> products,
      Granularity granularity,
      OffsetDateTime begin,
      OffsetDateTime end) {
    try (Stream<TallySnapshot> snapStream =
        tallyRepo.findByOrgIdAndProductIdInAndGranularityAndSnapshotDateBetween(
            orgId, products, granularity, begin, end)) {
      return snapStream.collect(Collectors.groupingBy(TallySnapshot::getOrgId));
    }
  }

  protected void populateSnapshotFromProductUsageCalculation(
      TallySnapshot snapshot,
      String account,
      String orgId,
      UsageCalculation productCalc,
      Granularity granularity) {
    snapshot.setProductId(productCalc.getProductId());
    snapshot.setServiceLevel(productCalc.getSla());
    snapshot.setUsage(productCalc.getUsage());
    snapshot.setGranularity(granularity);
    snapshot.setOrgId(orgId);
    snapshot.setAccountNumber(account);
    snapshot.setBillingAccountId(productCalc.getBillingAccountId());
    snapshot.setBillingProvider(productCalc.getBillingProvider());

    Set<TallyMeasurementKey> seenMeasurements = new HashSet<>();
    // Copy the calculated hardware measurements to the snapshots
    for (HardwareMeasurementType type : HardwareMeasurementType.values()) {
      UsageCalculation.Totals calculatedTotals = productCalc.getTotals(type);
      if (calculatedTotals != null) {
        // track measurements that are present in calculated values, so that we can remove
        // stale, unused measurements later
        calculatedTotals
            .getMeasurements()
            .keySet()
            .forEach(
                metricId ->
                    seenMeasurements.add(new TallyMeasurementKey(type, metricId.getValue())));
      }
      updateSnapshotWithHardwareMeasurements(snapshot, type, calculatedTotals);
    }
    // remove stale, unused measurements
    Set<TallyMeasurementKey> staleMeasurements =
        snapshot.getTallyMeasurements().keySet().stream()
            .filter(k -> !seenMeasurements.contains(k))
            .collect(Collectors.toSet());
    staleMeasurements.forEach(snapshot.getTallyMeasurements()::remove);
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
        throw new IllegalArgumentException(
            String.format("Unsupported granularity: %s", granularity));
    }
  }

  private TallySnapshotNaturalKey calculateRollupKey(
      Granularity rollupGranularity, TallySnapshot snapshot) {
    TallySnapshotNaturalKey key = new TallySnapshotNaturalKey(snapshot);
    key.setReferenceDate(
        clock.calculateStartOfRange(snapshot.getSnapshotDate(), rollupGranularity));
    key.setGranularity(rollupGranularity);
    return key;
  }

  private List<TallySnapshot> produceFinestGranularitySnapshots(
      Map<TallySnapshotNaturalKey, TallySnapshot> existingSnapshotLookup,
      Map<OffsetDateTime, AccountUsageCalculation> accountCalcs,
      Granularity granularity,
      DateRange dateRange) {

    List<TallySnapshot> toSave = new ArrayList<>();

    Map<TallySnapshotNaturalKey, TallySnapshot> affectedSnaps =
        existingSnapshotLookup.entrySet().stream()
            .filter(s -> snapshotAffectedByRange(s.getValue(), dateRange))
            .filter(existing -> existing.getValue().getGranularity() == granularity)
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue));

    accountCalcs.forEach(
        (offset, accountCalc) -> {
          for (UsageCalculation.Key usageKey : accountCalc.getKeys()) {
            var snapshotKey =
                new TallySnapshotNaturalKey(
                    accountCalc.getOrgId(),
                    usageKey.getProductId(),
                    granularity,
                    usageKey.getSla(),
                    usageKey.getUsage(),
                    usageKey.getBillingProvider(),
                    usageKey.getBillingAccountId(),
                    offset);

            TallySnapshot existing = affectedSnaps.remove(snapshotKey);
            TallySnapshot snapshot = Objects.requireNonNullElseGet(existing, TallySnapshot::new);

            UsageCalculation productCalc = accountCalc.getCalculation(usageKey);

            populateSnapshotFromProductUsageCalculation(
                snapshot,
                accountCalc.getAccount(),
                accountCalc.getOrgId(),
                productCalc,
                granularity);

            snapshot.setSnapshotDate(offset);
            toSave.add(tallyRepo.save(snapshot));
          }
        });

    // Reset remaining snaps, as they didn't have any calculations/events
    affectedSnaps
        .values()
        .forEach(
            snapshot -> {
              snapshot.getTallyMeasurements().clear();
              toSave.add(tallyRepo.save(snapshot));
            });
    return toSave;
  }

  private boolean snapshotAffectedByRange(TallySnapshot tallySnapshot, DateRange dateRange) {
    // NOTE: we can't simply use contains here, because contains includes a match on the end date
    // e.g. a tally in range 9:00 - 10:00 should match records w/ date 9:00, but not w/ date 10:00
    return (dateRange.getStartDate().isEqual(tallySnapshot.getSnapshotDate())
            || dateRange.getStartDate().isBefore(tallySnapshot.getSnapshotDate()))
        && dateRange.getEndDate().isAfter(tallySnapshot.getSnapshotDate());
  }

  private List<TallySnapshot> produceRollups(
      Map<TallySnapshotNaturalKey, TallySnapshot> existingSnapshotLookup,
      Map<TallySnapshotNaturalKey, List<TallySnapshot>> rollupLookup,
      Granularity granularity,
      Map<TallySnapshotNaturalKey, List<TallySnapshot>> snapshotMapping,
      DoubleBinaryOperator reductionFunction) {

    List<TallySnapshot> rollupsProduced = new ArrayList<>();
    snapshotMapping.forEach(
        (rollupKey, newAndUpdatedSnapshots) -> {
          List<TallySnapshot> existingContributingSnapshots =
              rollupLookup.getOrDefault(rollupKey, Collections.emptyList());
          rollupsProduced.addAll(
              produceRollups(
                  existingSnapshotLookup,
                  granularity,
                  existingContributingSnapshots,
                  newAndUpdatedSnapshots,
                  reductionFunction));
        });

    return rollupsProduced;
  }

  private List<TallySnapshot> produceRollups(
      Map<TallySnapshotNaturalKey, TallySnapshot> existingSnapshotLookup,
      Granularity granularity,
      List<TallySnapshot> existingContributingSnapshots,
      List<TallySnapshot> newAndUpdatedSnapshots,
      DoubleBinaryOperator reductionFunction) {

    Map<UsageCalculation.Key, Map<TallyMeasurementKey, Double>> reducedMeasurements =
        new HashMap<>();

    TallySnapshot firstFinestGranularitySnapshot =
        newAndUpdatedSnapshots.stream().findFirst().orElseThrow();

    // set used for deduping
    Set<TallySnapshot> allContributingSnapshots = new HashSet<>();
    allContributingSnapshots.addAll(existingContributingSnapshots);
    allContributingSnapshots.addAll(newAndUpdatedSnapshots);

    allContributingSnapshots.forEach(
        snapshot -> {
          var identifier =
              new UsageCalculation.Key(
                  snapshot.getProductId(),
                  snapshot.getServiceLevel(),
                  snapshot.getUsage(),
                  snapshot.getBillingProvider(),
                  snapshot.getBillingAccountId());
          reducedMeasurements.computeIfAbsent(identifier, i -> new HashMap<>());
          Map<TallyMeasurementKey, Double> measurements = reducedMeasurements.get(identifier);

          snapshot
              .getTallyMeasurements()
              .forEach(
                  (tallyMeasurementKey, measurementValue) -> {
                    Double currentTotal = measurements.getOrDefault(tallyMeasurementKey, 0.0);
                    measurements.put(
                        tallyMeasurementKey,
                        reductionFunction.applyAsDouble(currentTotal, measurementValue));
                  });
        });

    return updateTallySnapshots(
        existingSnapshotLookup, granularity, reducedMeasurements, firstFinestGranularitySnapshot);
  }

  private void updateSnapshotWithHardwareMeasurements(
      TallySnapshot snapshot,
      HardwareMeasurementType type,
      UsageCalculation.Totals calculatedTotals) {
    if (calculatedTotals != null) {
      log.debug("Updating snapshot with hardware measurement: {}", type);
      calculatedTotals
          .getMeasurements()
          .forEach((uom, value) -> snapshot.setMeasurement(type, uom, value));
    }
  }

  private List<TallySnapshot> updateTallySnapshots(
      Map<TallySnapshotNaturalKey, TallySnapshot> existingSnapshotLookup,
      Granularity granularity,
      Map<UsageCalculation.Key, Map<TallyMeasurementKey, Double>> reducedMeasurements,
      TallySnapshot firstFinestGranularitySnapshot) {
    List<TallySnapshot> saved = new ArrayList<>();

    reducedMeasurements.forEach(
        (usageKey, measurements) -> {
          OffsetDateTime snapshotDate =
              clock.calculateStartOfRange(
                  firstFinestGranularitySnapshot.getSnapshotDate(), granularity);
          var snapshotKey =
              new TallySnapshotNaturalKey(
                  firstFinestGranularitySnapshot.getOrgId(),
                  firstFinestGranularitySnapshot.getProductId(),
                  granularity,
                  usageKey.getSla(),
                  usageKey.getUsage(),
                  usageKey.getBillingProvider(),
                  usageKey.getBillingAccountId(),
                  snapshotDate);
          TallySnapshot existing = existingSnapshotLookup.get(snapshotKey);
          TallySnapshot snapshot = Objects.requireNonNullElseGet(existing, TallySnapshot::new);

          snapshot.setAccountNumber(firstFinestGranularitySnapshot.getAccountNumber());
          snapshot.setOrgId(firstFinestGranularitySnapshot.getOrgId());
          snapshot.setProductId(firstFinestGranularitySnapshot.getProductId());

          snapshot.setSnapshotDate(snapshotDate);
          snapshot.setGranularity(granularity);
          snapshot.setServiceLevel(usageKey.getSla());
          snapshot.setUsage(usageKey.getUsage());
          snapshot.setBillingAccountId(usageKey.getBillingAccountId());
          snapshot.setBillingProvider(usageKey.getBillingProvider());
          measurements.forEach(
              (measurementKey, value) ->
                  snapshot.setMeasurement(
                      measurementKey.getMeasurementType(),
                      MetricId.fromString(measurementKey.getMetricId()),
                      value));

          saved.add(tallyRepo.save(snapshot));
        });

    return saved;
  }
}
