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
package org.candlepin.subscriptions.resource;

import org.candlepin.subscriptions.db.model.TallyGranularity;
import org.candlepin.subscriptions.security.auth.AdminOnly;
import org.candlepin.subscriptions.utilization.api.model.CapacityReport;
import org.candlepin.subscriptions.utilization.api.model.CapacityReportMeta;
import org.candlepin.subscriptions.utilization.api.model.CapacitySnapshot;
import org.candlepin.subscriptions.utilization.api.model.TallyReportLinks;
import org.candlepin.subscriptions.utilization.api.resources.CapacityApi;

import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.Period;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.List;

import javax.validation.constraints.NotNull;

/**
 * Capacity API implementation.
 */
@Component
public class CapacityResource implements CapacityApi {

    @Override
    @AdminOnly
    public CapacityReport getCapacityReport(String productId, @NotNull String granularity,
        @NotNull OffsetDateTime beginning, @NotNull OffsetDateTime ending, Integer offset, Integer limit) {

        List<CapacitySnapshot> snapshots = createMockData(granularity, beginning, ending);

        return new CapacityReport()
            .data(snapshots)
            .links(createMockLinks())
            .meta(createMockMeta(productId, granularity, snapshots));
    }

    private CapacityReportMeta createMockMeta(String productId, @NotNull String granularity,
        List<CapacitySnapshot> snapshots) {

        return new CapacityReportMeta()
            .count(snapshots.size())
            .granularity(granularity)
            .product(productId);
    }

    private TallyReportLinks createMockLinks() {
        return new TallyReportLinks();
    }

    private List<CapacitySnapshot> createMockData(@NotNull String granularity,
        @NotNull OffsetDateTime beginning, @NotNull OffsetDateTime ending) {

        List<CapacitySnapshot> snapshots = new ArrayList<>();
        int i = 0;
        for (OffsetDateTime current = beginning; current.isBefore(ending); current =
            current.plus(getAmount(granularity))) {

            OffsetDateTime effectiveDate = current;
            if (i == 0) {
                effectiveDate = current.plusSeconds(1); // to keep our "range is exclusive" correct with this
                // mock data :-)
            }
            int sockets = 64;
            if (i == 4) {
                // leave a gap for illustrative purposes
                sockets = 0;
            }
            else if (i > 4) {
                sockets = 100;
            }
            snapshots.add(new CapacitySnapshot().date(effectiveDate).sockets(sockets)
                .physicalSockets(sockets / 2)
                .hypervisorSockets(sockets / 2)
                .hasInfiniteQuantity(false));
            i += 1;
        }
        return snapshots;
    }

    private TemporalAmount getAmount(String granularity) {
        switch (TallyGranularity.valueOf(granularity.toUpperCase())) {
            case DAILY:
                return Period.ofDays(1);
            case WEEKLY:
                return Period.ofWeeks(1);
            case MONTHLY:
                return Period.ofMonths(1);
            case QUARTERLY:
                return Period.ofMonths(3);
            case YEARLY:
                return Period.ofYears(1);
            default:
                return null;
        }
    }
}
