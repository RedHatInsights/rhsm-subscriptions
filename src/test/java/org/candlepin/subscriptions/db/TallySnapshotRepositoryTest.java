/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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

import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.HardwareMeasurement;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.db.model.TallySnapshotSummation;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@SpringBootTest
// The transactional annotation will rollback the transaction at the end of every test.
@Transactional
@TestPropertySource("classpath:/test.properties")
@TestInstance(Lifecycle.PER_CLASS)
public class TallySnapshotRepositoryTest {
    private static final OffsetDateTime LONG_AGO = OffsetDateTime.ofInstant(Instant.EPOCH,
        ZoneId.systemDefault());
    private static final OffsetDateTime NOWISH = OffsetDateTime.of(2019, 06, 23, 00, 00, 00, 00,
        ZoneOffset.UTC);
    private static final OffsetDateTime FAR_FUTURE = OffsetDateTime.of(2099, 01, 01, 00, 00, 00, 00,
        ZoneOffset.UTC);

    @Autowired private TallySnapshotRepository repository;

    private static final String PRODUCT_1 = "Product1";
    private static final String PRODUCT_2 = "Product2";
    private static final String SUM_PRODUCT_1 = "SUM_PRODUCT_1";

    // Set up some test data that all tests can use. Any one off test scenarios should create
    // the data it needs inside the test itself.
    @BeforeAll
    public void setupTestData() {
        // The snapshot producer will create a set of snapshots for an account (per product, per
        // attribute combonation). Simulate that here for SLA.
        Map<HardwareMeasurementType, HardwareMeasurement> a1PremiumMeasurements = new HashMap<>();
        a1PremiumMeasurements.put(HardwareMeasurementType.TOTAL, createMeasurement(18, 10, 10));
        a1PremiumMeasurements.put(HardwareMeasurementType.PHYSICAL, createMeasurement(12, 4, 4));
        a1PremiumMeasurements.put(HardwareMeasurementType.HYPERVISOR, createMeasurement(4, 4, 4));
        a1PremiumMeasurements.put(HardwareMeasurementType.AWS, createMeasurement(2, 2, 2));
        TallySnapshot a1Premium = createSnapshot("Account1", SUM_PRODUCT_1, Granularity.DAILY, "Premium",
            NOWISH, a1PremiumMeasurements);

        Map<HardwareMeasurementType, HardwareMeasurement> a1StandardMeasurements = new HashMap<>();
        a1StandardMeasurements.put(HardwareMeasurementType.TOTAL, createMeasurement(12, 10, 5));
        a1StandardMeasurements.put(HardwareMeasurementType.PHYSICAL, createMeasurement(4, 2, 1));
        a1StandardMeasurements.put(HardwareMeasurementType.HYPERVISOR, createMeasurement(6, 4, 2));
        a1StandardMeasurements.put(HardwareMeasurementType.AWS, createMeasurement(2, 4, 2));
        TallySnapshot a1Standard = createSnapshot("Account1", SUM_PRODUCT_1, Granularity.DAILY, "Standard",
            NOWISH, a1StandardMeasurements);

        Map<HardwareMeasurementType, HardwareMeasurement> a1NoSLAMeasurements = new HashMap<>();
        a1NoSLAMeasurements.put(HardwareMeasurementType.TOTAL, createMeasurement(2, 2, 1));
        a1NoSLAMeasurements.put(HardwareMeasurementType.PHYSICAL, createMeasurement(2, 2, 1));
        TallySnapshot a1NoSLA = createSnapshot("Account1", SUM_PRODUCT_1, Granularity.DAILY, null,
            NOWISH, a1NoSLAMeasurements);

        // Since this snapshot is for a different date it should remain on its own, but be included in the
        // result since it still matches the account/product/granularity for the sum query below.
        Map<HardwareMeasurementType, HardwareMeasurement> a1StdPastMeasurements = new HashMap<>();
        a1StdPastMeasurements.put(HardwareMeasurementType.TOTAL, createMeasurement(10, 11, 2));
        a1StdPastMeasurements.put(HardwareMeasurementType.PHYSICAL, createMeasurement(5, 10, 1));
        a1StdPastMeasurements.put(HardwareMeasurementType.HYPERVISOR, createMeasurement(5, 1, 1));
        TallySnapshot a1StandardPast = createSnapshot("Account1", SUM_PRODUCT_1, Granularity.DAILY,
            "Standard", LONG_AGO, a1StdPastMeasurements);

        repository.saveAll(Arrays.asList(a1Premium, a1Standard, a1StandardPast, a1NoSLA));
        repository.flush();
    }

