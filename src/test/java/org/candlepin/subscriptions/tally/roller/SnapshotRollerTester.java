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

import static org.junit.jupiter.api.Assertions.*;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.json.Measurement;
import org.candlepin.subscriptions.json.Measurement.Uom;
import org.candlepin.subscriptions.tally.AccountUsageCalculation;
import org.candlepin.subscriptions.tally.UsageCalculation;
import org.candlepin.subscriptions.tally.UsageCalculation.Totals;
import org.springframework.data.domain.PageRequest;

/** Since the roller tests are very similar, this class provides some common test scenarios. */
@SuppressWarnings("linelength")
public class SnapshotRollerTester<R extends BaseSnapshotRoller> {
  private String testProduct = "RHEL for x86";

  private TallySnapshotRepository repository;
  private R roller;

  public SnapshotRollerTester(TallySnapshotRepository tallySnapshotRepository, R roller) {
    this.repository = tallySnapshotRepository;
    this.roller = roller;
  }

  public String getTestProduct() {
    return testProduct;
  }

  public void setTestProduct(String testProduct) {
    this.testProduct = testProduct;
  }

  @SuppressWarnings("indentation")
  public void performBasicSnapshotRollerTest(
      Granularity granularity,
      OffsetDateTime startOfGranularPeriod,
      OffsetDateTime endOfGranularPeriod) {
    AccountUsageCalculation a1Calc = createTestData();

    UsageCalculation a1ProductCalc = a1Calc.getCalculation(createUsageKey(getTestProduct()));
    roller.rollSnapshots(a1Calc);

    List<TallySnapshot> currentSnaps =
        repository
            .findSnapshot(
                a1Calc.getOrgId(),
                getTestProduct(),
                granularity,
                ServiceLevel.EMPTY,
                Usage.EMPTY,
                BillingProvider.EMPTY,
                "sellerAcct",
                startOfGranularPeriod,
                endOfGranularPeriod,
                PageRequest.of(0, 100))
            .stream()
            .collect(Collectors.toList());
    assertEquals(1, currentSnaps.size());
    assertSnapshot(currentSnaps.get(0), a1ProductCalc, granularity);
  }

  @SuppressWarnings("indentation")
  public void performSnapshotUpdateTest(
      Granularity granularity,
      OffsetDateTime startOfGranularPeriod,
      OffsetDateTime endOfGranularPeriod) {
    AccountUsageCalculation a1Calc = createTestData();

    String orgId = a1Calc.getOrgId();
    roller.rollSnapshots(a1Calc);

    List<TallySnapshot> currentSnaps =
        repository
            .findSnapshot(
                orgId,
                getTestProduct(),
                granularity,
                ServiceLevel.EMPTY,
                Usage.EMPTY,
                BillingProvider.EMPTY,
                "sellerAcct",
                startOfGranularPeriod,
                endOfGranularPeriod,
                PageRequest.of(0, 100))
            .stream()
            .collect(Collectors.toList());
    assertEquals(1, currentSnaps.size());
    TallySnapshot toBeUpdated = currentSnaps.get(0);

    UsageCalculation a1ProductCalc = a1Calc.getCalculation(createUsageKey(getTestProduct()));
    assertNotNull(a1ProductCalc);
    assertSnapshot(toBeUpdated, a1ProductCalc, granularity);

    a1ProductCalc.addPhysical(100, 200, 50);
    roller.rollSnapshots(a1Calc);

    List<TallySnapshot> updatedSnaps =
        repository
            .findSnapshot(
                orgId,
                getTestProduct(),
                granularity,
                ServiceLevel.EMPTY,
                Usage.EMPTY,
                BillingProvider.EMPTY,
                "sellerAcct",
                startOfGranularPeriod,
                endOfGranularPeriod,
                PageRequest.of(0, 100))
            .stream()
            .collect(Collectors.toList());
    assertEquals(1, updatedSnaps.size());

    TallySnapshot updated = updatedSnaps.get(0);
    assertEquals(toBeUpdated.getId(), updated.getId());
    assertSnapshot(updated, a1ProductCalc, granularity);
  }

  @SuppressWarnings("indentation")
  public void performUpdateWithLesserValueTest(
      Granularity granularity,
      OffsetDateTime startOfGranularPeriod,
      OffsetDateTime endOfGranularPeriod,
      boolean expectMaxAccepted) {
    int lowCores = 2;
    int lowSockets = 2;
    int lowInstances = 2;

    int highCores = 100;
    int highSockets = 200;
    int highInstances = 10;

    String account = "A1";
    String orgId = "01";
    AccountUsageCalculation a1HighCalc =
        createAccountCalc(account, orgId, getTestProduct(), highCores, highSockets, highInstances);
    AccountUsageCalculation a1LowCalc =
        createAccountCalc(account, orgId, getTestProduct(), lowCores, lowSockets, lowInstances);

    AccountUsageCalculation expectedCalc = expectMaxAccepted ? a1HighCalc : a1LowCalc;

    // Roll to the initial high values
    roller.rollSnapshots(a1HighCalc);

    List<TallySnapshot> currentSnaps =
        repository
            .findSnapshot(
                orgId,
                getTestProduct(),
                granularity,
                ServiceLevel.EMPTY,
                Usage.EMPTY,
                BillingProvider.EMPTY,
                "sellerAcct",
                startOfGranularPeriod,
                endOfGranularPeriod,
                PageRequest.of(0, 100))
            .stream()
            .collect(Collectors.toList());
    assertEquals(1, currentSnaps.size());

    TallySnapshot toUpdate = currentSnaps.get(0);
    assertSnapshot(
        toUpdate, a1HighCalc.getCalculation(createUsageKey(getTestProduct())), granularity);

    // Roll again with the low values
    roller.rollSnapshots(a1LowCalc);

    List<TallySnapshot> updatedSnaps =
        repository
            .findSnapshot(
                orgId,
                getTestProduct(),
                granularity,
                ServiceLevel.EMPTY,
                Usage.EMPTY,
                BillingProvider.EMPTY,
                "sellerAcct",
                startOfGranularPeriod,
                endOfGranularPeriod,
                PageRequest.of(0, 100))
            .stream()
            .collect(Collectors.toList());
    assertEquals(1, updatedSnaps.size());

    TallySnapshot updated = updatedSnaps.get(0);
    assertEquals(toUpdate.getId(), updated.getId());

    // Use the calculation with the expected
    assertSnapshot(
        updated, expectedCalc.getCalculation(createUsageKey(getTestProduct())), granularity);
  }

