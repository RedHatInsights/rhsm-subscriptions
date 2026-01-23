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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.redhat.swatch.configuration.registry.MetricId;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.candlepin.subscriptions.db.AccountServiceInventoryRepository;
import org.candlepin.subscriptions.db.EventRecordRepository;
import org.candlepin.subscriptions.db.HostRepository;
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.TallyStateRepository;
import org.candlepin.subscriptions.db.model.AccountServiceInventory;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.EventRecord;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.Host;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.TallyMeasurementKey;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.db.model.TallyState;
import org.candlepin.subscriptions.db.model.Usage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles({"worker", "test"})
class AccountResetServiceTest {
  @Autowired private EventRecordRepository eventRecordRepo;
  @Autowired private HostRepository hostRepo;
  @Autowired private TallySnapshotRepository tallySnapshotRepository;
  @Autowired private AccountServiceInventoryRepository accountServiceInventoryRepository;
  @Autowired private TallyStateRepository tallyStateRepository;
  @Autowired private AccountResetService resetService;

  @BeforeEach
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  void setUp() {
    hostRepo.deleteAll();
    accountServiceInventoryRepository.deleteAll();
    eventRecordRepo.deleteAll();
    tallySnapshotRepository.deleteAll();
    tallyStateRepository.deleteAll();
    accountServiceInventoryRepository.save(new AccountServiceInventory("org123", "HBI_HOST"));

    Host host = new Host("inventory123", "insights123", "org123", "subman123");
    host.setDisplayName("host123");
    host.addBucket(
        "RHEL",
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        BillingProvider.EMPTY,
        "billingAccount123",
        false,
        10,
        10,
        HardwareMeasurementType.PHYSICAL);
    host.setMeasurement("CORES", 10.0);
    host.addToMonthlyTotal("2024-08", MetricId.fromString("CORES"), 10.0);
    hostRepo.save(host);

    EventRecord e =
        new EventRecord(
            UUID.randomUUID(),
            "org123",
            "eventType123",
            "eventSource123",
            "instance123",
            UUID.randomUUID(),
            OffsetDateTime.now(),
            OffsetDateTime.now(),
            null);
    eventRecordRepo.save(e);

    TallySnapshot snapshot =
        TallySnapshot.builder()
            .id(null)
            .snapshotDate(OffsetDateTime.now())
            .productId("RHEL")
            .orgId("org123")
            .serviceLevel(ServiceLevel.PREMIUM)
            .usage(Usage.PRODUCTION)
            .billingProvider(BillingProvider.EMPTY)
            .billingAccountId("billingAccount123")
            .granularity(Granularity.DAILY)
            .tallyMeasurements(
                Map.of(new TallyMeasurementKey(HardwareMeasurementType.PHYSICAL, "CORES"), 10.0))
            .build();
    tallySnapshotRepository.save(snapshot);

    TallyState tallyState = new TallyState("org123", "HBI_HOST", OffsetDateTime.now());
    tallyStateRepository.save(tallyState);
  }

  @Test
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  void testReset() {
    assertDoesNotThrow(() -> resetService.deleteDataForOrg("org123"));
    assertEquals(0, hostRepo.findAll().size());
    assertEquals(0, accountServiceInventoryRepository.findAll().size());
    assertEquals(0, eventRecordRepo.findAll().size());
    assertEquals(0, tallySnapshotRepository.findAll().size());
    assertEquals(0, tallyStateRepository.findAll().size());
  }
}
