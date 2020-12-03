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
package org.candlepin.subscriptions.security.auth;

import static org.junit.jupiter.api.Assertions.*;

import org.candlepin.subscriptions.security.WhitelistedAccountReportAccessService;
import org.candlepin.subscriptions.security.WithMockRedHatPrincipal;
import org.candlepin.subscriptions.util.StubResource;
import org.candlepin.subscriptions.util.StubResourceConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(StubResourceConfiguration.class)
class SubscriptionWatchAdminOnlyTest {

    @Autowired
    ApplicationContext context;

    @MockBean
    WhitelistedAccountReportAccessService reportAccessService;

    @Test
    @WithMockRedHatPrincipal(value = "NotAnAdmin", roles = {})
    void testAdminOnlyCallWithNonAdmin() {
        StubResource stub = context.getBean(StubResource.class);

        assertThrows(AccessDeniedException.class, stub::adminOnlyCall);
    }

    @Test
    @WithMockRedHatPrincipal("Admin")
    void testAdminOnlyCallWithOrgAdmin() {
        StubResource stub = context.getBean(StubResource.class);

        assertDoesNotThrow(stub::adminOnlyCall);
    }


}
