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
package org.candlepin.subscriptions.tally.roller;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.HardwareMeasurement;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.registry.ProductProfileRegistry;
import org.candlepin.subscriptions.tally.AccountUsageCalculation;
import org.candlepin.subscriptions.tally.UsageCalculation;
import org.candlepin.subscriptions.tally.UsageCalculation.Totals;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for all usage snapshot rollers. A snapshot roller is responsible compressing finer
 * granularity snapshots into more compressed snapshots. For example, rolling daily snapshots into
 * weekly snapshots or rolling weekly snapshots into monthly snapshots.
 */
public abstract class BaseSnapshotRoller {

  private static final Logger log = LoggerFactory.getLogger(BaseSnapshotRoller.class);

  protected TallySnapshotRepository tallyRepo;
  protected ApplicationClock clock;
  protected final ProductProfileRegistry productProfileRegistry;

  protected BaseSnapshotRoller(
      TallySnapshotRepository tallyRepo, ApplicationClock clock, ProductProfileRegistry registry) {
    this.tallyRepo = tallyRepo;
    this.clock = clock;
    this.productProfileRegistry = registry;
  }

  /**
   * Roll the snapshots for the given account.
   *
   * @param accounts the accounts of the snapshots to roll.
   * @param accountCalcs the current calculations from the host inventory.
   * @return collection of snapshots
   */
  public abstract Collection<TallySnapshot> rollSnapshots(
      Collection<String> accounts, Collection<AccountUsageCalculation> accountCalcs);

  protected TallySnapshot createSnapshotFromProductUsageCalculation(
      String account, String owner, UsageCalculation productCalc, Granularity granularity) {
    TallySnapshot snapshot = new TallySnapshot();
    snapshot.setProductId(productCalc.getProductId());
    snapshot.setServiceLevel(productCalc.getSla());
    snapshot.setUsage(productCalc.getUsage());
    snapshot.setGranularity(granularity);
    snapshot.setOwnerId(owner);
    snapshot.setAccountNumber(account);
    snapshot.setSnapshotDate(getSnapshotDate(granularity));

    // Copy the calculated hardware measurements to the snapshots
    for (HardwareMeasurementType type : HardwareMeasurementType.values()) {
      Totals calculatedTotals = productCalc.getTotals(type);
      if (calculatedTotals != null) {
        log.debug("Updating snapshot with hardware measurement: {}", type);
        HardwareMeasurement total = new HardwareMeasurement();
        total.setCores(calculatedTotals.getCores());
        total.setSockets(calculatedTotals.getSockets());
        total.setInstanceCount(calculatedTotals.getInstances());
        snapshot.setHardwareMeasurement(type, total);
        calculatedTotals
            .getMeasurements()
            .forEach((uom, value) -> snapshot.setMeasurement(type, uom, value));
      } else {
        log.debug("Skipping hardware measurement {} since it was not found.", type);
      }
    }

    return snapshot;
  }

  protected OffsetDateTime getSnapshotDate(Granularity granularity) {
    switch (granularity) {
      case HOURLY:
        return clock.startOfCurrentHour();
      case DAILY:
        return clock.startOfToday();
      case WEEKLY:
        return clock.startOfCurrentWeek();
      case MONTHLY:
        return clock.startOfCurrentMonth();
      case QUARTERLY:
        return clock.startOfCurrentQuarter();
      case YEARLY:
        return clock.startOfCurrentYear();
      default:
        throw new IllegalArgumentException(
            String.format("Unsupported granularity: %s", granularity));
    }
  }

  @SuppressWarnings("indentation")
  protected Map<String, List<TallySnapshot>> getCurrentSnapshotsByAccount(
      Collection<String> accounts,
      Collection<String> products,
      Granularity granularity,
      OffsetDateTime begin,
      OffsetDateTime end) {
    try (Stream<TallySnapshot> snapStream =
        tallyRepo.findByAccountNumberInAndProductIdInAndGranularityAndSnapshotDateBetween(
            accounts, products, granularity, begin, end)) {
      return snapStream.collect(Collectors.groupingBy(TallySnapshot::getAccountNumber));
    }
  }

  protected Collection<TallySnapshot> updateSnapshots(
      Collection<AccountUsageCalculation> accountCalcs,
      Map<String, List<TallySnapshot>> existingSnaps,
      Granularity targetGranularity) {
    List<TallySnapshot> snaps = new LinkedList<>();
    for (AccountUsageCalculation accountCalc : accountCalcs) {
      String account = accountCalc.getAccount();

      Map<UsageCalculation.Key, TallySnapshot> accountSnapsByUsageKey = new HashMap<>();
      if (existingSnaps.containsKey(account)) {
        accountSnapsByUsageKey =
            existingSnaps.get(account).stream()
                .collect(
                    Collectors.toMap(
                        UsageCalculation.Key::fromTallySnapshot,
                        Function.identity(),
                        this::handleDuplicateSnapshot));
      }

      for (UsageCalculation.Key usageKey : accountCalc.getKeys()) {
        boolean isGranularitySupported =
            productProfileRegistry
                .findProfileForSwatchProductId(usageKey.getProductId())
                .supportsGranularity(targetGranularity);

        if (isGranularitySupported) {
          TallySnapshot snap = accountSnapsByUsageKey.get(usageKey);
          UsageCalculation productCalc = accountCalc.getCalculation(usageKey);
          if (snap == null && productCalc.hasMeasurements()) {
            snap =
                createSnapshotFromProductUsageCalculation(
                    accountCalc.getAccount(),
                    accountCalc.getOwner(),
                    productCalc,
                    targetGranularity);
            snaps.add(snap);
          } else if (snap != null && updateMaxValues(snap, productCalc)) {
            snaps.add(snap);
          }
        }
      }
    }
    log.debug("Persisting {} {} snapshots.", snaps.size(), targetGranularity);
    return tallyRepo.saveAll(snaps);
  }

