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
package org.candlepin.subscriptions.tally.roller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.candlepin.subscriptions.FixedClockConfiguration;
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.TallyGranularity;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.util.ApplicationClock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


@SpringBootTest
// The transactional annotation will rollback the transaction at the end of every test.
@Transactional
@TestPropertySource("classpath:/test.properties")
@TestInstance(Lifecycle.PER_CLASS)
public class QuarterlySnapshotRollerTest {

    private static final String TEST_PRODUCT = "TEST_PROD";

    @Autowired
    private TallySnapshotRepository repository;

    private ApplicationClock clock;
    private QuarterlySnapshotRoller roller;

    @BeforeEach
    public void setupTest() {
        this.clock = new FixedClockConfiguration().fixedClock();
        this.roller = new QuarterlySnapshotRoller(TEST_PRODUCT, repository, clock);
    }

    @Test
    public void testQuarterlySnapshotProduction() {
        setupYearsWorthOfMonthlySnaps("A1", clock.now().minusYears(1));
        setupYearsWorthOfMonthlySnaps("A1", clock.now());
        setupYearsWorthOfMonthlySnaps("A1", clock.now().plusYears(1));
        roller.rollSnapshots(Arrays.asList("A1"));

        List<TallySnapshot> quarterlySnaps = repository
            .findByAccountNumberAndProductIdAndGranularityAndSnapshotDateBetweenOrderBySnapshotDate("A1",
            TEST_PRODUCT, TallyGranularity.QUARTERLY, clock.startOfCurrentQuarter(),
            clock.endOfCurrentQuarter(), PageRequest.of(0, 100)).stream().collect(Collectors.toList());
        assertEquals(1, quarterlySnaps.size());

        TallySnapshot secondQuarter = quarterlySnaps.get(0);
        assertEquals(TallyGranularity.QUARTERLY, secondQuarter.getGranularity());
        assertEquals(TEST_PRODUCT, secondQuarter.getProductId());
        assertEquals(Integer.valueOf(5), secondQuarter.getCores());
        assertEquals(Integer.valueOf(6), secondQuarter.getSockets());
        assertEquals(Integer.valueOf(5), secondQuarter.getInstanceCount());
    }

    @Test
    public void testQuarterlySnapIsUpdatedWhenItAlreadyExists() {
        setupYearsWorthOfMonthlySnaps("A1", clock.now().minusYears(1));
        List<TallySnapshot> monthlies = setupYearsWorthOfMonthlySnaps("A1", clock.now());
        setupYearsWorthOfMonthlySnaps("A1", clock.now().plusYears(1));
        roller.rollSnapshots(Arrays.asList("A1"));

        List<TallySnapshot> originalSnaps = repository
            .findByAccountNumberAndProductIdAndGranularityAndSnapshotDateBetweenOrderBySnapshotDate("A1",
            TEST_PRODUCT, TallyGranularity.QUARTERLY, clock.startOfCurrentQuarter(),
            clock.endOfCurrentQuarter(), PageRequest.of(0, 100)).stream().collect(Collectors.toList());
        assertEquals(1, originalSnaps.size());

        TallySnapshot toUpdate = originalSnaps.get(0);
        assertEquals(TallyGranularity.QUARTERLY, toUpdate.getGranularity());
        assertEquals(TEST_PRODUCT, toUpdate.getProductId());
        assertEquals(Integer.valueOf(5), toUpdate.getCores());
        assertEquals(Integer.valueOf(6), toUpdate.getSockets());
        assertEquals(Integer.valueOf(5), toUpdate.getInstanceCount());

        TallySnapshot secondQuarterMonthlyToUpdate = monthlies.get(5);
        secondQuarterMonthlyToUpdate.setCores(100);
        secondQuarterMonthlyToUpdate.setSockets(200);
        secondQuarterMonthlyToUpdate.setInstanceCount(50);
        repository.saveAndFlush(secondQuarterMonthlyToUpdate);

        roller.rollSnapshots(Arrays.asList("A1"));

        // Check the yearly again. Should still be a single instance, but have updated values.
        List<TallySnapshot> updatedSnaps = repository
            .findByAccountNumberAndProductIdAndGranularityAndSnapshotDateBetweenOrderBySnapshotDate("A1",
            TEST_PRODUCT, TallyGranularity.QUARTERLY, clock.startOfCurrentQuarter(),
            clock.endOfCurrentQuarter(), PageRequest.of(0, 100)).stream().collect(Collectors.toList());
        assertEquals(1, originalSnaps.size());

        TallySnapshot updated = updatedSnaps.get(0);
        assertEquals(toUpdate.getId(), updated.getId());
        assertEquals("A1", toUpdate.getAccountNumber());
        assertEquals(TallyGranularity.QUARTERLY, updated.getGranularity());
        assertEquals(secondQuarterMonthlyToUpdate.getProductId(), updated.getProductId());
        assertEquals(secondQuarterMonthlyToUpdate.getCores(), updated.getCores());
        assertEquals(secondQuarterMonthlyToUpdate.getSockets(), updated.getSockets());
        assertEquals(secondQuarterMonthlyToUpdate.getInstanceCount(), updated.getInstanceCount());
    }

    private List<TallySnapshot> setupYearsWorthOfMonthlySnaps(String account, OffsetDateTime anyDayOfWeek) {
        OffsetDateTime startOfYear = clock.startOfYear(anyDayOfWeek);

        List<TallySnapshot> monthlyForYear = new LinkedList<>();
        IntStream.rangeClosed(0, 11).forEach(i -> {
            monthlyForYear.add(createUnpersisted(account, TEST_PRODUCT, TallyGranularity.MONTHLY, i,
                i + 1, i, startOfYear.plusMonths(i)));
        });

        repository.saveAll(monthlyForYear);
        repository.flush();

        return monthlyForYear;
    }

    private TallySnapshot createUnpersisted(String account, String product, TallyGranularity granularity,
        int cores, int sockets, int instanceCount, OffsetDateTime date) {
        TallySnapshot tally = new TallySnapshot();
        tally.setAccountNumber(account);
        tally.setProductId(product);
        tally.setOwnerId("N/A");
        tally.setCores(cores);
        tally.setSockets(sockets);
        tally.setGranularity(granularity);
        tally.setInstanceCount(instanceCount);
        tally.setSnapshotDate(date);
        return tally;
    }
}
