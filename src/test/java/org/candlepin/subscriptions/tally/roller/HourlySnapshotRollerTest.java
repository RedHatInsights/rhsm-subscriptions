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

import static org.candlepin.subscriptions.db.model.Granularity.HOURLY;
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
class HourlySnapshotRollerTest {

  @Autowired private TallySnapshotRepository repository;

  @Autowired private ApplicationClock clock;

  private SnapshotRollerTester<HourlySnapshotRoller> tester;

  @BeforeEach
  void setupAllTests() throws Exception {
    this.tester =
        new SnapshotRollerTester<>(repository, new HourlySnapshotRoller(repository, clock));
    this.tester.setTestProduct("OpenShift-dedicated-metrics");
  }

  @Test
  void testHourlySnapshotProducer() {
    this.tester.performBasicSnapshotRollerTest(
        HOURLY, clock.startOfCurrentHour(), clock.endOfCurrentHour());
  }

  @Test
  void testHourlySnapIsUpdatedWhenItAlreadyExists() {
    this.tester.performSnapshotUpdateTest(
        HOURLY, clock.startOfCurrentHour(), clock.endOfCurrentHour());
  }

  @Test
  void ensureCurrentHourlyUpdatedRegardlessOfWhetherIncomingCalculationsAreLessThanTheExisting() {
    tester.performUpdateWithLesserValueTest(
        HOURLY, clock.startOfCurrentHour(), clock.endOfCurrentHour(), false);
  }

  @Test
  @SuppressWarnings("java:S2699") /* Sonar thinks no assertions */
  void testHandlesDuplicates() {
    tester.performRemovesDuplicates(HOURLY, clock.startOfCurrentHour(), clock.endOfCurrentHour());
  }

  @Test
  void testPrimaryFlagSetCorrectly() {
    String orgId = "org123";
    String product = "OpenShift-dedicated-metrics";

    // Test primary record: all fields specified (PAYG requirement)
    UsageCalculation.Key primaryKey =
        new UsageCalculation.Key(
            product, ServiceLevel.PREMIUM, Usage.PRODUCTION, BillingProvider.AWS, "mktp-123");
    UsageCalculation primaryCalc = new UsageCalculation(primaryKey);
    primaryCalc.add(HardwareMeasurementType.PHYSICAL, MetricIdUtils.getCores(), 12.0);

    AccountUsageCalculation primaryAccountCalc = new AccountUsageCalculation(orgId);
    primaryAccountCalc.addCalculation(primaryCalc);

    HourlySnapshotRoller roller = new HourlySnapshotRoller(repository, clock);
    roller.rollSnapshots(primaryAccountCalc);

    List<TallySnapshot> primarySnapshots =
        repository
            .findSnapshot(
                orgId,
                product,
                Granularity.HOURLY,
                ServiceLevel.PREMIUM,
                Usage.PRODUCTION,
                BillingProvider.AWS,
                "mktp-123",
                clock.startOfCurrentHour(),
                clock.endOfCurrentHour(),
                PageRequest.of(0, 100))
            .stream()
            .toList();

    assertEquals(1, primarySnapshots.size(), "Should have created exactly one snapshot");
    assertTrue(primarySnapshots.getFirst().isPrimary());

    // Test non-primary record: has _ANY for billing provider
    String orgId2 = "org456";
    UsageCalculation.Key nonPrimaryKey =
        new UsageCalculation.Key(
            product, ServiceLevel.PREMIUM, Usage.PRODUCTION, BillingProvider._ANY, "mktp-123");
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
                Granularity.HOURLY,
                ServiceLevel.PREMIUM,
                Usage.PRODUCTION,
                BillingProvider._ANY,
                "mktp-123",
                clock.startOfCurrentHour(),
                clock.endOfCurrentHour(),
                PageRequest.of(0, 100))
            .stream()
            .toList();

    assertEquals(1, nonPrimarySnapshots.size(), "Should have created exactly one snapshot");
    assertFalse(
        nonPrimarySnapshots.getFirst().isPrimary(),
        "Snapshot with BillingProvider _ANY should not be primary");
  }
}
