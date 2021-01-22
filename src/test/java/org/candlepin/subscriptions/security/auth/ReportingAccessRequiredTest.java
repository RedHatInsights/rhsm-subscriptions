/*
 * Copyright (c) 2021 Red Hat, Inc.
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
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(StubResourceConfiguration.class)
public class ReportingAccessRequiredTest {

  @Autowired ApplicationContext context;

  @MockBean WhitelistedAccountReportAccessService reportAccessService;

  /* The reporting admin expression in pseudo-code:
   *
   *     isOrgAdmin and isWhitelisted.
   *
   * I've marked the tests with the true/false values for these conditions.
   */

  /** F && F == F */
  @Test
  @WithMockRedHatPrincipal(
      value = "NotAdmin",
      roles = {})
  void testFalseOrFalseAndFalseIsFalse() throws Exception {
    whitelistOrg(false);
    StubResource stub = context.getBean(StubResource.class);

    assertThrows(AccessDeniedException.class, stub::reportingAdminOnlyCall);
  }

  /** F && T == F */
  @Test
  @WithMockRedHatPrincipal(
      value = "NotAdmin",
      roles = {})
  void testFalseOrFalseAndTrueIsFalse() throws Exception {
    whitelistOrg(true);
    StubResource stub = context.getBean(StubResource.class);

    assertThrows(AccessDeniedException.class, stub::reportingAdminOnlyCall);
  }

  /** T && F == F */
  @Test
  @WithMockRedHatPrincipal("Admin")
  void testFalseOrTrueAndFalseIsFalse() throws Exception {
    whitelistOrg(false);
    StubResource stub = context.getBean(StubResource.class);

    assertThrows(AccessDeniedException.class, stub::reportingAdminOnlyCall);
  }

  /** T && T == T */
  @Test
  @WithMockRedHatPrincipal("Admin")
  void testFalseOrTrueAndTrueIsTrue() throws Exception {
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
