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
        TallySnapshot t = createUnpersisted("Hello", "World", 2, OffsetDateTime.now());
        TallySnapshot saved = repository.saveAndFlush(t);
        assertNotNull(saved.getId());
    }

    @Test
    public void testFindByAccountNumberAndProduct() {
        TallySnapshot t1 = createUnpersisted("Hello", "World", 2, NOWISH);
        TallySnapshot t2 = createUnpersisted("Bugs", "Bunny", 9999, NOWISH);

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
        TallySnapshot t1 = createUnpersisted("Account1", productId, 2, LONG_AGO);
        TallySnapshot t2 = createUnpersisted("Account2", productId, 9, NOWISH);
        TallySnapshot t3 = createUnpersisted("Account2", "Another Product", 19, NOWISH);
        // Will not be in result - Account not in query
        TallySnapshot t4 = createUnpersisted("Account3", productId, 99, FAR_FUTURE);

        repository.saveAll(Arrays.asList(t1, t2, t3, t4));
        repository.flush();

        OffsetDateTime min = OffsetDateTime.of(2019, 05, 23, 00, 00, 00, 00,
            ZoneOffset.UTC);
        OffsetDateTime max = OffsetDateTime.of(2019, 07, 23, 00, 00, 00, 00,
            ZoneOffset.UTC);

        List<String> accounts = Arrays.asList("Account1", "Account2");
        List<TallySnapshot> found =
            repository.findByAccountNumberInAndProductIdAndGranularityAndSnapshotDateBetween(accounts,
            productId, TallyGranularity.DAILY, min, max);
        assertEquals(1, found.size());
        assertEquals("Account2", found.get(0).getAccountNumber());
        assertEquals(found.get(0).getProductId(), productId);
        assertEquals(found.get(0).getCores(), Integer.valueOf(9));
    }

    private TallySnapshot createUnpersisted(String account, String product, int cores, OffsetDateTime date) {
        TallySnapshot tally = new TallySnapshot();
        tally.setAccountNumber(account);
        tally.setProductId(product);
        tally.setOwnerId("N/A");
        tally.setCores(cores);
        tally.setGranularity(TallyGranularity.DAILY);
        tally.setSnapshotDate(date);
        return tally;
    }
}