    @Test
    public void testSave() {
        TallySnapshot t = createSnapshot("Hello", "World", Granularity.DAILY, "Premium", 2, 3, 4,
            OffsetDateTime.now());
        TallySnapshot saved = repository.saveAndFlush(t);
        assertNotNull(saved.getId());
    }

    @Test
    public void testFindByAccountNumberInAndProductIdInAndGranularityAndSnapshotDateBetween() {
        String product1 = "Product1";
        String product2 = "Product2";

        repository.saveAll(Arrays.asList(
            // Will not be found - out of date range.
            createSnapshot("Account1", product1, Granularity.DAILY, "Premium", 2, 3, 4, LONG_AGO),
            // Will be found.
            createSnapshot("Account2", product1, Granularity.DAILY, "Premium", 9, 10, 11, NOWISH),
            // Will be found.
            createSnapshot("Account2", product2, Granularity.DAILY, "Premium", 19, 20, 21, NOWISH),
            // Will not be found, incorrect granularity
            createSnapshot("Account2", product2, Granularity.WEEKLY, "Premium", 19, 20, 21, NOWISH),
            // Will not be in result - Account not in query
            createSnapshot("Account3", product1, Granularity.DAILY, "Premium", 99, 100, 101, FAR_FUTURE),
            // Will not be found - incorrect granularity
            createSnapshot("Account2", product1, Granularity.WEEKLY, "Premium", 20, 22, 23, NOWISH)
        ));
        repository.flush();

        OffsetDateTime min = OffsetDateTime.of(2019, 05, 23, 00, 00, 00, 00,
            ZoneOffset.UTC);
        OffsetDateTime max = OffsetDateTime.of(2019, 07, 23, 00, 00, 00, 00,
            ZoneOffset.UTC);

        List<String> accounts = Arrays.asList("Account1", "Account2");
        List<String> products = Arrays.asList(product1, product2);
        List<TallySnapshot> found =
            repository.findByAccountNumberInAndProductIdInAndGranularityAndSnapshotDateBetween(accounts,
            products, Granularity.DAILY, min, max).collect(Collectors.toList());

        assertEquals(2, found.size());

        TallySnapshot result = found.get(0);

        assertEquals("Account2", result.getAccountNumber());
        assertEquals(product1, result.getProductId());

        HardwareMeasurement total = result.getHardwareMeasurement(HardwareMeasurementType.TOTAL);
        assertEquals(9, total.getCores());
        assertEquals(10, total.getSockets());
        assertEquals(11, total.getInstanceCount());
    }

    @Test
    public void testPersistsHardwareMeasurements() {
        TallySnapshot snap = createSnapshot("Acme Inc.", "rocket-skates", Granularity.DAILY, "Premium",
            1, 2, 3, NOWISH);

        HardwareMeasurement physical = new HardwareMeasurement();
        physical.setCores(9);
        physical.setSockets(8);
        physical.setInstanceCount(7);
        snap.setHardwareMeasurement(HardwareMeasurementType.PHYSICAL, physical);

        repository.save(snap);
        repository.flush();

        List<TallySnapshot> found = repository
            .findByAccountNumberInAndProductIdInAndGranularityAndSnapshotDateBetween(
            Arrays.asList("Acme Inc."), Arrays.asList("rocket-skates"), Granularity.DAILY, LONG_AGO,
            FAR_FUTURE).collect(Collectors.toList());

        TallySnapshot expected = found.get(0);
        HardwareMeasurement physicalResult =
            expected.getHardwareMeasurement(HardwareMeasurementType.PHYSICAL);

        assertEquals(9, physicalResult.getCores());
        assertEquals(8, physicalResult.getSockets());
        assertEquals(7, physicalResult.getInstanceCount());
    }

