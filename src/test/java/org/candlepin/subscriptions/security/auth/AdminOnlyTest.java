/*
 * Copyright (c) 2009 - 2020 Red Hat, Inc.
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
import static org.mockito.Mockito.*;

import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.security.WhitelistedAccountReportAccessService;
import org.candlepin.subscriptions.security.WithMockRedHatPrincipal;
import org.candlepin.subscriptions.security.auth.AdminOnlyTest.AdminOnlyConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = AdminOnlyConfiguration.class)
public class AdminOnlyTest {

    @Autowired
    ApplicationContext context;

    @EnableGlobalMethodSecurity(prePostEnabled = true)
    protected static class AdminOnlyConfiguration {
        @Bean
        public StubResource stubResource() {
            return new StubResource();
        }

        @Bean
        public ApplicationProperties applicationProperties() {
            return new ApplicationProperties();
        }

        @Bean
        public WhitelistedAccountReportAccessService reportAccessService() {
            return mock(WhitelistedAccountReportAccessService.class);
        }
    }

    protected static class StubResource {
        @AdminOnly
        public void adminOnlyCall() {
            // Does nothing
        }
    }

    @Test
    @WithMockRedHatPrincipal(value = "NotAnAdmin", roles = {})
    void testAdminOnlyCallWithNonAdmin() throws Exception {
        StubResource stub = context.getBean(StubResource.class);

        context.getBean(ApplicationProperties.class).setOrgAdminOptional(false);
        assertThrows(AccessDeniedException.class, stub::adminOnlyCall);

        context.getBean(ApplicationProperties.class).setOrgAdminOptional(true);
        assertDoesNotThrow(stub::adminOnlyCall);
    }

    @Test
    @WithMockRedHatPrincipal("Admin")
    void testAdminOnlyCallWithOrgAdmin() throws Exception {
        StubResource stub = context.getBean(StubResource.class);

        context.getBean(ApplicationProperties.class).setOrgAdminOptional(false);
        assertDoesNotThrow(stub::adminOnlyCall);

        context.getBean(ApplicationProperties.class).setOrgAdminOptional(true);
        assertDoesNotThrow(stub::adminOnlyCall);
    }


}
