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

import static org.junit.jupiter.api.Assertions.*;

import org.candlepin.subscriptions.db.SubscriptionCapacityRepository;
import org.candlepin.subscriptions.db.model.SubscriptionCapacity;
import org.candlepin.subscriptions.exception.SubscriptionsException;
import org.candlepin.subscriptions.resteasy.PageLinkCreator;
import org.candlepin.subscriptions.security.IdentityHeaderAuthenticationDetailsSource;
import org.candlepin.subscriptions.utilization.api.model.CapacityReport;
import org.candlepin.subscriptions.utilization.api.model.CapacitySnapshot;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;

import javax.ws.rs.core.Response;

@SpringBootTest
@TestPropertySource("classpath:/test.properties")
class CapacityResourceTest {

    private final OffsetDateTime min = OffsetDateTime.now().minusDays(4);
    private final OffsetDateTime max = OffsetDateTime.now().plusDays(4);

    @MockBean
    SubscriptionCapacityRepository repository;

    @MockBean
    PageLinkCreator pageLinkCreator;

    @MockBean
    BuildProperties buildProperties;

    @Autowired
    CapacityResource resource;

    @Test
    @WithMockUser(value = "owner123456",
        authorities = "ROLE_" + IdentityHeaderAuthenticationDetailsSource.ORG_ADMIN_ROLE)
    void testShouldUseQueryBasedOnHeaderAndParameters() {
        SubscriptionCapacity capacity = new SubscriptionCapacity();
        capacity.setBeginDate(min);
        capacity.setEndDate(max);

        Mockito.when(repository
            .findSubscriptionCapacitiesByOwnerIdAndProductIdAndEndDateAfterAndBeginDateBefore(
            Mockito.eq("owner123456"),
            Mockito.eq("product1"),
            Mockito.eq(min),
            Mockito.eq(max)))
            .thenReturn(Collections.singletonList(capacity));

        CapacityReport report = resource.getCapacityReport(
            "product1",
            "daily",
            min,
            max,
            null,
            null
        );

        assertEquals(9, report.getData().size());
    }

    @Test
    @WithMockUser(value = "owner123456",
        authorities = "ROLE_" + IdentityHeaderAuthenticationDetailsSource.ORG_ADMIN_ROLE)
    void testShouldCalculateCapacityBasedOnMultipleSubscriptions() {
        SubscriptionCapacity capacity = new SubscriptionCapacity();
        capacity.setVirtualSockets(5);
        capacity.setPhysicalSockets(2);
        capacity.setBeginDate(min.truncatedTo(ChronoUnit.DAYS).minusSeconds(1));
        capacity.setEndDate(max);

        SubscriptionCapacity capacity2 = new SubscriptionCapacity();
        capacity2.setVirtualSockets(7);
        capacity2.setPhysicalSockets(11);
        capacity2.setBeginDate(min.truncatedTo(ChronoUnit.DAYS).minusSeconds(1));
        capacity2.setEndDate(max);

        Mockito.when(repository
            .findSubscriptionCapacitiesByOwnerIdAndProductIdAndEndDateAfterAndBeginDateBefore(
                Mockito.eq("owner123456"),
                Mockito.eq("product1"),
                Mockito.eq(min),
                Mockito.eq(max)))
            .thenReturn(Arrays.asList(capacity, capacity2));

        CapacityReport report = resource.getCapacityReport(
            "product1",
            "daily",
            min,
            max,
            null,
            null
        );

        CapacitySnapshot capacitySnapshot = report.getData().get(0);
        assertEquals(12, capacitySnapshot.getHypervisorSockets().intValue());
        assertEquals(13, capacitySnapshot.getPhysicalSockets().intValue());
    }

    @Test
    @WithMockUser(value = "owner123456",
        authorities = "ROLE_" + IdentityHeaderAuthenticationDetailsSource.ORG_ADMIN_ROLE)
    void testShouldThrowExceptionOnBadOffset() {
        SubscriptionsException e = assertThrows(SubscriptionsException.class, () ->
            resource.getCapacityReport(
            "product1",
            "daily",
            min,
            max,
            11,
            10));
        assertEquals(Response.Status.BAD_REQUEST, e.getStatus());
    }

    @Test
    @WithMockUser(value = "owner123456",
        authorities = "ROLE_" + IdentityHeaderAuthenticationDetailsSource.ORG_ADMIN_ROLE)
    void testShouldRespectOffsetAndLimit() {
        SubscriptionCapacity capacity = new SubscriptionCapacity();
        capacity.setBeginDate(min);
        capacity.setEndDate(max);

        Mockito.when(repository
            .findSubscriptionCapacitiesByOwnerIdAndProductIdAndEndDateAfterAndBeginDateBefore(
                Mockito.eq("owner123456"),
                Mockito.eq("product1"),
                Mockito.eq(min),
                Mockito.eq(max)))
            .thenReturn(Collections.singletonList(capacity));

        CapacityReport report = resource.getCapacityReport(
            "product1",
            "daily",
            min,
            max,
            1,
            1
        );

        assertEquals(1, report.getData().size());
        assertEquals(OffsetDateTime.now().minusDays(3).truncatedTo(ChronoUnit.DAYS),
            report.getData().get(0).getDate());
    }
}