    @Test
    public void testSumSnapshotMeasurementsWithNoFilters() {
        // NOTE: The overall total for all the systems in Account1 should be the total of all the snapshots
        // (totaled per measurement) with no filters.
        List<TallySnapshotSummation> found = repository.sumSnapshotMeasurements(
            "Account1",
            SUM_PRODUCT_1,
            Granularity.DAILY,
            null,
            LONG_AGO,
            FAR_FUTURE,
            false,
            PageRequest.of(0, 10)
        ).stream().collect(Collectors.toList());

        // Group by date.
        Map<OffsetDateTime, List<TallySnapshotSummation>> summationBySla =
            found.stream().collect(Collectors.groupingBy(TallySnapshotSummation::getSnapshotDate));
        assertEquals(2, summationBySla.keySet().size());

        // TODO Date is converted to local time when coming back from the DB,\
        //      so convert it back. Can we address this?
        OffsetDateTime nowish = OffsetDateTime.from(NOWISH.atZoneSameInstant(ZoneOffset.systemDefault()));
        // Make sure that we contain the expected measurement groups
        assertTrue(summationBySla.containsKey(nowish));
        assertTrue(summationBySla.containsKey(LONG_AGO));

        Map<HardwareMeasurementType, TallySnapshotSummation> nowishSums = summationBySla.get(nowish)
            .stream().collect(Collectors.toMap(TallySnapshotSummation::getType, Function.identity()));

        assertEquals(4, nowishSums.keySet().size());
        assertTrue(nowishSums.keySet().containsAll(Arrays.asList(
            HardwareMeasurementType.TOTAL,
            HardwareMeasurementType.PHYSICAL,
            HardwareMeasurementType.HYPERVISOR,
            HardwareMeasurementType.AWS
        )));

        assertSummation(nowishSums.get(HardwareMeasurementType.TOTAL), "Account1", SUM_PRODUCT_1,
            HardwareMeasurementType.TOTAL, 32, 22, 16);
        assertSummation(nowishSums.get(HardwareMeasurementType.PHYSICAL), "Account1", SUM_PRODUCT_1,
            HardwareMeasurementType.PHYSICAL, 18, 8, 6);
        assertSummation(nowishSums.get(HardwareMeasurementType.HYPERVISOR), "Account1", SUM_PRODUCT_1,
            HardwareMeasurementType.HYPERVISOR, 10, 8, 6);
        assertSummation(nowishSums.get(HardwareMeasurementType.AWS), "Account1", SUM_PRODUCT_1,
            HardwareMeasurementType.AWS, 4, 6, 4);

        Map<HardwareMeasurementType, TallySnapshotSummation> longAgoSums = summationBySla.get(LONG_AGO)
            .stream().collect(Collectors.toMap(TallySnapshotSummation::getType, Function.identity()));
        assertEquals(3, longAgoSums.keySet().size());
        assertTrue(longAgoSums.keySet().containsAll(Arrays.asList(
            HardwareMeasurementType.TOTAL,
            HardwareMeasurementType.PHYSICAL,
            HardwareMeasurementType.HYPERVISOR
        )));

        assertSummation(longAgoSums.get(HardwareMeasurementType.TOTAL), "Account1", SUM_PRODUCT_1,
            HardwareMeasurementType.TOTAL, 10, 11, 2);
        assertSummation(longAgoSums.get(HardwareMeasurementType.PHYSICAL), "Account1", SUM_PRODUCT_1,
            HardwareMeasurementType.PHYSICAL, 5, 10, 1);
        assertSummation(longAgoSums.get(HardwareMeasurementType.HYPERVISOR), "Account1", SUM_PRODUCT_1,
            HardwareMeasurementType.HYPERVISOR, 5, 1, 1);
    }

    @Test
    public void testSlaFilterWhenSummingData() {
        // NOTE: The overall total for all the systems in Account1 should be the total of all the snapshots
        // (totaled per measurement) with no filters.
        List<TallySnapshotSummation> found = repository.sumSnapshotMeasurements(
            "Account1",
            SUM_PRODUCT_1,
            Granularity.DAILY,
            "Premium",
            LONG_AGO,
            FAR_FUTURE,
            false,
            PageRequest.of(0, 10)
        ).stream().collect(Collectors.toList());

        // Group by date.
        Map<OffsetDateTime, List<TallySnapshotSummation>> summationBySla =
            found.stream().collect(Collectors.groupingBy(TallySnapshotSummation::getSnapshotDate));
        assertEquals(1, summationBySla.keySet().size());

        // TODO Date is converted to local time when coming back from the DB,\
        //      so convert it back. Can we address this?
        OffsetDateTime nowish = OffsetDateTime.from(NOWISH.atZoneSameInstant(ZoneOffset.systemDefault()));
        // Make sure that we contain the expected measurement groups
        assertTrue(summationBySla.containsKey(nowish));

        Map<HardwareMeasurementType, TallySnapshotSummation> nowishSums = summationBySla.get(nowish)
            .stream().collect(Collectors.toMap(TallySnapshotSummation::getType, Function.identity()));

        assertEquals(4, nowishSums.keySet().size());
        assertTrue(nowishSums.keySet().containsAll(Arrays.asList(
            HardwareMeasurementType.TOTAL,
            HardwareMeasurementType.PHYSICAL,
            HardwareMeasurementType.HYPERVISOR,
            HardwareMeasurementType.AWS
        )));

        assertSummation(nowishSums.get(HardwareMeasurementType.TOTAL), "Account1", SUM_PRODUCT_1,
            HardwareMeasurementType.TOTAL, 18, 10, 10);
        assertSummation(nowishSums.get(HardwareMeasurementType.PHYSICAL), "Account1", SUM_PRODUCT_1,
            HardwareMeasurementType.PHYSICAL, 12, 4, 4);
        assertSummation(nowishSums.get(HardwareMeasurementType.HYPERVISOR), "Account1", SUM_PRODUCT_1,
            HardwareMeasurementType.HYPERVISOR, 4, 4, 4);
        assertSummation(nowishSums.get(HardwareMeasurementType.AWS), "Account1", SUM_PRODUCT_1,
            HardwareMeasurementType.AWS, 2, 2, 2);
    }

