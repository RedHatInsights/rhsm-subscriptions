/*
 * Copyright (c) 2019 Red Hat, Inc.
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

import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.SubscriptionCapacity;
import org.candlepin.subscriptions.db.model.SubscriptionView;
import org.candlepin.subscriptions.db.model.Usage;

import org.candlepin.subscriptions.resource.ResourceUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

@SpringBootTest
// The transactional annotation will rollback the transaction at the end of every test.
@Transactional
@TestPropertySource("classpath:/test.properties")
class SubscriptionCapacityRepositoryTest {
    private static final OffsetDateTime LONG_AGO = OffsetDateTime.ofInstant(Instant.EPOCH,
        ZoneId.systemDefault());
    private static final OffsetDateTime NOWISH = OffsetDateTime.of(2019, 6, 23, 0, 0, 0, 0,
        ZoneOffset.UTC);
    private static final OffsetDateTime FAR_FUTURE = OffsetDateTime.of(2099, 1, 1, 0, 0, 0, 0,
        ZoneOffset.UTC);

    @Autowired
    private SubscriptionCapacityRepository repository;

    @Test
    void testSave() {
        SubscriptionCapacity capacity = createUnpersisted(LONG_AGO, FAR_FUTURE);
        assertNotNull(repository.saveAndFlush(capacity));
    }

    @Test
    void testShouldFindGivenSubscriptionStartingBeforeRangeAndEndingDuringRange() {
        SubscriptionCapacity c = createUnpersisted(NOWISH.minusDays(1), FAR_FUTURE.minusDays(1));

        repository.save(c);
        repository.flush();

        List<SubscriptionCapacity> found = repository.findByOwnerAndProductId(
            "ownerId",
            "product",
            ServiceLevel.ANY,
            Usage.ANY,
            NOWISH,
            FAR_FUTURE);
        assertEquals(1, found.size());
        SubscriptionCapacity capacity = found.get(0);
        assertEquals("account", capacity.getAccountNumber());
        assertEquals("product", capacity.getProductId());
        assertEquals("subscription", capacity.getSubscriptionId());
        assertEquals(4, capacity.getPhysicalSockets().intValue());
        assertEquals(20, capacity.getVirtualSockets().intValue());
        assertEquals(8, capacity.getPhysicalCores().intValue());
        assertEquals(40, capacity.getVirtualCores().intValue());
        assertEquals("ownerId", capacity.getOwnerId());
        assertEquals(NOWISH.minusDays(1), capacity.getBeginDate());
        assertEquals(FAR_FUTURE.minusDays(1), capacity.getEndDate());
        assertFalse(capacity.getHasUnlimitedGuestSockets());
    }

    @Test
    public void testShouldFindGivenSubscriptionStartingBeforeRangeAndEndingAfterRange() {
        SubscriptionCapacity c = createUnpersisted(NOWISH.minusDays(1), FAR_FUTURE.plusDays(1));

        repository.save(c);
        repository.flush();

        List<SubscriptionCapacity> found = repository.findByOwnerAndProductId(
            "ownerId",
            "product",
            ServiceLevel.ANY,
            Usage.ANY,
            NOWISH,
            FAR_FUTURE);
        assertEquals(1, found.size());
        SubscriptionCapacity capacity = found.get(0);
        assertEquals("account", capacity.getAccountNumber());
        assertEquals("product", capacity.getProductId());
        assertEquals("subscription", capacity.getSubscriptionId());
        assertEquals(4, capacity.getPhysicalSockets().intValue());
        assertEquals(20, capacity.getVirtualSockets().intValue());
        assertEquals(8, capacity.getPhysicalCores().intValue());
        assertEquals(40, capacity.getVirtualCores().intValue());
        assertEquals("ownerId", capacity.getOwnerId());
        assertEquals(NOWISH.minusDays(1), capacity.getBeginDate());
        assertEquals(FAR_FUTURE.plusDays(1), capacity.getEndDate());
        assertFalse(capacity.getHasUnlimitedGuestSockets());
    }

    @Test
    void testShouldFindGivenSubscriptionStartingDuringRangeAndEndingDuringRange() {
        SubscriptionCapacity c = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.minusDays(1));

        repository.save(c);
        repository.flush();

        List<SubscriptionCapacity> found = repository.findByOwnerAndProductId(
            "ownerId",
            "product",
            ServiceLevel.ANY,
            Usage.ANY,
            NOWISH,
            FAR_FUTURE);
        assertEquals(1, found.size());
        SubscriptionCapacity capacity = found.get(0);
        assertEquals("account", capacity.getAccountNumber());
        assertEquals("product", capacity.getProductId());
        assertEquals("subscription", capacity.getSubscriptionId());
        assertEquals(4, capacity.getPhysicalSockets().intValue());
        assertEquals(20, capacity.getVirtualSockets().intValue());
        assertEquals(8, capacity.getPhysicalCores().intValue());
        assertEquals(40, capacity.getVirtualCores().intValue());
        assertEquals("ownerId", capacity.getOwnerId());
        assertEquals(NOWISH.plusDays(1), capacity.getBeginDate());
        assertEquals(FAR_FUTURE.minusDays(1), capacity.getEndDate());
        assertFalse(capacity.getHasUnlimitedGuestSockets());
    }

    @Test
    void testShouldFindGivenSubscriptionStartingDuringRangeAndEndingAfterRange() {
        SubscriptionCapacity c = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));

        repository.save(c);
        repository.flush();

        List<SubscriptionCapacity> found = repository.findByOwnerAndProductId(
            "ownerId",
            "product",
            ServiceLevel.ANY,
            Usage.ANY,
            NOWISH,
            FAR_FUTURE);
        assertEquals(1, found.size());
        SubscriptionCapacity capacity = found.get(0);
        assertEquals("account", capacity.getAccountNumber());
        assertEquals("product", capacity.getProductId());
        assertEquals("subscription", capacity.getSubscriptionId());
        assertEquals(4, capacity.getPhysicalSockets().intValue());
        assertEquals(20, capacity.getVirtualSockets().intValue());
        assertEquals(8, capacity.getPhysicalCores().intValue());
        assertEquals(40, capacity.getVirtualCores().intValue());
        assertEquals("ownerId", capacity.getOwnerId());
        assertEquals(NOWISH.plusDays(1), capacity.getBeginDate());
        assertEquals(FAR_FUTURE.plusDays(1), capacity.getEndDate());
        assertFalse(capacity.getHasUnlimitedGuestSockets());
    }

    @Test
    void testShouldNotFindGivenSubscriptionBeforeWindow() {
        SubscriptionCapacity c = createUnpersisted(NOWISH.minusDays(7), NOWISH.minusDays(1));

        repository.save(c);
        repository.flush();

        List<SubscriptionCapacity> found = repository.findByOwnerAndProductId(
            "ownerId",
            "product",
            ServiceLevel.ANY,
            Usage.ANY,
            NOWISH,
            FAR_FUTURE);
        assertEquals(0, found.size());
    }

    @Test
    void testShouldNotFindGivenSubscriptionAfterWindow() {
        SubscriptionCapacity c = createUnpersisted(FAR_FUTURE.plusDays(1), FAR_FUTURE.plusDays(7));

        repository.save(c);
        repository.flush();

        List<SubscriptionCapacity> found = repository.findByOwnerAndProductId(
            "ownerId",
            "product",
            ServiceLevel.ANY,
            Usage.ANY,
            NOWISH,
            FAR_FUTURE);
        assertEquals(0, found.size());
    }

    @Test
    void testAllowsNullAccountNumber() {
        SubscriptionCapacity c = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
        c.setAccountNumber(null);
        repository.save(c);
        repository.flush();

        List<SubscriptionCapacity> found = repository.findByOwnerAndProductId(
            "ownerId",
            "product",
            ServiceLevel.ANY,
            Usage.ANY,
            NOWISH,
            FAR_FUTURE);
        assertEquals(1, found.size());
    }

    @Test
    void testShouldFilterOutSlaIfDifferent() {
        SubscriptionCapacity c = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
        repository.save(c);
        repository.flush();

        List<SubscriptionCapacity> found = repository.findByOwnerAndProductId(
            "ownerId",
            "product",
            ServiceLevel.STANDARD,
            Usage.ANY,
            NOWISH,
            FAR_FUTURE);
        assertEquals(0, found.size());
    }

    @Test
    void testShouldFilterOutUsageIfDifferent() {
        SubscriptionCapacity c = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
        repository.save(c);
        repository.flush();

        List<SubscriptionCapacity> found = repository.findByOwnerAndProductId(
            "ownerId",
            "product",
            ServiceLevel.ANY,
            Usage.DEVELOPMENT_TEST,
            NOWISH,
            FAR_FUTURE);
        assertEquals(0, found.size());
    }

    @Test
    void testShouldMatchSlaIfSame() {
        SubscriptionCapacity c = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
        repository.save(c);
        repository.flush();

        List<SubscriptionCapacity> found = repository.findByOwnerAndProductId(
            "ownerId",
            "product",
            ServiceLevel.PREMIUM,
            Usage.ANY,
            NOWISH,
            FAR_FUTURE);

        assertEquals(1, found.size());
    }
    @Test
    void testShouldMatchUsageIfSame() {
        SubscriptionCapacity c = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
        repository.save(c);
        repository.flush();

        List<SubscriptionCapacity> found = repository.findByOwnerAndProductId(
            "ownerId",
            "product",
            ServiceLevel.ANY,
            Usage.PRODUCTION,
            NOWISH,
            FAR_FUTURE);
        assertEquals(1, found.size());
    }

    @Test
    void testCanQueryBySlaNull() {
        SubscriptionCapacity premium = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
        SubscriptionCapacity standard = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
        SubscriptionCapacity unset = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
        premium.setSubscriptionId("premium");
        premium.setServiceLevel(ServiceLevel.PREMIUM);
        standard.setSubscriptionId("standard");
        standard.setServiceLevel(ServiceLevel.STANDARD);
        unset.setSubscriptionId("unset");
        unset.setServiceLevel(ServiceLevel.UNSPECIFIED);
        repository.saveAll(Arrays.asList(premium, standard, unset));
        repository.flush();

        List<SubscriptionCapacity> found = repository.findByOwnerAndProductId(
            "ownerId",
            "product",
            null,
            Usage.PRODUCTION,
            NOWISH,
            FAR_FUTURE);
        assertEquals(3, found.size());
    }

    @Test
    void testCanQueryBySlaUnspecified() {
        SubscriptionCapacity premium = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
        SubscriptionCapacity standard = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
        SubscriptionCapacity unset = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
        premium.setSubscriptionId("premium");
        premium.setServiceLevel(ServiceLevel.PREMIUM);
        standard.setSubscriptionId("standard");
        standard.setServiceLevel(ServiceLevel.STANDARD);
        unset.setSubscriptionId("unset");
        unset.setServiceLevel(ServiceLevel.UNSPECIFIED);
        repository.saveAll(Arrays.asList(premium, standard, unset));
        repository.flush();

        List<SubscriptionCapacity> found = repository.findByOwnerAndProductId(
            "ownerId",
            "product",
            ServiceLevel.UNSPECIFIED,
            Usage.PRODUCTION,
            NOWISH,
            FAR_FUTURE);
        assertEquals(1, found.size());
        assertEquals(ServiceLevel.UNSPECIFIED, found.get(0).getServiceLevel());
    }

    @Test
    void testCanQueryBySlaAny() {
        SubscriptionCapacity premium = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
        SubscriptionCapacity standard = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
        SubscriptionCapacity unset = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
        premium.setSubscriptionId("premium");
        premium.setServiceLevel(ServiceLevel.PREMIUM);
        standard.setSubscriptionId("standard");
        standard.setServiceLevel(ServiceLevel.STANDARD);
        unset.setSubscriptionId("unset");
        unset.setServiceLevel(ServiceLevel.UNSPECIFIED);
        repository.saveAll(Arrays.asList(premium, standard, unset));
        repository.flush();

        List<SubscriptionCapacity> found = repository.findByOwnerAndProductId(
            "ownerId",
            "product",
            ServiceLevel.ANY,
            Usage.PRODUCTION,
            NOWISH,
            FAR_FUTURE);
        assertEquals(3, found.size());
    }

    @Test
    void testCanQueryByUsageNull() {
        SubscriptionCapacity production = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
        SubscriptionCapacity dr = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
        SubscriptionCapacity unset = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
        production.setSubscriptionId("production");
        production.setUsage(Usage.PRODUCTION);
        dr.setSubscriptionId("dr");
        dr.setUsage(Usage.DISASTER_RECOVERY);
        unset.setSubscriptionId("unset");
        unset.setUsage(Usage.UNSPECIFIED);
        repository.saveAll(Arrays.asList(production, dr, unset));
        repository.flush();

        List<SubscriptionCapacity> found = repository.findByOwnerAndProductId(
            "ownerId",
            "product",
            ServiceLevel.ANY,
            null,
            NOWISH,
            FAR_FUTURE);
        assertEquals(3, found.size());
    }

    @Test
    void testCanQueryByUsageUnspecified() {
        SubscriptionCapacity production = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
        SubscriptionCapacity dr = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
        SubscriptionCapacity unset = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
        production.setSubscriptionId("production");
        production.setUsage(Usage.PRODUCTION);
        dr.setSubscriptionId("dr");
        dr.setUsage(Usage.DISASTER_RECOVERY);
        unset.setSubscriptionId("unset");
        unset.setUsage(Usage.UNSPECIFIED);
        repository.saveAll(Arrays.asList(production, dr, unset));
        repository.flush();

        List<SubscriptionCapacity> found = repository.findByOwnerAndProductId(
            "ownerId",
            "product",
            ServiceLevel.ANY,
            Usage.UNSPECIFIED,
            NOWISH,
            FAR_FUTURE);
        assertEquals(1, found.size());
        assertEquals(Usage.UNSPECIFIED, found.get(0).getUsage());
    }

    @Test
    void testCanQueryByUsageAny() {
        SubscriptionCapacity production = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
        SubscriptionCapacity dr = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
        SubscriptionCapacity unset = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
        production.setSubscriptionId("production");
        production.setUsage(Usage.PRODUCTION);
        dr.setSubscriptionId("dr");
        dr.setUsage(Usage.DISASTER_RECOVERY);
        unset.setSubscriptionId("unset");
        unset.setUsage(Usage.UNSPECIFIED);
        repository.saveAll(Arrays.asList(production, dr, unset));
        repository.flush();

        List<SubscriptionCapacity> found = repository.findByOwnerAndProductId(
            "ownerId",
            "product",
            ServiceLevel.ANY,
            Usage.ANY,
            NOWISH,
            FAR_FUTURE);
        assertEquals(3, found.size());
    }

    @Test
    void testCanGetSubscriptionViewByUsageAny() {
        SubscriptionCapacity production = createUnpersisted(NOWISH.plusDays(2), FAR_FUTURE.plusDays(1));
        SubscriptionCapacity production2 = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
        SubscriptionCapacity production3 = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
        production.setSubscriptionId("production");
        production.setSku("sku");
        production.setUsage(Usage.PRODUCTION);
        production2.setSubscriptionId("dr");
        production2.setSku("sku");
        production2.setUsage(Usage.PRODUCTION);
        production3.setSubscriptionId("unset");
        production3.setSku("sku");
        production3.setUsage(Usage.UNSPECIFIED);
        repository.saveAll(Arrays.asList(production, production2, production3));
        repository.flush();
        Pageable page = PageRequest.of(0, 10, Sort.unsorted());
        Page<SubscriptionView> views = repository.getSubscriptionViews("account",
                "product",
                ServiceLevel.PREMIUM,
                Usage.PRODUCTION,
                page);
        List<SubscriptionView> viewList = views.toList();
        assertEquals(1, viewList.size());
        assertEquals(NOWISH.plusDays(2).toInstant().toEpochMilli(),
                viewList.get(0).getBeginDate().toInstant().toEpochMilli());
        assertEquals(FAR_FUTURE.plusDays(1).toInstant().toEpochMilli(),
                viewList.get(0).getEndDate().toInstant().toEpochMilli());

    }

    private SubscriptionCapacity createUnpersisted(OffsetDateTime begin, OffsetDateTime end) {
        SubscriptionCapacity capacity = new SubscriptionCapacity();
        capacity.setAccountNumber("account");
        capacity.setProductId("product");
        capacity.setSubscriptionId("subscription");
        capacity.setBeginDate(begin);
        capacity.setEndDate(end);
        capacity.setHasUnlimitedGuestSockets(false);
        capacity.setOwnerId("ownerId");
        capacity.setPhysicalSockets(4);
        capacity.setVirtualSockets(20);
        capacity.setPhysicalCores(8);
        capacity.setVirtualCores(40);
        capacity.setServiceLevel(ServiceLevel.PREMIUM);
        capacity.setUsage(Usage.PRODUCTION);
        return capacity;
    }
}
