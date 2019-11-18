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

import org.candlepin.subscriptions.db.model.SubscriptionCapacity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
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

        List<SubscriptionCapacity> found = repository
            .findSubscriptionCapacitiesByOwnerIdAndProductIdAndEndDateAfterAndBeginDateBefore(
            "ownerId",
            "product",
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

        List<SubscriptionCapacity> found = repository
            .findSubscriptionCapacitiesByOwnerIdAndProductIdAndEndDateAfterAndBeginDateBefore(
            "ownerId",
            "product",
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

        List<SubscriptionCapacity> found = repository
            .findSubscriptionCapacitiesByOwnerIdAndProductIdAndEndDateAfterAndBeginDateBefore(
            "ownerId",
            "product",
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

        List<SubscriptionCapacity> found = repository
            .findSubscriptionCapacitiesByOwnerIdAndProductIdAndEndDateAfterAndBeginDateBefore(
            "ownerId",
            "product",
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

        List<SubscriptionCapacity> found = repository
            .findSubscriptionCapacitiesByOwnerIdAndProductIdAndEndDateAfterAndBeginDateBefore(
            "ownerId",
            "product",
            NOWISH,
            FAR_FUTURE);
        assertEquals(0, found.size());
    }

    @Test
    void testShouldNotFindGivenSubscriptionAfterWindow() {
        SubscriptionCapacity c = createUnpersisted(FAR_FUTURE.plusDays(1), FAR_FUTURE.plusDays(7));

        repository.save(c);
        repository.flush();

        List<SubscriptionCapacity> found = repository
            .findSubscriptionCapacitiesByOwnerIdAndProductIdAndEndDateAfterAndBeginDateBefore(
            "ownerId",
            "product",
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

        List<SubscriptionCapacity> found = repository
            .findSubscriptionCapacitiesByOwnerIdAndProductIdAndEndDateAfterAndBeginDateBefore(
            "ownerId",
            "product",
            NOWISH,
            FAR_FUTURE);
        assertEquals(1, found.size());
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
        return capacity;
    }
}
