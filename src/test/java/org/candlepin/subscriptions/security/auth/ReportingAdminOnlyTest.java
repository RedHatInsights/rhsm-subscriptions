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
import org.candlepin.subscriptions.security.auth.ReportingAdminOnlyTest.ReportingAdminOnlyConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = ReportingAdminOnlyConfiguration.class)
public class ReportingAdminOnlyTest {

    @Autowired ApplicationContext context;

    @EnableGlobalMethodSecurity(prePostEnabled = true)
    protected static class ReportingAdminOnlyConfiguration {
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
        @ReportingAdminOnly
        public void reportingAdminOnlyCall() {
            // Does nothing
        }
    }

    /* The reporting admin expression in pseudo-code:
     *
     *     isOrgAdminOptional or (isOrgAdmin and isWhitelisted).
     *
     * I've marked the tests with the true/false values for these conditions.
     */

    /** (F || F) && F == F */
    @Test
    @WithMockRedHatPrincipal(value = "NotAdmin", roles = {})
    void testFalseOrFalseAndFalseIsFalse() throws Exception {
        context.getBean(ApplicationProperties.class).setOrgAdminOptional(false);
        whitelistOrg(false);
        StubResource stub = context.getBean(StubResource.class);

        assertThrows(AccessDeniedException.class, stub::reportingAdminOnlyCall);
    }

    /** (F || F) && T) == F */
    @Test
    @WithMockRedHatPrincipal(value = "NotAdmin", roles = {})
    void testFalseOrFalseAndTrueIsFalse() throws Exception {
        context.getBean(ApplicationProperties.class).setOrgAdminOptional(false);
        whitelistOrg(true);
        StubResource stub = context.getBean(StubResource.class);

        assertThrows(AccessDeniedException.class, stub::reportingAdminOnlyCall);
    }

    /** (F || T) && F == F */
    @Test
    @WithMockRedHatPrincipal("Admin")
    void testFalseOrTrueAndFalseIsFalse() throws Exception {
        context.getBean(ApplicationProperties.class).setOrgAdminOptional(false);
        whitelistOrg(false);
        StubResource stub = context.getBean(StubResource.class);

        assertThrows(AccessDeniedException.class, stub::reportingAdminOnlyCall);
    }

    /** (T || F) && F == F */
    @Test
    @WithMockRedHatPrincipal(value = "NotAdmin", roles = {})
    void testTrueOrFalseAndFalseIsTrue() throws Exception {
        context.getBean(ApplicationProperties.class).setOrgAdminOptional(true);
        whitelistOrg(false);
        StubResource stub = context.getBean(StubResource.class);

        assertThrows(AccessDeniedException.class, stub::reportingAdminOnlyCall);
    }

    /** (F || T) && T == T */
    @Test
    @WithMockRedHatPrincipal("Admin")
    void testFalseOrTrueAndTrueIsTrue() throws Exception {
        context.getBean(ApplicationProperties.class).setOrgAdminOptional(false);
        whitelistOrg(true);
        StubResource stub = context.getBean(StubResource.class);

        assertDoesNotThrow(stub::reportingAdminOnlyCall);
    }

    /** (T || F) && T == T */
    @Test
    @WithMockRedHatPrincipal(value = "NotAdmin", roles = {})
    void testTrueOrFalseAndTrueIsTrue() throws Exception {
        context.getBean(ApplicationProperties.class).setOrgAdminOptional(true);
        whitelistOrg(true);
        StubResource stub = context.getBean(StubResource.class);

        assertDoesNotThrow(stub::reportingAdminOnlyCall);
    }

    /** (T || T) && F == F */
    @Test
    @WithMockRedHatPrincipal("Admin")
    void testTrueOrTrueAndFalseIsTrue() throws Exception {
        context.getBean(ApplicationProperties.class).setOrgAdminOptional(true);
        whitelistOrg(false);
        StubResource stub = context.getBean(StubResource.class);

        assertThrows(AccessDeniedException.class, stub::reportingAdminOnlyCall);
    }

    /** (T || T) && T == T */
    @Test
    @WithMockRedHatPrincipal("Admin")
    void testTrueOrTrueAndTrueIsTrue() throws Exception {
        context.getBean(ApplicationProperties.class).setOrgAdminOptional(true);
        whitelistOrg(true);
        StubResource stub = context.getBean(StubResource.class);

        assertDoesNotThrow(stub::reportingAdminOnlyCall);
    }

    private void whitelistOrg(boolean shouldWhitelist) throws Exception {
        WhitelistedAccountReportAccessService mockAccess =
            context.getBean(WhitelistedAccountReportAccessService.class);
        when(mockAccess.providesAccessTo(any(Authentication.class))).thenReturn(shouldWhitelist);
    }
}
