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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.configuration.util.MetricIdUtils;
import java.util.List;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.tally.AccountUsageCalculation;
import org.candlepin.subscriptions.tally.UsageCalculation;
import org.candlepin.subscriptions.test.TestClockConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
// The transactional annotation will rollback the transaction at the end of every test.
@Transactional
@ActiveProfiles({"api", "test"})
@TestInstance(Lifecycle.PER_CLASS)
@Import(TestClockConfiguration.class)
class YearlySnapshotRollerTest {

  @Autowired private TallySnapshotRepository repository;
  @Autowired private ApplicationClock clock;

  private SnapshotRollerTester<YearlySnapshotRoller> tester;

  @BeforeEach
  void setupTest() {
    this.tester =
        new SnapshotRollerTester<>(repository, new YearlySnapshotRoller(repository, clock));
  }

  @Test
  void testYearlySnapshotProduction() {
    this.tester.performBasicSnapshotRollerTest(
        Granularity.YEARLY, clock.startOfCurrentYear(), clock.endOfCurrentYear());
  }

  @Test
  void testYearlySnapIsUpdatedWhenItAlreadyExists() {
    this.tester.performSnapshotUpdateTest(
        Granularity.YEARLY, clock.startOfCurrentYear(), clock.endOfCurrentYear());
  }

  @Test
  void ensureCurrentYearlyIsNotUpdatedWhenIncomingCalculationsAreLessThanTheExisting() {
    this.tester.performUpdateWithLesserValueTest(
        Granularity.YEARLY, clock.startOfCurrentYear(), clock.endOfCurrentYear(), true);
  }

  @Test
  @SuppressWarnings("java:S2699") /* Sonar thinks no assertions */
  void testHandlesDuplicates() {
    tester.performRemovesDuplicates(
        Granularity.YEARLY, clock.startOfCurrentYear(), clock.endOfCurrentYear());
  }

  @Test
  void testPrimaryFlagSetCorrectlyForTraditionalProduct() {
    String orgId = "org123";
    String product = "RHEL for x86";

    UsageCalculation.Key primaryKey =
        new UsageCalculation.Key(
            product, ServiceLevel.PREMIUM, Usage.PRODUCTION, BillingProvider._ANY, "_ANY");
    UsageCalculation primaryCalc = new UsageCalculation(primaryKey);
    primaryCalc.add(HardwareMeasurementType.PHYSICAL, MetricIdUtils.getCores(), 12.0);

    AccountUsageCalculation primaryAccountCalc = new AccountUsageCalculation(orgId);
    primaryAccountCalc.addCalculation(primaryCalc);

    YearlySnapshotRoller roller = new YearlySnapshotRoller(repository, clock);
    roller.rollSnapshots(primaryAccountCalc);

    List<TallySnapshot> primarySnapshots =
        repository
            .findSnapshot(
                orgId,
                product,
                Granularity.YEARLY,
                ServiceLevel.PREMIUM,
                Usage.PRODUCTION,
                BillingProvider._ANY,
                "_ANY",
                clock.startOfCurrentYear(),
                clock.endOfCurrentYear(),
                PageRequest.of(0, 100))
            .stream()
            .toList();

    assertEquals(1, primarySnapshots.size(), "Should have created exactly one snapshot");
    assertTrue(
        primarySnapshots.get(0).isPrimary(),
        "Traditional product with SLA/Usage specified should be primary");

    String orgId2 = "org456";
    UsageCalculation.Key nonPrimaryKey =
        new UsageCalculation.Key(
            product, ServiceLevel._ANY, Usage.PRODUCTION, BillingProvider._ANY, "_ANY");
    UsageCalculation nonPrimaryCalc = new UsageCalculation(nonPrimaryKey);
    nonPrimaryCalc.add(HardwareMeasurementType.PHYSICAL, MetricIdUtils.getCores(), 12.0);

    AccountUsageCalculation nonPrimaryAccountCalc = new AccountUsageCalculation(orgId2);
    nonPrimaryAccountCalc.addCalculation(nonPrimaryCalc);

    roller.rollSnapshots(nonPrimaryAccountCalc);

    List<TallySnapshot> nonPrimarySnapshots =
        repository
            .findSnapshot(
                orgId2,
                product,
                Granularity.YEARLY,
                ServiceLevel._ANY,
                Usage.PRODUCTION,
                BillingProvider._ANY,
                "_ANY",
                clock.startOfCurrentYear(),
                clock.endOfCurrentYear(),
                PageRequest.of(0, 100))
            .stream()
            .toList();

    assertEquals(1, nonPrimarySnapshots.size(), "Should have created exactly one snapshot");
    assertFalse(
        nonPrimarySnapshots.get(0).isPrimary(), "Snapshot with SLA _ANY should not be primary");
  }
}
