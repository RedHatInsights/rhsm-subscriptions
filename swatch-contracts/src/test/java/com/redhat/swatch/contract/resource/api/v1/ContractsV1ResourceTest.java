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

import static com.redhat.swatch.common.security.RhIdentityHeaderAuthenticationMechanism.RH_IDENTITY_HEADER;
import static com.redhat.swatch.contract.security.RhIdentityUtils.ASSOCIATE_IDENTITY_HEADER;
import static com.redhat.swatch.contract.security.RhIdentityUtils.CUSTOMER_IDENTITY_HEADER;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.redhat.swatch.contract.openapi.model.BillingAccount;
import com.redhat.swatch.contract.openapi.model.BillingAccountIdResponse;
import com.redhat.swatch.contract.repository.BillingAccountInfoDTO;
import com.redhat.swatch.contract.repository.BillingProvider;
import com.redhat.swatch.contract.repository.SubscriptionRepository;
import com.redhat.swatch.contract.test.resources.DisableRbacResource;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(DisableRbacResource.class)
class ContractsV1ResourceTest {

  @InjectMock SubscriptionRepository subscriptionRepository;
  private static final String DEFAULT_ORG_ID = "org123";

  @Test
  void testFetchBillingAccountIdsForUsersOwnOrg() {
    var billingAccountInfoDTOs = new ArrayList<BillingAccountInfoDTO>();
    billingAccountInfoDTOs.add(
        new BillingAccountInfoDTO(DEFAULT_ORG_ID, "account1", BillingProvider.AWS, "rosa"));
    billingAccountInfoDTOs.add(
        new BillingAccountInfoDTO(DEFAULT_ORG_ID, "account2", BillingProvider.AWS, "rosa"));
    when(subscriptionRepository.findBillingAccountInfo(any(), any()))
        .thenReturn(billingAccountInfoDTOs);

    var response =
        given()
            .queryParams("org_id", DEFAULT_ORG_ID, "product_tag", "rosa")
            .header(RH_IDENTITY_HEADER, CUSTOMER_IDENTITY_HEADER)
            .get("/api/swatch-contracts/v1/subscriptions/billing_account_ids")
            .then()
            .statusCode(200)
            .extract()
            .as(BillingAccountIdResponse.class);

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
    given()
        .queryParams(
            "org_id", "notMyOrg",
            "product_tag", "rosa")
        .header(RH_IDENTITY_HEADER, CUSTOMER_IDENTITY_HEADER)
        .get("/api/swatch-contracts/v1/subscriptions/billing_account_ids")
        .then()
        .statusCode(403);
  }

  @Test
  void testFetchBillingAccountIdsForOrgByAssociateIsAllowed() {
    given()
        .queryParams(
            "org_id", "notMyOrg",
            "product_tag", "rosa")
        .header(RH_IDENTITY_HEADER, ASSOCIATE_IDENTITY_HEADER)
        .get("/api/swatch-contracts/v1/subscriptions/billing_account_ids")
        .then()
        .statusCode(200)
        .extract()
        .as(BillingAccountIdResponse.class);
  }

  @Test
  void testFetchBillingAccountIdsWithoutProductTag() {
    var billingAccountInfoDTOs = new ArrayList<BillingAccountInfoDTO>();
    billingAccountInfoDTOs.add(
        new BillingAccountInfoDTO(DEFAULT_ORG_ID, "account1", BillingProvider.AWS, "rosa"));
    when(subscriptionRepository.findBillingAccountInfo(any(), any()))
        .thenReturn(billingAccountInfoDTOs);

    var response =
        given()
            .queryParams("org_id", DEFAULT_ORG_ID)
            .header(RH_IDENTITY_HEADER, CUSTOMER_IDENTITY_HEADER)
            .get("/api/swatch-contracts/v1/subscriptions/billing_account_ids")
            .then()
            .statusCode(200)
            .extract()
            .as(BillingAccountIdResponse.class);

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
    given()
        .queryParams("product_tag", "rosa")
        .header(RH_IDENTITY_HEADER, CUSTOMER_IDENTITY_HEADER)
        .get("/api/swatch-contracts/v1/subscriptions/billing_account_ids")
        .then()
        .statusCode(400);
  }

  @Test
  void testFetchBillingAccountIdsWithNullBillingProvider() {
    var billingAccountInfoDTOs = new ArrayList<BillingAccountInfoDTO>();
    billingAccountInfoDTOs.add(new BillingAccountInfoDTO(DEFAULT_ORG_ID, "account1", null, "rosa"));
    when(subscriptionRepository.findBillingAccountInfo(any(), any()))
        .thenReturn(billingAccountInfoDTOs);

    var response =
        given()
            .queryParams("org_id", DEFAULT_ORG_ID, "product_tag", "rosa")
            .header(RH_IDENTITY_HEADER, CUSTOMER_IDENTITY_HEADER)
            .get("/api/swatch-contracts/v1/subscriptions/billing_account_ids")
            .then()
            .statusCode(200)
            .extract()
            .as(BillingAccountIdResponse.class);

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
}
