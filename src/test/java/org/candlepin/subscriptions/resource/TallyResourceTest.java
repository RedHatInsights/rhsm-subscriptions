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

import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.exception.SubscriptionsException;
import org.candlepin.subscriptions.resteasy.PageLinkCreator;
import org.candlepin.subscriptions.security.IdentityHeaderAuthenticationDetailsSource;
import org.candlepin.subscriptions.utilization.api.model.TallyReport;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;

import javax.ws.rs.core.Response;

@SpringBootTest
@TestPropertySource("classpath:/test.properties")
public class TallyResourceTest {

    @MockBean
    TallySnapshotRepository repository;

    @MockBean
    PageLinkCreator pageLinkCreator;

    @MockBean
    BuildProperties buildProperties;

    @Autowired
    TallyResource resource;

    private final OffsetDateTime min = OffsetDateTime.now().minusDays(4);
    private final OffsetDateTime max = OffsetDateTime.now().plusDays(4);

    @Test
    @WithMockUser(value = "owner123456",
        authorities = "ROLE_" + IdentityHeaderAuthenticationDetailsSource.ORG_ADMIN_ROLE)
    public void testShouldUseQueryBasedOnHeaderAndParameters() {
        TallySnapshot snap = new TallySnapshot();

        Mockito.when(repository
            .findByOwnerIdAndProductIdAndGranularityAndSnapshotDateBetweenOrderBySnapshotDate(
            Mockito.eq("owner123456"),
            Mockito.eq("product1"),
            Mockito.eq(Granularity.DAILY),
            Mockito.eq(min),
            Mockito.eq(max),
            Mockito.any(Pageable.class)))
            .thenReturn(new PageImpl<>(Arrays.asList(snap)));
        TallyReport report = resource.getTallyReport(
            "product1",
            "daily",
            min,
            max,
            10,
            10
        );
        assertEquals(1, report.getData().size());

        Pageable expectedPageable = PageRequest.of(1, 10);
        Mockito.verify(repository)
            .findByOwnerIdAndProductIdAndGranularityAndSnapshotDateBetweenOrderBySnapshotDate(
            Mockito.eq("owner123456"),
            Mockito.eq("product1"),
            Mockito.eq(Granularity.DAILY),
            Mockito.eq(min),
            Mockito.eq(max),
            Mockito.eq(expectedPageable)
            );
    }

    @Test
    @WithMockUser(value = "owner123456",
        authorities = "ROLE_" + IdentityHeaderAuthenticationDetailsSource.ORG_ADMIN_ROLE)
    public void testShouldThrowExceptionOnBadOffset() {
        SubscriptionsException e = assertThrows(SubscriptionsException.class, () -> resource.getTallyReport(
            "product1",
            "daily",
            min,
            max,
            11,
            10
        ));
        assertEquals(Response.Status.BAD_REQUEST, e.getStatus());
    }

    @Test
    @WithMockUser(value = "owner123456",
        authorities = "ROLE_" + IdentityHeaderAuthenticationDetailsSource.ORG_ADMIN_ROLE)
    public void reportDataShouldGetFilledWhenPagingParametersAreNotPassed() {
        Mockito.when(repository
            .findByOwnerIdAndProductIdAndGranularityAndSnapshotDateBetweenOrderBySnapshotDate(
                 Mockito.eq("owner123456"),
                 Mockito.eq("product1"),
                 Mockito.eq(Granularity.DAILY),
                 Mockito.eq(min),
                 Mockito.eq(max),
                 Mockito.eq(null)))
            .thenReturn(new PageImpl<>(Collections.emptyList()));

        TallyReport report = resource.getTallyReport(
            "product1",
            "daily",
            min,
            max,
            null,
            null
        );

        // Since nothing was returned from the DB, there should be one generated snapshot for each day
        // in the range.
        assertEquals(9, report.getData().size());
        report.getData().forEach(snap -> assertFalse(snap.getHasData()));
    }
}
