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

import static org.junit.jupiter.api.Assertions.*;

import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.TallyMeasurementKey;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.db.model.Usage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
// The transactional annotation will rollback the transaction at the end of every test.
@Transactional
@TestInstance(Lifecycle.PER_CLASS)
@ActiveProfiles("test")
class TallySnapshotRepositoryTest {
  private static final OffsetDateTime LONG_AGO =
      OffsetDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault());
  private static final OffsetDateTime NOWISH =
      OffsetDateTime.of(2019, 06, 23, 00, 00, 00, 00, ZoneOffset.UTC);
  private static final OffsetDateTime FAR_FUTURE =
      OffsetDateTime.of(2099, 01, 01, 00, 00, 00, 00, ZoneOffset.UTC);

  @Autowired private TallySnapshotRepository repository;

  @Test
  void testSave() {
    TallySnapshot t =
        createUnpersisted(
            "orgHello", "Hello", "World", Granularity.DAILY, 2, 3, 4, OffsetDateTime.now());
    TallySnapshot saved = repository.saveAndFlush(t);
    assertNotNull(saved.getId());
  }

  @SuppressWarnings("linelength")
  @Test
  void findByOrgIdAndProductIdAndGranularityAndServiceLevelAndUsage() {
    TallySnapshot t1 =
        createUnpersisted("orgHello", "Hello", "World", Granularity.DAILY, 2, 3, 4, NOWISH);
    TallySnapshot t2 =
        createUnpersisted("orgBugs", "Bugs", "Bunny", Granularity.DAILY, 9999, 999, 99, NOWISH);
    TallySnapshot t3 =
        createUnpersisted(
            "orgBugs",
            "Bugs",
            "Bunny",
            Granularity.DAILY,
            ServiceLevel.STANDARD,
            Usage.PRODUCTION,
            BillingProvider._ANY,
            "sellerAcct",
            8888,
            888,
            88,
            NOWISH);

    repository.saveAll(Arrays.asList(t1, t2, t3));
    repository.flush();

    List<TallySnapshot> found =
        repository
            .findSnapshot(
                "orgBugs",
                "Bunny",
                Granularity.DAILY,
                ServiceLevel.STANDARD,
                Usage.PRODUCTION,
                BillingProvider._ANY,
                "sellerAcct",
                LONG_AGO,
                FAR_FUTURE,
                PageRequest.of(0, 10))
            .stream()
            .collect(Collectors.toList());
    assertEquals(1, found.size());
    TallySnapshot snapshot = found.get(0);
    assertEquals("orgBugs", snapshot.getOrgId());
    assertEquals("Bunny", snapshot.getProductId());
    assertEquals(Usage.PRODUCTION, snapshot.getUsage());
    assertEquals(NOWISH, found.get(0).getSnapshotDate());

    int cores =
        snapshot.getMeasurement(HardwareMeasurementType.TOTAL, MetricIdUtils.getCores()).intValue();
    assertEquals(8888, cores);
  }

  @SuppressWarnings("linelength")
  @Test
  void testFindByEmptyServiceLevelAndUsage() {
    TallySnapshot t1 =
        createUnpersisted(
            "orgA1",
            "A1",
            "P1",
            Granularity.DAILY,
            ServiceLevel.EMPTY,
            Usage.EMPTY,
            BillingProvider.EMPTY,
            "sellerAcct",
            1111,
            111,
            11,
            NOWISH);

    repository.saveAll(Arrays.asList(t1));
    repository.flush();

    List<TallySnapshot> found =
        repository
            .findSnapshot(
                "orgA1",
                "P1",
                Granularity.DAILY,
                ServiceLevel.EMPTY,
                Usage.EMPTY,
                BillingProvider.EMPTY,
                "sellerAcct",
                LONG_AGO,
                FAR_FUTURE,
                PageRequest.of(0, 10))
            .stream()
            .collect(Collectors.toList());
    assertEquals(1, found.size());
    TallySnapshot snapshot = found.get(0);
    assertEquals("orgA1", snapshot.getOrgId());
    assertEquals(NOWISH, found.get(0).getSnapshotDate());

    int cores =
        snapshot.getMeasurement(HardwareMeasurementType.TOTAL, MetricIdUtils.getCores()).intValue();
    assertEquals(1111, cores);
  }

  @Test
  void testFindByOrgIdInAndProductIdInAndGranularityAndSnapshotDateBetween() {
    String product1 = "Product1";
    String product2 = "Product2";
    // Will not be found - out of date range.
    TallySnapshot t1 =
        createUnpersisted("Org1", "Account1", product1, Granularity.DAILY, 2, 3, 4, LONG_AGO);
    // Will be found.
    TallySnapshot t2 =
        createUnpersisted("Org1", "Account1", product1, Granularity.DAILY, 9, 10, 11, NOWISH);
    // Will not be found, incorrect granularity
    TallySnapshot t3 =
        createUnpersisted("Org1", "Account1", product2, Granularity.WEEKLY, 19, 20, 21, NOWISH);
    // Will not be in result - Account not in query
    TallySnapshot t4 =
        createUnpersisted(
            "Org1", "Account2", product1, Granularity.DAILY, 99, 100, 101, FAR_FUTURE);
    // Will not be found - incorrect granularity
    TallySnapshot t5 =
        createUnpersisted("Org1", "Account1", product1, Granularity.WEEKLY, 20, 22, 23, NOWISH);

    repository.saveAll(Arrays.asList(t1, t2, t3, t4, t5));
    repository.flush();

    OffsetDateTime min = OffsetDateTime.of(2019, 05, 23, 00, 00, 00, 00, ZoneOffset.UTC);
    OffsetDateTime max = OffsetDateTime.of(2019, 07, 23, 00, 00, 00, 00, ZoneOffset.UTC);

    List<String> products = Arrays.asList(product1, product2);
    List<TallySnapshot> found =
        repository
            .findByOrgIdAndProductIdInAndGranularityAndSnapshotDateBetween(
                "Org1", products, Granularity.DAILY, min, max)
            .collect(Collectors.toList());
    assertEquals(1, found.size());

    TallySnapshot result = found.get(0);

    assertEquals(product1, result.getProductId());

    assertEquals(
        9,
        result.getMeasurement(HardwareMeasurementType.TOTAL, MetricIdUtils.getCores()).intValue());
    assertEquals(
        10,
        result
            .getMeasurement(HardwareMeasurementType.TOTAL, MetricIdUtils.getSockets())
            .intValue());
    assertEquals(
        11,
        result
            .getMeasurement(HardwareMeasurementType.TOTAL, MetricIdUtils.getInstanceHours())
            .intValue());
  }

  @Test
  void testPersistsHardwareMeasurements() {
    TallySnapshot snap =
        createUnpersisted(
            "OrgAcme", "Acme Inc.", "rocket-skates", Granularity.DAILY, 1, 2, 3, NOWISH);

    snap.setMeasurement(HardwareMeasurementType.PHYSICAL, MetricIdUtils.getCores(), 9.0);
    snap.setMeasurement(HardwareMeasurementType.PHYSICAL, MetricIdUtils.getSockets(), 8.0);
    snap.setMeasurement(HardwareMeasurementType.PHYSICAL, MetricIdUtils.getInstanceHours(), 7.0);

    repository.save(snap);
    repository.flush();

    List<TallySnapshot> found =
        repository
            .findByOrgIdAndProductIdInAndGranularityAndSnapshotDateBetween(
                "OrgAcme", Arrays.asList("rocket-skates"), Granularity.DAILY, LONG_AGO, FAR_FUTURE)
            .collect(Collectors.toList());

    TallySnapshot expected = found.get(0);

    assertEquals(
        9,
        expected
            .getMeasurement(HardwareMeasurementType.PHYSICAL, MetricIdUtils.getCores())
            .intValue());
    assertEquals(
        8,
        expected
            .getMeasurement(HardwareMeasurementType.PHYSICAL, MetricIdUtils.getSockets())
            .intValue());
    assertEquals(
        7,
        expected
            .getMeasurement(HardwareMeasurementType.PHYSICAL, MetricIdUtils.getInstanceHours())
            .intValue());
    assertEquals(
        Double.valueOf(1.0),
        expected.getMeasurement(HardwareMeasurementType.TOTAL, MetricIdUtils.getCores()));
  }

  private TallySnapshot createUnpersisted(
      String orgId,
      String account,
      String product,
      Granularity granularity,
      int cores,
      int sockets,
      int instances,
      OffsetDateTime date) {
    return createUnpersisted(
        orgId,
        account,
        product,
        granularity,
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        BillingProvider._ANY,
        "sellerAcct",
        cores,
        sockets,
        instances,
        date);
  }

  private TallySnapshot createUnpersisted(
      String orgId,
      String account,
      String product,
      Granularity granularity,
      ServiceLevel serviceLevel,
      Usage usage,
      BillingProvider billingProvider,
      String billingAcctId,
      int cores,
      int sockets,
      int instances,
      OffsetDateTime date) {
    TallySnapshot tally = new TallySnapshot();
    tally.setOrgId(orgId);
    tally.setProductId(product);
    tally.setGranularity(granularity);
    tally.setServiceLevel(serviceLevel);
    tally.setUsage(usage);
    tally.setBillingProvider(billingProvider);
    tally.setBillingAccountId(billingAcctId);
    tally.setSnapshotDate(date);
    tally.setServiceLevel(serviceLevel);

    tally.setMeasurement(HardwareMeasurementType.TOTAL, MetricIdUtils.getCores(), (double) cores);
    tally.setMeasurement(
        HardwareMeasurementType.TOTAL, MetricIdUtils.getSockets(), (double) sockets);
    tally.setMeasurement(
        HardwareMeasurementType.TOTAL, MetricIdUtils.getInstanceHours(), (double) instances);

    return tally;
  }

  @Test
  void testFindMonthlyTotal() {
    //    List<TallySnapshot> tallySnapshots = new ArrayList<>();
    String expectedOrgId = "Acme Inc.";
    String expectedProduct = "rocket-skates";
    Granularity expectedGranularity = Granularity.HOURLY;
    ServiceLevel expectedServiceLevel = ServiceLevel.PREMIUM;
    Usage expectedUsage = Usage.PRODUCTION;
    BillingProvider expectedBillingProvider = BillingProvider._ANY;
    String expectedBillingAccountId = "sellerAcct";
    HardwareMeasurementType expectedMeasurementType = HardwareMeasurementType.AWS;
    MetricId expectedMetricId = MetricIdUtils.getInstanceHours();

    loadIgnoredSequencedSnapshots();

    double testMeasurementValue = 2.0;
    List<TallySnapshot> snapshots =
        createSequencedSnapshots(
            NOWISH,
            5,
            expectedOrgId,
            expectedProduct,
            expectedGranularity,
            expectedMeasurementType,
            expectedMetricId,
            testMeasurementValue);

    OffsetDateTime beginning = NOWISH;
    OffsetDateTime ending = NOWISH.plusHours(snapshots.size());
    TallyMeasurementKey key =
        new TallyMeasurementKey(expectedMeasurementType, expectedMetricId.getValue());
    Double monthlyTotal =
        repository.sumMeasurementValueForPeriod(
            expectedOrgId,
            expectedProduct,
            expectedGranularity,
            expectedServiceLevel,
            expectedUsage,
            expectedBillingProvider,
            expectedBillingAccountId,
            beginning,
            ending,
            key);
    assertEquals(snapshots.size() * testMeasurementValue, monthlyTotal);
  }

  @Test
  void testFindMonthlyTotalForDateRange() {
    String expectedOrgId = "Acme Inc.";
    String expectedProduct = "rocket-skates";
    Granularity expectedGranularity = Granularity.HOURLY;
    ServiceLevel expectedServiceLevel = ServiceLevel.PREMIUM;
    Usage expectedUsage = Usage.PRODUCTION;
    BillingProvider expectedBillingProvider = BillingProvider._ANY;
    String expectedBillingAccountId = "sellerAcct";
    HardwareMeasurementType expectedMeasurementType = HardwareMeasurementType.AWS;
    MetricId expectedMetricId = MetricIdUtils.getInstanceHours();

    loadIgnoredSequencedSnapshots();

    double testMeasurementValue = 2.0;
    List<TallySnapshot> snapshots =
        createSequencedSnapshots(
            NOWISH,
            5,
            expectedOrgId,
            expectedProduct,
            expectedGranularity,
            expectedMeasurementType,
            expectedMetricId,
            testMeasurementValue);

    OffsetDateTime beginning = NOWISH;
    // Don't include the last snapshot.
    OffsetDateTime ending = NOWISH.plusHours(snapshots.size() - 2);
    TallyMeasurementKey key =
        new TallyMeasurementKey(
            HardwareMeasurementType.AWS, MetricIdUtils.getInstanceHours().getValue());
    Double monthlyTotal =
        repository.sumMeasurementValueForPeriod(
            expectedOrgId,
            expectedProduct,
            expectedGranularity,
            expectedServiceLevel,
            expectedUsage,
            expectedBillingProvider,
            expectedBillingAccountId,
            beginning,
            ending,
            key);
    assertEquals((snapshots.size() - 1) * testMeasurementValue, monthlyTotal);
  }

  @Test
  void testFindMonthlyTotalReturnsZeroWhenNothingFound() {
    TallyMeasurementKey key =
        new TallyMeasurementKey(
            HardwareMeasurementType.ALIBABA, MetricIdUtils.getInstanceHours().getValue());
    assertEquals(
        0.0,
        repository.sumMeasurementValueForPeriod(
            "org1",
            "p1",
            Granularity.DAILY,
            ServiceLevel.STANDARD,
            Usage.DEVELOPMENT_TEST,
            BillingProvider.AWS,
            "sa",
            NOWISH,
            NOWISH.plusHours(1),
            key));
  }

  private List<TallySnapshot> createSequencedSnapshots(
      OffsetDateTime start,
      int numOfSnaps,
      String orgId,
      String product,
      Granularity granularity,
      HardwareMeasurementType measurementType,
      MetricId measurementMetricId,
      Double measurementValue) {
    List<TallySnapshot> snaps = new ArrayList<>();
    OffsetDateTime next = start;
    for (int i = 1; i <= numOfSnaps; i++) {
      TallySnapshot snap =
          createUnpersisted(orgId, "accountSeq", product, granularity, 1, 2, 3, next);
      snap.setMeasurement(HardwareMeasurementType.PHYSICAL, MetricIdUtils.getCores(), 1.0);
      snap.setMeasurement(measurementType, measurementMetricId, measurementValue);
      snaps.add(snap);
      next = next.plusHours(1);
    }
    repository.saveAll(snaps);
    repository.flush();
    return snaps;
  }

  private void loadIgnoredSequencedSnapshots() {
    createSequencedSnapshots(
        NOWISH,
        5,
        "account1",
        "product1",
        Granularity.HOURLY,
        HardwareMeasurementType.AWS,
        MetricIdUtils.getCores(),
        2.0);
  }

  @Test
  void testHasLatestBillablesWhenMetricExists() {
    TallySnapshot snap =
        createUnpersisted(
            "OrgAcme", "Acme Inc.", "rocket-skates", Granularity.HOURLY, 1, 2, 3, NOWISH);

    snap.setMeasurement(HardwareMeasurementType.PHYSICAL, MetricIdUtils.getCores(), 9.0);
    snap.setMeasurement(HardwareMeasurementType.PHYSICAL, MetricIdUtils.getSockets(), 8.0);
    snap.setMeasurement(HardwareMeasurementType.PHYSICAL, MetricIdUtils.getInstanceHours(), 7.0);

    repository.save(snap);
    repository.flush();

    assertTrue(
        repository.hasLatestBillables(
            snap.getOrgId(),
            snap.getProductId(),
            snap.getServiceLevel(),
            snap.getUsage(),
            snap.getBillingProvider(),
            snap.getBillingAccountId(),
            NOWISH,
            NOWISH,
            new TallyMeasurementKey(
                HardwareMeasurementType.PHYSICAL, MetricIdUtils.getCores().getValue())));
  }

  @Test
  void testDoesNotHaveLatestBillablesWhenMetricDoesNotExists() {
    TallySnapshot snap =
        createUnpersisted(
            "OrgAcme", "Acme Inc.", "rocket-skates", Granularity.HOURLY, 1, 2, 3, NOWISH);

    snap.setMeasurement(HardwareMeasurementType.PHYSICAL, MetricIdUtils.getSockets(), 8.0);

    repository.save(snap);
    repository.flush();

    assertFalse(
        repository.hasLatestBillables(
            snap.getOrgId(),
            snap.getProductId(),
            snap.getServiceLevel(),
            snap.getUsage(),
            snap.getBillingProvider(),
            snap.getBillingAccountId(),
            NOWISH,
            NOWISH,
            new TallyMeasurementKey(
                HardwareMeasurementType.PHYSICAL, MetricIdUtils.getCores().getValue())));
  }
}
