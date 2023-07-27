/*
 * Copyright Red Hat, Inc.
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
import static org.mockito.Mockito.*;

import jakarta.ws.rs.BadRequestException;
import org.candlepin.subscriptions.db.AccountConfigRepository;
import org.candlepin.subscriptions.db.model.config.OptInType;
import org.candlepin.subscriptions.security.OptInController;
import org.candlepin.subscriptions.security.WithMockRedHatPrincipal;
import org.candlepin.subscriptions.test.TestClockConfiguration;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"api", "test"})
@WithMockRedHatPrincipal("123456")
@Import(TestClockConfiguration.class)
class OptInResourceTest {

  @Autowired private ApplicationClock clock;

  @MockBean AccountConfigRepository accountConfigRepository;

  @MockBean private OptInController controller;

  @Autowired private OptInResource resource;

  @BeforeEach
  public void setupTests() {
    when(accountConfigRepository.existsByOrgId("owner123456")).thenReturn(true);
  }

  @Test
  void testDeleteOptInConfig() {
    resource.deleteOptInConfig();
    Mockito.verify(controller).optOut("owner123456");
  }

  @Test
  void testGet() {
    resource.getOptInConfig();
    Mockito.verify(controller).getOptInConfig("account123456", "owner123456");
  }

  @Test
  void testPut() {
    resource.putOptInConfig();
    Mockito.verify(controller).optIn("account123456", "owner123456", OptInType.API);
  }

  @Test
  @WithMockRedHatPrincipal(value = "123456", nullifyOrgId = true)
  void testMissingOrgOnDelete() {
    assertThrows(BadRequestException.class, () -> resource.deleteOptInConfig());
  }

  @Test
  @WithMockRedHatPrincipal(
      value = "123456",
      roles = {})
  void testAccessDeniedForDeleteAccountConfigWhenUserIsNotAnAdmin() {
    assertThrows(AccessDeniedException.class, () -> resource.deleteOptInConfig());
  }

  @Test
  @WithMockRedHatPrincipal(value = "123456", nullifyOrgId = true)
  void testMissingOrgOnGet() {
    assertThrows(BadRequestException.class, () -> resource.getOptInConfig());
  }

  @Test
  @WithMockRedHatPrincipal(
      value = "123456",
      roles = {})
  void testAccessDeniedForGetAccountConfigWhenUserIsNotAnAdmin() {
    assertThrows(AccessDeniedException.class, () -> resource.getOptInConfig());
  }

  @Test
  @WithMockRedHatPrincipal(value = "123456", nullifyOrgId = true)
  void testMissingOrgOnPut() {
    assertThrows(BadRequestException.class, () -> resource.putOptInConfig());
  }

  @Test
  @WithMockRedHatPrincipal(
      value = "123456",
      roles = {})
  void testAccessDeniedForOptInWhenUserIsNotAnAdmin() {
    assertThrows(AccessDeniedException.class, () -> resource.putOptInConfig());
  }
}