  public void performRemovesDuplicates(
      Granularity granularity,
      OffsetDateTime startOfGranularPeriod,
      OffsetDateTime endOfGranularPeriod) {

    AccountUsageCalculation a1Calc = createTestData();
    String account = a1Calc.getAccount();
    String orgId = a1Calc.getOrgId();

    TallySnapshot orig = new TallySnapshot();
    orig.setAccountNumber("my_account");
    orig.setOrgId(orgId);
    orig.setServiceLevel(ServiceLevel.EMPTY);
    orig.setUsage(Usage.EMPTY);
    orig.setBillingProvider(BillingProvider.EMPTY);
    orig.setBillingAccountId("sellerAcct");
    orig.setGranularity(granularity);
    orig.setSnapshotDate(startOfGranularPeriod);
    orig.setProductId(getTestProduct());

    TallySnapshot dupe = new TallySnapshot();
    dupe.setAccountNumber("my_account");
    dupe.setOrgId(orgId);
    dupe.setServiceLevel(ServiceLevel.EMPTY);
    dupe.setUsage(Usage.EMPTY);
    dupe.setBillingProvider(BillingProvider.EMPTY);
    dupe.setBillingAccountId("sellerAcct");
    dupe.setGranularity(granularity);
    dupe.setSnapshotDate(startOfGranularPeriod);
    dupe.setProductId(getTestProduct());

    repository.saveAll(List.of(orig, dupe));

    List<TallySnapshot> currentSnaps =
        repository
            .findSnapshot(
                orgId,
                getTestProduct(),
                granularity,
                ServiceLevel.EMPTY,
                Usage.EMPTY,
                BillingProvider.EMPTY,
                "sellerAcct",
                startOfGranularPeriod,
                endOfGranularPeriod,
                PageRequest.of(0, 100))
            .stream()
            .collect(Collectors.toList());
    assertEquals(2, currentSnaps.size());

    UsageCalculation a1ProductCalc = a1Calc.getCalculation(createUsageKey(getTestProduct()));
    assertNotNull(a1ProductCalc);

    roller.rollSnapshots(a1Calc);

    List<TallySnapshot> updatedSnaps =
        repository
            .findSnapshot(
                orgId,
                getTestProduct(),
                granularity,
                ServiceLevel.EMPTY,
                Usage.EMPTY,
                BillingProvider.EMPTY,
                "sellerAcct",
                startOfGranularPeriod,
                endOfGranularPeriod,
                PageRequest.of(0, 100))
            .stream()
            .collect(Collectors.toList());
    assertEquals(1, updatedSnaps.size());
  }

  private UsageCalculation.Key createUsageKey(String product) {
    return new UsageCalculation.Key(
        product, ServiceLevel.EMPTY, Usage.EMPTY, BillingProvider.EMPTY, "sellerAcct");
  }

  private AccountUsageCalculation createTestData() {
    return createAccountCalc("my_account", "O1", getTestProduct(), 12, 24, 6);
  }

  private AccountUsageCalculation createAccountCalc(
      String account,
      String orgId,
      String product,
      int totalCores,
      int totalSockets,
      int totalInstances) {
    UsageCalculation productCalc = new UsageCalculation(createUsageKey(product));
    Stream.of(
            HardwareMeasurementType.AWS,
            HardwareMeasurementType.PHYSICAL,
            HardwareMeasurementType.VIRTUAL,
            HardwareMeasurementType.HYPERVISOR)
        .forEach(
            type -> {
              productCalc.add(type, Measurement.Uom.CORES, (double) totalCores);
              productCalc.add(type, Measurement.Uom.SOCKETS, (double) totalSockets);
              productCalc.add(type, Uom.INSTANCES, (double) totalInstances);
            });

    AccountUsageCalculation calc = new AccountUsageCalculation(orgId);
    calc.setAccount(account);
    calc.addCalculation(productCalc);

    return calc;
  }

  private void assertSnapshot(
      TallySnapshot snapshot, UsageCalculation expectedVals, Granularity expectedGranularity) {
    assertNotNull(snapshot);
    assertEquals(expectedGranularity, snapshot.getGranularity());
    assertEquals(expectedVals.getProductId(), snapshot.getProductId());

    for (HardwareMeasurementType type : HardwareMeasurementType.values()) {
      Totals expectedTotal = expectedVals.getTotals(type);
      Arrays.stream(Measurement.Uom.values())
          .forEach(
              uom -> {
                assertEquals(
                    Optional.ofNullable(expectedTotal)
                        .map(totals -> totals.getMeasurement(uom))
                        .orElse(null),
                    snapshot.getMeasurement(type, uom));
              });
    }
  }
}
