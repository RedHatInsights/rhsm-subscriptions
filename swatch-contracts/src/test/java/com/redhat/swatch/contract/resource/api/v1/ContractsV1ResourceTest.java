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
package com.redhat.swatch.contract.resource.api.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.redhat.swatch.contract.openapi.model.BillingAccount;
import com.redhat.swatch.contract.repository.BillingAccountInfoDTO;
import com.redhat.swatch.contract.repository.BillingProvider;
import com.redhat.swatch.contract.repository.SubscriptionRepository;
import com.redhat.swatch.contract.security.Identity;
import com.redhat.swatch.contract.security.RhIdentityPrincipal;
import io.quarkus.security.ForbiddenException;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.SecurityContext;
import java.util.ArrayList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@QuarkusTest
@TestSecurity(
    user = "owner123456",
    roles = {"customer"})
class ContractsV1ResourceTest {

  @Inject ContractsV1Resource resource;
  @InjectMock SubscriptionRepository subscriptionRepository;
  @InjectMock SecurityContext securityContext;

  private static final String DEFAULT_ORG_ID = "org123";

  @BeforeEach
  void updateSecurityContext() {
    setSecurityContext(false);
  }

  @Test
  void testFetchBillingAccountIdsForUsersOwnOrg() {
    var billingAccountInfoDTOs = new ArrayList<BillingAccountInfoDTO>();
    billingAccountInfoDTOs.add(
        new BillingAccountInfoDTO(DEFAULT_ORG_ID, "account1", BillingProvider.AWS, "rosa"));
    billingAccountInfoDTOs.add(
        new BillingAccountInfoDTO(DEFAULT_ORG_ID, "account2", BillingProvider.AWS, "rosa"));
    when(subscriptionRepository.findBillingAccountInfo(any(), any()))
        .thenReturn(billingAccountInfoDTOs);

    var response = resource.fetchBillingAccountIdsForOrg(DEFAULT_ORG_ID, "rosa");

    var expectedIds = new ArrayList<BillingAccount>();
    expectedIds.add(
        new BillingAccount()
            .billingAccountId("account1")
            .billingProvider(BillingProvider.AWS.getValue())
            .productTag("rosa")
            .orgId(DEFAULT_ORG_ID));
    expectedIds.add(
        new BillingAccount()
            .billingAccountId("account2")
            .billingProvider(BillingProvider.AWS.getValue())
            .productTag("rosa")
            .orgId(DEFAULT_ORG_ID));
    assertEquals(2, response.getIds().size());
    assertEquals(expectedIds, response.getIds());
  }

  @Test
  void testFetchBillingAccountIdsForNotUsersOrgIsForbidden() {
    ForbiddenException exception =
        assertThrows(
            ForbiddenException.class,
            () -> resource.fetchBillingAccountIdsForOrg("notMyOrg", "rosa"));
    assertEquals("The user is not authorized to access this organization.", exception.getMessage());
  }

  @Test
  void testFetchBillingAccountIdsForOrgByAssociateIsAllowed() {
    setSecurityContext(true);
    Assertions.assertNotNull(resource.fetchBillingAccountIdsForOrg("notMyOrg", "rosa"));
  }

  @Test
  void testFetchBillingAccountIdsWithoutProductTag() {
    var billingAccountInfoDTOs = new ArrayList<BillingAccountInfoDTO>();
    billingAccountInfoDTOs.add(
        new BillingAccountInfoDTO(DEFAULT_ORG_ID, "account1", BillingProvider.AWS, "rosa"));
    when(subscriptionRepository.findBillingAccountInfo(any(), any()))
        .thenReturn(billingAccountInfoDTOs);

    var response = resource.fetchBillingAccountIdsForOrg(DEFAULT_ORG_ID, null);

    var expectedIds = new ArrayList<BillingAccount>();
    expectedIds.add(
        new BillingAccount()
            .orgId(DEFAULT_ORG_ID)
            .billingAccountId("account1")
            .billingProvider(BillingProvider.AWS.getValue())
            .productTag("rosa"));
    assertEquals(1, response.getIds().size());
    assertEquals(expectedIds, response.getIds());
  }

  @Test
  void testFetchBillingAccountIdsWithoutOrgIdThrowsError() {
    assertThrows(
        ConstraintViolationException.class,
        () -> resource.fetchBillingAccountIdsForOrg(null, "rosa"));
  }

  @Test
  void testFetchBillingAccountIdsWithNullBillingProvider() {
    var billingAccountInfoDTOs = new ArrayList<BillingAccountInfoDTO>();
    billingAccountInfoDTOs.add(new BillingAccountInfoDTO(DEFAULT_ORG_ID, "account1", null, "rosa"));
    when(subscriptionRepository.findBillingAccountInfo(any(), any()))
        .thenReturn(billingAccountInfoDTOs);

    var response = resource.fetchBillingAccountIdsForOrg(DEFAULT_ORG_ID, "rosa");

    var expectedIds = new ArrayList<BillingAccount>();
    expectedIds.add(
        new BillingAccount()
            .billingAccountId("account1")
            .billingProvider(null)
            .productTag("rosa")
            .orgId(DEFAULT_ORG_ID));
    assertEquals(1, response.getIds().size());
    assertEquals(expectedIds, response.getIds());
  }

  private void setSecurityContext(boolean isAssociate) {
    RhIdentityPrincipal mockPrincipal = Mockito.mock(RhIdentityPrincipal.class);
    Identity identity = new Identity();
    identity.setOrgId(ContractsV1ResourceTest.DEFAULT_ORG_ID);
    when(mockPrincipal.getIdentity()).thenReturn(identity);
    when(mockPrincipal.isAssociate()).thenReturn(isAssociate);
    when(securityContext.getUserPrincipal()).thenReturn(mockPrincipal);
  }
}