  private TallySnapshot handleDuplicateSnapshot(TallySnapshot snap1, TallySnapshot snap2) {
    log.warn(
        "Removing duplicate TallySnapshot granularity: {}, key: {}",
        snap2.getGranularity(),
        UsageCalculation.Key.fromTallySnapshot(snap2));
    tallyRepo.delete(snap2);
    return snap1;
  }

  protected Set<String> getApplicableProducts(
      Collection<AccountUsageCalculation> accountCalcs, Granularity granularity) {
    Set<String> prods = new HashSet<>();

    for (AccountUsageCalculation calc : accountCalcs) {
      Stream<String> prodStream = calc.getProducts().stream();
      Set<String> matchingProds =
          prodStream
              .filter(
                  p ->
                      productProfileRegistry
                          .findProfileForSwatchProductId(p)
                          .supportsGranularity(granularity))
              .collect(Collectors.toSet());
      prods.addAll(matchingProds);
    }

    return prods;
  }

  private boolean isFinestGranularity(TallySnapshot snap) {
    Granularity finestGranularity = getFinestGranularity(snap);
    return finestGranularity.equals(snap.getGranularity());
  }

  private boolean updateMaxValues(TallySnapshot snap, UsageCalculation calc) {
    boolean changed = false;

    boolean overrideMaxCheck = isFinestGranularity(snap);

    for (HardwareMeasurementType type : HardwareMeasurementType.values()) {
      changed |= updateTotals(overrideMaxCheck, snap, type, calc);
    }
    return changed;
  }

  private Granularity getFinestGranularity(TallySnapshot snap) {
    return productProfileRegistry
        .findProfileForSwatchProductId(snap.getProductId())
        .getFinestGranularity();
  }

  private boolean updateTotals(
      boolean override,
      TallySnapshot snap,
      HardwareMeasurementType measurementType,
      UsageCalculation calc) {

    Totals prodCalcTotals = calc.getTotals(measurementType);
    HardwareMeasurement measurement = snap.getHardwareMeasurement(measurementType);

    // Nothing to update if the existing measure does not exist and there
    // was no new incoming measurement.
    if (measurement == null && prodCalcTotals == null) {
      return false;
    }

    boolean changed = false;

    // If the calculated values for the measurement do not exist, zero them out
    // for the snapshot update. Daily snapshots will have the values reset to zero.
    // All other snapshots will take the existing value.
    int calcSockets = prodCalcTotals != null ? prodCalcTotals.getSockets() : 0;
    int calcCores = prodCalcTotals != null ? prodCalcTotals.getCores() : 0;
    int calcInstanceCount = prodCalcTotals != null ? prodCalcTotals.getInstances() : 0;

    if (measurement == null) {
      // All the int fields in measurement will be initialized to zero
      measurement = new HardwareMeasurement();
    }

    if (override || mustUpdate(measurement.getCores(), calcCores)) {
      measurement.setCores(calcCores);
      changed = true;
    }

    if (override || mustUpdate(measurement.getSockets(), calcSockets)) {
      measurement.setSockets(calcSockets);
      changed = true;
    }

    if (override || mustUpdate(measurement.getInstanceCount(), calcInstanceCount)) {
      measurement.setInstanceCount(calcInstanceCount);
      changed = true;
    }

    updateUomTotals(override, snap, measurementType, prodCalcTotals);

    if (changed) {
      snap.setHardwareMeasurement(measurementType, measurement);
    }

    return changed;
  }

  private void updateUomTotals(
      boolean override,
      TallySnapshot snap,
      HardwareMeasurementType measurementType,
      Totals prodCalcTotals) {
    if (prodCalcTotals != null) {
      prodCalcTotals
          .getMeasurements()
          .forEach(
              (uom, value) -> {
                Double prodCalcMeasurement = prodCalcTotals.getMeasurement(uom);
                if (override
                    || mustUpdate(snap.getMeasurement(measurementType, uom), prodCalcMeasurement)) {
                  snap.setMeasurement(measurementType, uom, prodCalcMeasurement);
                }
              });
    }
  }

  private boolean mustUpdate(Double existing, Double newMeasurment) {
    return existing == null || newMeasurment > existing;
  }

  private boolean mustUpdate(Integer v1, Integer v2) {
    return v1 == null || v2 > v1;
  }
}
