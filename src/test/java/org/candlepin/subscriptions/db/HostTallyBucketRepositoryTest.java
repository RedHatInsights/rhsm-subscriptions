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

import com.redhat.swatch.configuration.util.MetricIdUtils;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.candlepin.subscriptions.db.model.AccountBucketTally;
import org.candlepin.subscriptions.db.model.AccountServiceInventory;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.DbReportCriteria;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.Host;
import org.candlepin.subscriptions.db.model.HostBucketKey;
import org.candlepin.subscriptions.db.model.HostTallyBucket;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.tally.InventoryAccountUsageCollector;
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
    assertBucketTally(abt, null, null, 1.0);
  }

  @Transactional
  @Test
  void testBillingAccountIdsOrgFilter() {
    prepareBillingAccountTests();
    List<HostTallyBucketRepository.BillingAccountIdRecord> records =
        bucketRepo.billingAccountIds(DbReportCriteria.builder().orgId("org1").build());
    // one is excluded for duplicate values
    assertEquals(3, records.size());
    // confirm sort
    assertEquals(BillingProvider.AWS, records.get(0).billingProvider());
    assertEquals(BillingProvider.AZURE, records.get(1).billingProvider());
    assertEquals(BillingProvider.RED_HAT, records.get(2).billingProvider());
    records = bucketRepo.billingAccountIds(DbReportCriteria.builder().orgId("org2").build());
    assertEquals(4, records.size());
    // confirm sort
    assertEquals("account1", records.get(2).billingAccountId());
    assertEquals("account2", records.get(3).billingAccountId());
  }

  @Transactional
  @Test
  void testBillingAccountIdsUnique() {
    prepareBillingAccountTests();
    List<HostTallyBucketRepository.BillingAccountIdRecord> records =
        bucketRepo.billingAccountIds(
            DbReportCriteria.builder()
                .orgId("org1")
                .productTag("prod1")
                .billingProvider(BillingProvider.RED_HAT)
                .build());
    assertEquals(1, records.size());
    assertEquals("prod1", records.get(0).productId());
    assertEquals(BillingProvider.RED_HAT, records.get(0).billingProvider());
  }

  @Transactional
  @Test
  void testBillingAccountProductTagFilter() {
    prepareBillingAccountTests();
    List<HostTallyBucketRepository.BillingAccountIdRecord> records =
        bucketRepo.billingAccountIds(
            DbReportCriteria.builder().orgId("org2").productTag("prod3").build());
    assertEquals(2, records.size());
    records.stream()
        .forEach(
            accountIdRecord -> {
              assertEquals("prod3", records.get(0).productId());
            });
  }

  @Transactional
  @Test
  void testBillingAccountBillingProviderFilter() {
    prepareBillingAccountTests();
    List<HostTallyBucketRepository.BillingAccountIdRecord> records =
        bucketRepo.billingAccountIds(
            DbReportCriteria.builder()
                .orgId("org2")
                .billingProvider(BillingProvider.RED_HAT)
                .build());
    assertEquals(2, records.size());
    records.stream()
        .forEach(
            accountIdRecord -> {
              assertEquals(BillingProvider.RED_HAT, records.get(0).billingProvider());
            });
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
    Host host = new Host(inventoryId, "INSIGHTS_" + inventoryId, orgId, "SUBMAN_" + inventoryId);
    host.setDisplayName(orgId);
    host.setMeasurement(MetricIdUtils.getSockets().getValue(), 1.0);
    host.setMeasurement(MetricIdUtils.getCores().getValue(), 1.0);
    host.setLastSeen(OffsetDateTime.now());
    return host;
  }

  private Host createOldHost(String inventoryId, String orgId) {
    Host host =
        new Host(inventoryId + "_old", "INSIGHTS_" + inventoryId, orgId, "SUBMAN_" + inventoryId);
    host.setDisplayName(orgId);
    host.setMeasurement(MetricIdUtils.getSockets().getValue(), 1.0);
    host.setMeasurement(MetricIdUtils.getCores().getValue(), 1.0);
    host.setLastSeen(OffsetDateTime.now().minusMonths(1));
    return host;
  }

  record BillingAccountIdTestRecord(
      String inventoryId,
      String orgId,
      String productId,
      BillingProvider billingProvider,
      String billingAccountId) {}

  List<BillingAccountIdTestRecord> billingAccountIdTestRecords =
      List.of(
          new BillingAccountIdTestRecord(
              "inv1", "org1", "prod1", BillingProvider.RED_HAT, "account1"),
          new BillingAccountIdTestRecord("inv2", "org1", "prod2", BillingProvider.AWS, "account2"),
          new BillingAccountIdTestRecord(
              "inv3", "org1", "prod1", BillingProvider.RED_HAT, "account1"),
          new BillingAccountIdTestRecord(
              "inv4", "org1", "prod3", BillingProvider.AZURE, "account4"),
          new BillingAccountIdTestRecord(
              "inv5", "org2", "prod1", BillingProvider.RED_HAT, "account1"),
          new BillingAccountIdTestRecord(
              "inv6", "org2", "prod3", BillingProvider.RED_HAT, "account2"),
          new BillingAccountIdTestRecord(
              "inv7", "org2", "prod1", BillingProvider.AZURE, "account3"),
          new BillingAccountIdTestRecord("inv8", "org2", "prod3", BillingProvider.AWS, "account4"));

  private void prepareBillingAccountTests() {
    AccountServiceInventory account1 = new AccountServiceInventory("org1", "HBI_HOST");
    AccountServiceInventory account2 = new AccountServiceInventory("org2", "HBI_HOST");

    billingAccountIdTestRecords.stream()
        .forEach(
            testRecord -> {
              Stream.of(
                      createHost(testRecord.inventoryId, testRecord.orgId),
                      createOldHost(testRecord.inventoryId, testRecord.orgId))
                  .forEach(
                      host -> {
                        HostTallyBucket bucket = new HostTallyBucket();
                        bucket.setKey(
                            new HostBucketKey(
                                host,
                                testRecord.productId,
                                ServiceLevel._ANY,
                                Usage._ANY,
                                testRecord.billingProvider,
                                testRecord.billingAccountId,
                                false));
                        bucket.setHost(host);
                        host.addBucket(bucket);
                        if (testRecord.orgId == "org1") {
                          account1.getServiceInstances().put(host.getInstanceId(), host);
                        } else {
                          account2.getServiceInstances().put(host.getInstanceId(), host);
                        }
                      });
            });
    accountRepo.save(account1);
    accountRepo.save(account2);
    accountRepo.flush();
  }
}
