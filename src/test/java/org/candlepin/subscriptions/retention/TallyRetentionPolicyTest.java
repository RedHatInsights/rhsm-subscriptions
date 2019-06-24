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
package org.candlepin.subscriptions.retention;

import static org.junit.jupiter.api.Assertions.*;

import org.candlepin.subscriptions.FixedClockConfiguration;
import org.candlepin.subscriptions.model.TallyGranularity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * See test.properties for test retention policy (i.e. keys like rhsm-subscriptions.tally.retention.%s=%i)
 */
@SpringBootTest
@Import(FixedClockConfiguration.class)
@TestPropertySource("classpath:/test.properties")
class TallyRetentionPolicyTest {
    @Autowired
    TallyRetentionPolicy policy;

    @Test
    void testDailyCutoffDate() {
        OffsetDateTime cutoff = policy.getCutoffDate(TallyGranularity.DAILY);
        OffsetDateTime fifteenDaysAgo = OffsetDateTime.of(2019, 5, 9, 0, 0, 0, 0, ZoneOffset.UTC);
        assertEquals(fifteenDaysAgo, cutoff);
    }

    @Test
    void testWeeklyCutoffDate() {
        OffsetDateTime cutoff = policy.getCutoffDate(TallyGranularity.WEEKLY);
        OffsetDateTime threeWeeksAgo = OffsetDateTime.of(2019, 4, 28, 0, 0, 0, 0, ZoneOffset.UTC);
        assertEquals(threeWeeksAgo, cutoff);
    }

    @Test
    void testMonthlyCutoffDate() {
        OffsetDateTime cutoff = policy.getCutoffDate(TallyGranularity.MONTHLY);
        OffsetDateTime twoMonthsAgo = OffsetDateTime.of(2019, 3, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        assertEquals(twoMonthsAgo, cutoff);
    }

    @Test
    void testQuarterlyCutoffDate() {
        OffsetDateTime cutoff = policy.getCutoffDate(TallyGranularity.QUARTERLY);
        OffsetDateTime twoQuartersAgo = OffsetDateTime.of(2018, 10, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        assertEquals(twoQuartersAgo, cutoff);
    }

    @Test
    void testYearlyCutoffDate() {
        OffsetDateTime cutoff = policy.getCutoffDate(TallyGranularity.YEARLY);
        OffsetDateTime twoYearsAgo = OffsetDateTime.of(2017, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        assertEquals(twoYearsAgo, cutoff);
    }
}
