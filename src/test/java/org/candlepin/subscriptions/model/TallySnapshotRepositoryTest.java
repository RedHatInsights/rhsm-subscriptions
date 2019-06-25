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
package org.candlepin.subscriptions.model;

import static org.junit.jupiter.api.Assertions.*;

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
        TallySnapshot t = new TallySnapshot();
        t.setOwnerId("Hello");
        t.setProductId("World");
        t.setCores(2);
        t.setGranularity(TallyGranularity.DAILY);
        t.setSnapshotDate(OffsetDateTime.now());
        TallySnapshot saved = repository.saveAndFlush(t);
        assertNotNull(saved.getId());
    }

    @Test
    public void testFindByAccountNumberAndProduct() {
        TallySnapshot t1 = new TallySnapshot();
        t1.setAccountNumber("Hello");
        t1.setProductId("World");
        t1.setCores(2);
        t1.setGranularity(TallyGranularity.DAILY);
        t1.setSnapshotDate(NOWISH);

        TallySnapshot t2 = new TallySnapshot();
        t2.setAccountNumber("Bugs");
        t2.setProductId("Bunny");
        t2.setOwnerId("N/A");
        t2.setCores(9999);
        t2.setGranularity(TallyGranularity.DAILY);
        t2.setSnapshotDate(NOWISH);

        repository.save(t1);
        repository.save(t2);
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
}
