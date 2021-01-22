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
package org.candlepin.subscriptions.resource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.candlepin.subscriptions.FixedClockConfiguration;
import org.candlepin.subscriptions.db.model.config.OptInType;
import org.candlepin.subscriptions.security.OptInController;
import org.candlepin.subscriptions.security.WithMockRedHatPrincipal;
import org.candlepin.subscriptions.tally.files.ReportingAccountWhitelist;
import org.candlepin.subscriptions.util.ApplicationClock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;

import javax.ws.rs.BadRequestException;

@SpringBootTest
@ActiveProfiles("api,test")
@WithMockRedHatPrincipal("123456")
public class OptInResourceTest {

  private ApplicationClock clock;

  @MockBean private ReportingAccountWhitelist accountWhitelist;

  @MockBean private OptInController controller;

  @Autowired private OptInResource resource;

  @BeforeEach
  public void setupTests() throws IOException {
    this.clock = new FixedClockConfiguration().fixedClock();
    when(accountWhitelist.hasAccount(eq("account123456"))).thenReturn(true);
  }

  @Test
  public void testDeleteOptInConfig() {
    resource.deleteOptInConfig();
    Mockito.verify(controller).optOut(eq("account123456"), eq("owner123456"));
  }

  @Test
  public void testGet() {
    resource.getOptInConfig();
    Mockito.verify(controller).getOptInConfig(eq("account123456"), eq("owner123456"));
  }

  @Test
  public void testPut() {
    resource.putOptInConfig(false, false, false);
    Mockito.verify(controller)
        .optIn(
            eq("account123456"),
            eq("owner123456"),
            eq(OptInType.API),
            eq(Boolean.FALSE),
            eq(Boolean.FALSE),
            eq(Boolean.FALSE));
  }

  @Test
  public void testPutDefaultsToTrue() {
    resource.putOptInConfig(null, null, null);
    Mockito.verify(controller)
        .optIn(
            eq("account123456"),
            eq("owner123456"),
            eq(OptInType.API),
            eq(Boolean.TRUE),
            eq(Boolean.TRUE),
            eq(Boolean.TRUE));
  }

  @Test
  @WithMockRedHatPrincipal(value = "123456", nullifyOwner = true)
  public void testMissingOrgOnDelete() {
    assertThrows(BadRequestException.class, () -> resource.deleteOptInConfig());
  }

  @Test
  @WithMockRedHatPrincipal(value = "123456", nullifyAccount = true)
  public void testMissingAccountOnDelete() {
    assertThrows(BadRequestException.class, () -> resource.deleteOptInConfig());
  }

  @Test
  @WithMockRedHatPrincipal(
      value = "123456",
      roles = {})
  public void testAccessDeniedForDeleteAccountConfigWhenUserIsNotAnAdmin() {
    assertThrows(AccessDeniedException.class, () -> resource.deleteOptInConfig());
  }

  @Test
  @WithMockRedHatPrincipal(value = "123456", nullifyOwner = true)
  public void testMissingOrgOnGet() {
    assertThrows(BadRequestException.class, () -> resource.getOptInConfig());
  }

  @Test
  @WithMockRedHatPrincipal(value = "123456", nullifyAccount = true)
  public void testMissingAccountOnGet() {
    assertThrows(BadRequestException.class, () -> resource.getOptInConfig());
  }

  @Test
  @WithMockRedHatPrincipal(
      value = "123456",
      roles = {})
  public void testAccessDeniedForGetAccountConfigWhenUserIsNotAnAdmin() {
    assertThrows(AccessDeniedException.class, () -> resource.getOptInConfig());
  }

  @Test
  @WithMockRedHatPrincipal(value = "123456", nullifyOwner = true)
  public void testMissingOrgOnPut() {
    assertThrows(BadRequestException.class, () -> resource.putOptInConfig(true, true, true));
  }

  @Test
  @WithMockRedHatPrincipal(value = "123456", nullifyAccount = true)
  public void testMissingAccountOnPut() {
    assertThrows(BadRequestException.class, () -> resource.putOptInConfig(true, true, true));
  }

  @Test
  @WithMockRedHatPrincipal(
      value = "123456",
      roles = {})
  public void testAccessDeniedForOptInWhenUserIsNotAnAdmin() {
    assertThrows(AccessDeniedException.class, () -> resource.putOptInConfig(true, true, true));
  }
}
