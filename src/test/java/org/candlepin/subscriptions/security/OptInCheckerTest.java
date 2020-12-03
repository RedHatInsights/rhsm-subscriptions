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
package org.candlepin.subscriptions.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.candlepin.subscriptions.exception.OptInRequiredException;
import org.candlepin.subscriptions.utilization.api.model.OptInConfig;
import org.candlepin.subscriptions.utilization.api.model.OptInConfigData;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class OptInCheckerTest {

    @Autowired OptInChecker checker;

    @MockBean OptInController controller;

    @Test
    @WithInvalidPrincipal
    void testBadPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertFalse(checker.checkAccess(auth));
    }

    @Test
    @WithMockRedHatPrincipal
    void testNotOptedIn() {
        OptInConfig config = mock(OptInConfig.class);
        OptInConfigData configData = mock(OptInConfigData.class);

        when(config.getData()).thenReturn(configData);
        when(configData.getOptInComplete()).thenReturn(Boolean.FALSE);

        when(controller.getOptInConfig(anyString(), anyString())).thenReturn(config);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThrows(OptInRequiredException.class, () -> checker.checkAccess(auth));
    }

    @Test
    @WithMockRedHatPrincipal
    void testOptedIn() {
        OptInConfig config = mock(OptInConfig.class);
        OptInConfigData configData = mock(OptInConfigData.class);

        when(config.getData()).thenReturn(configData);
        when(configData.getOptInComplete()).thenReturn(Boolean.TRUE);

        when(controller.getOptInConfig(anyString(), anyString())).thenReturn(config);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertTrue(checker.checkAccess(auth));
    }
}
