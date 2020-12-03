/*
 * Copyright (c) 2019 - 2020 Red Hat, Inc.
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

import static org.mockito.Mockito.*;

import org.candlepin.subscriptions.db.AccountListSource;
import org.candlepin.subscriptions.db.HostRepository;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.TallyHostView;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.resteasy.PageLinkCreator;
import org.candlepin.subscriptions.security.WithMockRedHatPrincipal;
import org.candlepin.subscriptions.tally.AccountListSourceException;
import org.candlepin.subscriptions.utilization.api.model.HostReportSort;
import org.candlepin.subscriptions.utilization.api.model.SortDirection;
import org.candlepin.subscriptions.utilization.api.model.Uom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collections;

@SpringBootTest
@ActiveProfiles("api,test")
@WithMockRedHatPrincipal("123456")
class HostsResourceTest {

    @MockBean
    HostRepository repository;

    @MockBean
    PageLinkCreator pageLinkCreator;

    @MockBean
    AccountListSource accountListSource;

    @Autowired
    HostsResource resource;

    static final Sort.Order IMPLICIT_ORDER = new Sort.Order(Sort.Direction.ASC, "id");

    @BeforeEach
    public void setup() throws AccountListSourceException {
        PageImpl<TallyHostView> mockPage = new PageImpl<>(Collections.emptyList());
        when(repository.getTallyHostViews(any(), any(), any(), any(), anyInt(), anyInt(), any()))
            .thenReturn(mockPage);
        when(accountListSource.containsReportingAccount("account123456")).thenReturn(true);
    }

    @Test
    void testShouldMapDisplayNameAppropriately() {
        resource.getHosts("RHEL", 0, 1, null, null, null, HostReportSort.DISPLAY_NAME, SortDirection.ASC);

        verify(repository, only()).getTallyHostViews(
            eq("account123456"),
            eq("RHEL"),
            eq(ServiceLevel.ANY),
            eq(Usage.ANY),
            eq(0),
            eq(0),
            eq(PageRequest.of(0, 1,
            Sort.by(Sort.Order.asc(HostsResource.SORT_PARAM_MAPPING.get(HostReportSort.DISPLAY_NAME)),
            IMPLICIT_ORDER)))
        );
    }

    @Test
    void testShouldMapCoresAppropriately() {
        resource.getHosts("RHEL", 0, 1, null, null, null, HostReportSort.CORES, SortDirection.ASC);

        verify(repository, only()).getTallyHostViews(
            eq("account123456"),
            eq("RHEL"),
            eq(ServiceLevel.ANY),
            eq(Usage.ANY),
            eq(0),
            eq(0),
            eq(PageRequest.of(0, 1,
            Sort.by(Sort.Order.asc(HostsResource.SORT_PARAM_MAPPING.get(HostReportSort.CORES)),
            IMPLICIT_ORDER)))
        );
    }

    @Test
    void testShouldMapSocketsAppropriately() {
        resource.getHosts("RHEL", 0, 1, null, null, null, HostReportSort.SOCKETS, SortDirection.ASC);

        verify(repository, only()).getTallyHostViews(
            eq("account123456"),
            eq("RHEL"),
            eq(ServiceLevel.ANY),
            eq(Usage.ANY),
            eq(0),
            eq(0),
            eq(PageRequest.of(0, 1,
            Sort.by(Sort.Order.asc(HostsResource.SORT_PARAM_MAPPING.get(HostReportSort.SOCKETS)),
            IMPLICIT_ORDER)))
        );
    }

    @Test
    void testShouldMapLastSeenAppropriately() {
        resource.getHosts("RHEL", 0, 1, null, null, null, HostReportSort.LAST_SEEN, SortDirection.ASC);

        verify(repository, only()).getTallyHostViews(
            eq("account123456"),
            eq("RHEL"),
            eq(ServiceLevel.ANY),
            eq(Usage.ANY),
            eq(0),
            eq(0),
            eq(PageRequest.of(0, 1,
            Sort.by(Sort.Order.asc(HostsResource.SORT_PARAM_MAPPING.get(HostReportSort.LAST_SEEN)),
            IMPLICIT_ORDER)))
        );
    }

    @Test
    void testShouldMapHardwareTypeAppropriately() {
        resource.getHosts("RHEL", 0, 1, null, null, null, HostReportSort.HARDWARE_TYPE, SortDirection.ASC);

        verify(repository, only()).getTallyHostViews(
            eq("account123456"),
            eq("RHEL"),
            eq(ServiceLevel.ANY),
            eq(Usage.ANY),
            eq(0),
            eq(0),
            eq(PageRequest.of(0, 1,
            Sort.by(Sort.Order.asc(HostsResource.SORT_PARAM_MAPPING.get(HostReportSort.HARDWARE_TYPE)),
            IMPLICIT_ORDER)))
        );
    }

    @Test
    void testShouldDefaultToImplicitOrder() {
        resource.getHosts("RHEL", 0, 1, null, null, null, null, null);

        verify(repository, only()).getTallyHostViews(
            eq("account123456"),
            eq("RHEL"),
            eq(ServiceLevel.ANY),
            eq(Usage.ANY),
            eq(0),
            eq(0),
            eq(PageRequest.of(0, 1, Sort.by(IMPLICIT_ORDER)))
        );
    }

    @Test
    void testShouldDefaultToAscending() {
        resource.getHosts("RHEL", 0, 1, null, null, null, HostReportSort.DISPLAY_NAME, null);

        verify(repository, only()).getTallyHostViews(
            eq("account123456"),
            eq("RHEL"),
            eq(ServiceLevel.ANY),
            eq(Usage.ANY),
            eq(0),
            eq(0),
            eq(PageRequest.of(0, 1,
            Sort.by(Sort.Order.asc(HostsResource.SORT_PARAM_MAPPING.get(HostReportSort.DISPLAY_NAME)),
            IMPLICIT_ORDER)))
        );
    }

    @Test
    void testShouldUseMinCoresWhenUomIsCores() {
        resource.getHosts("RHEL", 0, 1, null, null, Uom.CORES, null, null);
        verify(repository, only()).getTallyHostViews(
            eq("account123456"),
            eq("RHEL"),
            eq(ServiceLevel.ANY),
            eq(Usage.ANY),
            eq(1),
            eq(0),
            eq(PageRequest.of(0, 1, Sort.by(IMPLICIT_ORDER)))
        );
    }

    @Test
    void testShouldUseMinSocketsWhenUomIsSockets() {
        resource.getHosts("RHEL", 0, 1, null, null, Uom.SOCKETS, null, null);
        verify(repository, only()).getTallyHostViews(
            eq("account123456"),
            eq("RHEL"),
            eq(ServiceLevel.ANY),
            eq(Usage.ANY),
            eq(0),
            eq(1),
            eq(PageRequest.of(0, 1, Sort.by(IMPLICIT_ORDER)))
        );
    }
}
