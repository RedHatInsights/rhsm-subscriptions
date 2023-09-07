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
package org.candlepin.subscriptions.db;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.candlepin.subscriptions.db.model.AccountBucketTally;
import org.candlepin.subscriptions.db.model.AccountServiceInventory;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.Host;
import org.candlepin.subscriptions.db.model.HostBucketKey;
import org.candlepin.subscriptions.db.model.HostTallyBucket;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.tally.InventoryAccountUsageCollector;
import org.candlepin.subscriptions.util.MetricIdUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
class HostTallyBucketRepositoryTest {

  @Autowired private AccountServiceInventoryRepository accountRepo;

  @Autowired private HostTallyBucketRepository bucketRepo;

  @Test
  @Transactional
  void testTallyHostBucketsQuery() {
    AccountServiceInventory account = new AccountServiceInventory("org123", "HBI_HOST");

    Host h1 = createHost("inv1", "org123");
    h1.addBucket(
        "P1",
        ServiceLevel._ANY,
        Usage._ANY,
        BillingProvider.RED_HAT,
        "redhat1",
        false,
        1,
        4,
        HardwareMeasurementType.PHYSICAL);
    account.getServiceInstances().put(h1.getInstanceId(), h1);

    Host h2 = createHost("inv2", "org123");
    h2.addBucket(
        "P1",
        ServiceLevel._ANY,
        Usage._ANY,
        BillingProvider.RED_HAT,
        "redhat1",
        false,
        2,
        8,
        HardwareMeasurementType.PHYSICAL);
    account.getServiceInstances().put(h2.getInstanceId(), h2);

    Host h3 = createHost("inv3", "org123");
    h3.addBucket(
        "P1",
        ServiceLevel._ANY,
        Usage._ANY,
        BillingProvider.RED_HAT,
        "redhat1",
        false,
        3,
        6,
        HardwareMeasurementType.PHYSICAL);
    account.getServiceInstances().put(h3.getInstanceId(), h3);

    // Should not be included in instances since there are no buckets assigned to the host.
    Host h4 = createHost("inv4", "org123");
    account.getServiceInstances().put(h4.getInstanceId(), h4);

    // Should be ignored since the instance type is not HBI_INSTANCE.
    AccountServiceInventory nonHBIServiceAccount = new AccountServiceInventory("org123", "NON_HBI");
    Host h5 = createHost("inv5", "org123");
    h5.setInstanceType("NON_HBI");
    h5.addBucket(
        "P1",
        ServiceLevel._ANY,
        Usage._ANY,
        BillingProvider.RED_HAT,
        "redhat1",
        false,
        3,
        6,
        HardwareMeasurementType.PHYSICAL);
    nonHBIServiceAccount.getServiceInstances().put(h5.getInstanceId(), h5);

    accountRepo.save(account);
    accountRepo.flush();

    Stream<AccountBucketTally> orgTally =
        bucketRepo.tallyHostBuckets("org123", InventoryAccountUsageCollector.HBI_INSTANCE_TYPE);
    List<AccountBucketTally> tallies = orgTally.collect(Collectors.toList());
    assertEquals(1, tallies.size());

    AccountBucketTally abt = tallies.get(0);
    assertBucketTally(abt, 6.0, 18.0, 3.0);
  }

  @Transactional
  @Test
  void testUnsetCoresAndSocketsResultInCountingZerosDuringTally() {
    AccountServiceInventory account = new AccountServiceInventory("org123", "HBI_HOST");

    Host h1 = createHost("inv1", "org123");
    HostTallyBucket bucket = new HostTallyBucket();
    bucket.setKey(
        new HostBucketKey(
            h1, "P1", ServiceLevel._ANY, Usage._ANY, BillingProvider.RED_HAT, "redhat1", false));
    bucket.setHost(h1);
    // Keep cores/sockets as unset
    bucket.setMeasurementType(HardwareMeasurementType.PHYSICAL);
    h1.addBucket(bucket);
    account.getServiceInstances().put(h1.getInstanceId(), h1);

    accountRepo.save(account);
    accountRepo.flush();

    Stream<AccountBucketTally> orgTally =
        bucketRepo.tallyHostBuckets("org123", InventoryAccountUsageCollector.HBI_INSTANCE_TYPE);
    List<AccountBucketTally> tallies = orgTally.collect(Collectors.toList());
    assertEquals(1, tallies.size());

    AccountBucketTally abt = tallies.get(0);
    assertBucketTally(abt, 0.0, 0.0, 1.0);
  }

  private void assertBucketTally(
      AccountBucketTally tally, Double expSockets, Double expCores, Double expInstanceCount) {
    assertEquals("P1", tally.getProductId());
    assertEquals(HardwareMeasurementType.PHYSICAL, tally.getMeasurementType());
    assertEquals(Usage._ANY, tally.getUsage());
    assertEquals(ServiceLevel._ANY, tally.getSla());
    assertEquals(BillingProvider.RED_HAT, tally.getBillingProvider());
    assertEquals("redhat1", tally.getBillingAccountId());
    assertEquals(expSockets, tally.getSockets());
    assertEquals(expCores, tally.getCores());
    assertEquals(expInstanceCount, tally.getInstances());
  }

  private Host createHost(String inventoryId, String orgId) {
    Host host =
        new Host(
            inventoryId,
            "INSIGHTS_" + inventoryId,
            orgId + "_ACCOUNT",
            orgId,
            "SUBMAN_" + inventoryId);
    host.setDisplayName(orgId);
    host.setMeasurement(MetricIdUtils.getSockets().getValue(), 1.0);
    host.setMeasurement(MetricIdUtils.getCores().getValue(), 1.0);
    return host;
  }
}
