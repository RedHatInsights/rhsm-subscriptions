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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.subscriptions.db.model.AccountMaxValues;
import org.candlepin.subscriptions.db.model.TallyGranularity;
import org.candlepin.subscriptions.db.model.TallySnapshot;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@SpringBootTest
// The transactional annotation will rollback the transaction at the end of every test.
@Transactional
@TestPropertySource("classpath:/test.properties")
public class TallySnapshotRepositoryTest {
    private static final OffsetDateTime LONG_AGO = OffsetDateTime.ofInstant(Instant.EPOCH,
        ZoneId.systemDefault());
    private static final OffsetDateTime NOWISH = OffsetDateTime.of(2019, 06, 23, 00, 00, 00, 00,
        ZoneOffset.UTC);
    private static final OffsetDateTime FAR_FUTURE = OffsetDateTime.of(2099, 01, 01, 00, 00, 00, 00,
        ZoneOffset.UTC);

    @Autowired private TallySnapshotRepository repository;

    @Test
    public void testSave() {
        TallySnapshot t = createUnpersisted("Hello", "World", TallyGranularity.DAILY, 2, 3, 4,
            OffsetDateTime.now());
        TallySnapshot saved = repository.saveAndFlush(t);
        assertNotNull(saved.getId());
    }

    @Test
    public void testFindByAccountNumberAndProduct() {
        TallySnapshot t1 = createUnpersisted("Hello", "World", TallyGranularity.DAILY, 2, 3, 4, NOWISH);
        TallySnapshot t2 = createUnpersisted("Bugs", "Bunny", TallyGranularity.DAILY, 9999, 999, 99, NOWISH);

        repository.saveAll(Arrays.asList(t1, t2));
        repository.flush();

        List<TallySnapshot> found = repository
            .findByAccountNumberAndProductIdAndGranularityAndSnapshotDateBetween(
            "Bugs",
            "Bunny",
            TallyGranularity.DAILY,
            LONG_AGO,
            FAR_FUTURE
        );
        assertEquals(1, found.size());
        TallySnapshot snapshot = found.get(0);
        assertEquals(9999, (int) snapshot.getCores());
        assertEquals("Bugs", snapshot.getAccountNumber());
        assertEquals("Bunny", snapshot.getProductId());
        assertEquals("N/A", snapshot.getOwnerId());
        assertEquals(NOWISH, found.get(0).getSnapshotDate());
    }

    @Test
    public void testFindByAccountNumberInAndProductIdAndGranularityAndSnapshotDateBetween() {
        String productId = "Product1";
        TallySnapshot t1 = createUnpersisted("Account1", productId, TallyGranularity.DAILY, 2, 3, 4,
            LONG_AGO);
        TallySnapshot t2 = createUnpersisted("Account2", productId, TallyGranularity.DAILY, 9, 10, 11,
            NOWISH);
        TallySnapshot t3 = createUnpersisted("Account2", "Another Product", TallyGranularity.DAILY, 19, 20,
            21, NOWISH);
        // Will not be in result - Account not in query
        TallySnapshot t4 = createUnpersisted("Account3", productId, TallyGranularity.DAILY, 99, 100, 101,
            FAR_FUTURE);
        // Will not be found - incorrect granularity
        TallySnapshot t5 = createUnpersisted("Account2", productId, TallyGranularity.WEEKLY, 20, 22, 23,
            NOWISH);

        repository.saveAll(Arrays.asList(t1, t2, t3, t4, t5));
        repository.flush();

        OffsetDateTime min = OffsetDateTime.of(2019, 05, 23, 00, 00, 00, 00,
            ZoneOffset.UTC);
        OffsetDateTime max = OffsetDateTime.of(2019, 07, 23, 00, 00, 00, 00,
            ZoneOffset.UTC);

        List<String> accounts = Arrays.asList("Account1", "Account2");
        List<TallySnapshot> found =
            repository.findByAccountNumberInAndProductIdAndGranularityAndSnapshotDateBetween(accounts,
            productId, TallyGranularity.DAILY, min, max).collect(Collectors.toList());
        assertEquals(1, found.size());
        assertEquals("Account2", found.get(0).getAccountNumber());
        assertEquals(productId, found.get(0).getProductId());
        assertEquals(Integer.valueOf(9), found.get(0).getCores());
        assertEquals(Integer.valueOf(10), found.get(0).getSockets());
        assertEquals(Integer.valueOf(11), found.get(0).getInstanceCount());
    }

    @Test
    public void testGetMaxForAccounts() {
        String productId = "P1";
        List<TallySnapshot> toPersist = Arrays.asList(
            createUnpersisted("Account1", productId, TallyGranularity.DAILY, 200, 400, 200, LONG_AGO),
            createUnpersisted("Account1", productId, TallyGranularity.DAILY, 9, 10, 20, NOWISH),
            createUnpersisted("Account1", productId, TallyGranularity.DAILY, 19, 3, 8, NOWISH),
            createUnpersisted("Account1", productId, TallyGranularity.MONTHLY, 192, 7, 120, NOWISH),
            createUnpersisted("Account1", "Another Product", TallyGranularity.DAILY, 100, 100, 200, NOWISH),
            createUnpersisted("Account2", productId, TallyGranularity.DAILY, 24, 64, 20, NOWISH),
            createUnpersisted("Account2", productId, TallyGranularity.DAILY, 224, 1, 100, NOWISH),
            createUnpersisted("Account3", productId, TallyGranularity.DAILY, 112, 13, 100, NOWISH),
            createUnpersisted("Account3", productId, TallyGranularity.DAILY, 223, 27, 200, NOWISH)
        );
        repository.saveAll(toPersist);
        repository.flush();

        List<AccountMaxValues> maxValues = repository.getMaxValuesForAccounts(
            Arrays.asList("Account1", "Account2"), productId, TallyGranularity.DAILY,
            NOWISH, NOWISH);
        assertEquals(2, maxValues.size());

        boolean foundA1 = false;
        boolean foundA2 = false;
        for (AccountMaxValues max : maxValues) {
            if ("Account1".equals(max.getAccountNumber())) {
                assertEquals(Integer.valueOf(19), max.getMaxCores());
                assertEquals(Integer.valueOf(10), max.getMaxSockets());
                assertEquals(Integer.valueOf(20), max.getMaxInstances());
                foundA1 = true;
            }
            else if ("Account2".equals(max.getAccountNumber())) {
                assertEquals(Integer.valueOf(224), max.getMaxCores());
                assertEquals(Integer.valueOf(64), max.getMaxSockets());
                assertEquals(Integer.valueOf(100), max.getMaxInstances());
                foundA2 = true;
            }
        }
        assertTrue(foundA1 && foundA2);
    }


    private TallySnapshot createUnpersisted(String account, String product, TallyGranularity granularity,
        int cores, int sockets, int instances, OffsetDateTime date) {
        TallySnapshot tally = new TallySnapshot();
        tally.setAccountNumber(account);
        tally.setProductId(product);
        tally.setOwnerId("N/A");
        tally.setCores(cores);
        tally.setSockets(sockets);
        tally.setGranularity(granularity);
        tally.setInstanceCount(instances);
        tally.setSnapshotDate(date);
        return tally;
    }
}
