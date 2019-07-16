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

import org.candlepin.subscriptions.db.model.TallyGranularity;
import org.candlepin.subscriptions.db.model.TallySnapshot;

import org.junit.jupiter.api.Test;
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
            .findByAccountNumberAndProductIdAndGranularityAndSnapshotDateBetweenOrderBySnapshotDate(
            "Bugs",
            "Bunny",
            TallyGranularity.DAILY,
            LONG_AGO,
            FAR_FUTURE,
            PageRequest.of(0, 10)
        ).stream().collect(Collectors.toList());
        assertEquals(1, found.size());
        TallySnapshot snapshot = found.get(0);
        assertEquals(9999, (int) snapshot.getCores());
        assertEquals("Bugs", snapshot.getAccountNumber());
        assertEquals("Bunny", snapshot.getProductId());
        assertEquals("N/A", snapshot.getOwnerId());
        assertEquals(NOWISH, found.get(0).getSnapshotDate());
    }

    @Test
    public void testFindByAccountNumberInAndProductIdInAndGranularityAndSnapshotDateBetween() {
        String product1 = "Product1";
        String product2 = "Product2";
        // Will not be found - out of date range.
        TallySnapshot t1 = createUnpersisted("Account1", product1, TallyGranularity.DAILY, 2, 3, 4,
            LONG_AGO);
        // Will be found.
        TallySnapshot t2 = createUnpersisted("Account2", product1, TallyGranularity.DAILY, 9, 10, 11,
            NOWISH);
        // Will be found.
        TallySnapshot t3 = createUnpersisted("Account2", product2, TallyGranularity.DAILY, 19, 20, 21,
            NOWISH);
        // Will not be found, incorrect granularity
        TallySnapshot t4 = createUnpersisted("Account2", product2, TallyGranularity.WEEKLY, 19, 20, 21,
            NOWISH);
        // Will not be in result - Account not in query
        TallySnapshot t5 = createUnpersisted("Account3", product1, TallyGranularity.DAILY, 99, 100, 101,
            FAR_FUTURE);
        // Will not be found - incorrect granularity
        TallySnapshot t6 = createUnpersisted("Account2", product1, TallyGranularity.WEEKLY, 20, 22, 23,
            NOWISH);

        repository.saveAll(Arrays.asList(t1, t2, t3, t4, t5, t6));
        repository.flush();

        OffsetDateTime min = OffsetDateTime.of(2019, 05, 23, 00, 00, 00, 00,
            ZoneOffset.UTC);
        OffsetDateTime max = OffsetDateTime.of(2019, 07, 23, 00, 00, 00, 00,
            ZoneOffset.UTC);

        List<String> accounts = Arrays.asList("Account1", "Account2");
        List<String> products = Arrays.asList(product1, product2);
        List<TallySnapshot> found =
            repository.findByAccountNumberInAndProductIdInAndGranularityAndSnapshotDateBetween(accounts,
            products, TallyGranularity.DAILY, min, max).collect(Collectors.toList());
        // TODO Expect this to fail. Need to rebuild test result checking.
        assertEquals(2, found.size());
        assertEquals("Account2", found.get(0).getAccountNumber());
        assertEquals(product1, found.get(0).getProductId());
        assertEquals(Integer.valueOf(9), found.get(0).getCores());
        assertEquals(Integer.valueOf(10), found.get(0).getSockets());
        assertEquals(Integer.valueOf(11), found.get(0).getInstanceCount());
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