    @Test
    public void testSumSlaWithOnlyUnsetValues() {
        // NOTE: The overall total for all the systems in Account1 should be the total of all the snapshots
        // (totaled per measurement) with no filters.
        List<TallySnapshotSummation> found = repository.sumSnapshotMeasurements(
            "Account1",
            SUM_PRODUCT_1,
            Granularity.DAILY,
            null,
            LONG_AGO,
            FAR_FUTURE,
            true,
            PageRequest.of(0, 10)
        ).stream().collect(Collectors.toList());

        // Group by date.
        Map<OffsetDateTime, List<TallySnapshotSummation>> summationBySla =
            found.stream().collect(Collectors.groupingBy(TallySnapshotSummation::getSnapshotDate));
        assertEquals(1, summationBySla.keySet().size());

        // TODO Date is converted to local time when coming back from the DB,\
        //      so convert it back. Can we address this?
        OffsetDateTime nowish = OffsetDateTime.from(NOWISH.atZoneSameInstant(ZoneOffset.systemDefault()));
        // Make sure that we contain the expected measurement groups
        assertTrue(summationBySla.containsKey(nowish));

        Map<HardwareMeasurementType, TallySnapshotSummation> nowishSums = summationBySla.get(nowish)
            .stream().collect(Collectors.toMap(TallySnapshotSummation::getType, Function.identity()));

        assertEquals(2, nowishSums.keySet().size());
        assertTrue(nowishSums.keySet().containsAll(Arrays.asList(
            HardwareMeasurementType.TOTAL,
            HardwareMeasurementType.PHYSICAL
        )));

        assertSummation(nowishSums.get(HardwareMeasurementType.TOTAL), "Account1", SUM_PRODUCT_1,
            HardwareMeasurementType.TOTAL, 2, 2, 1);
        assertSummation(nowishSums.get(HardwareMeasurementType.PHYSICAL), "Account1", SUM_PRODUCT_1,
            HardwareMeasurementType.PHYSICAL, 2, 2, 1);
    }

    private TallySnapshot createSnapshot(String account, String product, Granularity granularity,
        String sla, int cores, int sockets, int instances, OffsetDateTime date) {

        Map<HardwareMeasurementType, HardwareMeasurement> measurements = new HashMap<>();
        measurements.put(HardwareMeasurementType.TOTAL, createMeasurement(cores, sockets, instances));
        measurements.put(HardwareMeasurementType.PHYSICAL, createMeasurement(cores, sockets, instances));
        return createSnapshot(account, product, granularity, sla, date, measurements);
    }

    private TallySnapshot createSnapshot(String account, String product, Granularity granularity,
        String sla, OffsetDateTime date, Map<HardwareMeasurementType, HardwareMeasurement> measurements) {
        TallySnapshot tally = new TallySnapshot();
        tally.setAccountNumber(account);
        tally.setProductId(product);
        tally.setOwnerId("N/A");
        tally.setGranularity(granularity);
        tally.setServiceLevel(sla);
        tally.setSnapshotDate(date);

        measurements.entrySet().forEach(m -> tally.setHardwareMeasurement(m.getKey(), m.getValue()));

        return tally;
    }

    private HardwareMeasurement createMeasurement(int cores, int sockets, int instances) {
        HardwareMeasurement measurement = new HardwareMeasurement();
        measurement.setCores(cores);
        measurement.setSockets(sockets);
        measurement.setInstanceCount(instances);
        return measurement;
    }

    private void assertSummation(TallySnapshotSummation sum, String expectedAccount, String expectedProductId,
        HardwareMeasurementType expectedType, Integer expectedCores, Integer expectedSockets,
        Integer expectedInstances) {
        assertNotNull(sum);
        assertEquals(expectedAccount, sum.getAccountNumber(), "Invalid account number in sum!");
        assertEquals(expectedProductId, sum.getProductId(), "Invalid product ID in sum!");
        assertEquals(expectedType, sum.getType(), "Invalid Hardware Measurement Type in sum!");
        assertEquals(expectedCores, sum.getCores(), "Invalid cores in sum!");
        assertEquals(expectedSockets, sum.getSockets(), "Invalid sockets in sum!");
        assertEquals(expectedInstances, sum.getInstances(), "Invalid instance count in sum!");
    }
}
