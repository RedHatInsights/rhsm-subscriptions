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

import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.test.TestClockConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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
}
