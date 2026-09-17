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
package tests;

import static com.redhat.swatch.component.tests.utils.SwatchUtils.X_RH_IDENTITY_HEADER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.redhat.swatch.component.tests.api.AuthorizationModel;
import com.redhat.swatch.component.tests.api.SubscriptionsAccessLevel;
import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.component.tests.utils.SwatchUtils;
import com.redhat.swatch.contract.test.model.BillingAccount;
import com.redhat.swatch.contract.test.model.BillingAccountIdResponse;
import domain.BillingProvider;
import domain.Offering;
import domain.Product;
import domain.Subscription;
import io.restassured.response.Response;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;

public class BillingAccountIdsComponentTest extends BaseContractComponentTest {

  @TestPlanName("billing-account-ids-TC001")
  @Test
  void shouldGetBillingAccountIdForSubscriptionByProductTag() {
    // Given: Active subscriptions for two products in the same organization
    String billingAccountId = "billing-" + RandomUtils.generateRandom();
    String otherProductBillingAccountId = "billing-other-" + RandomUtils.generateRandom();
    givenSubscription(
        Product.ROSA, billingAccountId, clock.now().minusDays(1), clock.now().plusDays(1));
    givenSubscription(
        Product.OPENSHIFT,
        otherProductBillingAccountId,
        clock.now().minusDays(1),
        clock.now().plusDays(1));
    Map<String, String> requestHeaders = givenAssociateHeaders();

    // When: Billing account IDs are requested for ROSA
    BillingAccountIdResponse response =
        whenGetBillingAccountIds(Product.ROSA.getName(), requestHeaders);

    // Then: Only the ROSA subscription is returned with its subscription
    // metadata
    assertEquals(1, response.getIds().size(), "Only the requested product should be returned");
    BillingAccount account = response.getIds().get(0);
    assertBillingAccount(account, billingAccountId, Product.ROSA.getName());
  }

  @TestPlanName("billing-account-ids-TC002")
  @Test
  void shouldGetBillingAccountIdsForAllProductsWithoutTag() {
    // Given: Active subscriptions for two products in the same organization
    String billingAccountId = "billing-" + RandomUtils.generateRandom();
    givenSubscription(
        Product.ROSA, billingAccountId, clock.now().minusDays(1), clock.now().plusDays(1));
    givenSubscription(
        Product.OPENSHIFT, billingAccountId, clock.now().minusDays(1), clock.now().plusDays(1));
    Map<String, String> requestHeaders = givenAssociateHeaders();

    // When: Billing account IDs are requested without a product tag
    List<BillingAccount> accounts = whenGetBillingAccountIds(null, requestHeaders).getIds();

    // Then: One record is returned for each product
    assertEquals(2, accounts.size(), "One record should be returned for each product");
    assertBillingAccountForProduct(accounts, billingAccountId, Product.ROSA.getName());
    assertBillingAccountForProduct(accounts, billingAccountId, Product.OPENSHIFT.getName());
  }

  @TestPlanName("billing-account-ids-TC003")
  @Test
  void shouldExcludeSubscriptionActiveOnlyInPriorMonth() {
    // Given: One subscription ended before the current month and one is
    // currently active
    OffsetDateTime monthStart = clock.startOfCurrentMonth();
    String priorMonthBillingAccountId = "billing-prior-" + RandomUtils.generateRandom();
    String currentMonthBillingAccountId = "billing-current-" + RandomUtils.generateRandom();
    givenSubscription(
        Product.ROSA,
        priorMonthBillingAccountId,
        monthStart.minusMonths(1),
        monthStart.minusDays(1));
    givenSubscription(
        Product.ROSA,
        currentMonthBillingAccountId,
        monthStart.minusDays(1),
        monthStart.plusMonths(1));
    Map<String, String> requestHeaders = givenAssociateHeaders();

    // When: Billing account IDs are requested for ROSA
    List<BillingAccount> accounts =
        whenGetBillingAccountIds(Product.ROSA.getName(), requestHeaders).getIds();

    // Then: The prior-month subscription is excluded
    assertEquals(1, accounts.size(), "Only the current subscription should be returned");
    assertBillingAccount(accounts.get(0), currentMonthBillingAccountId, Product.ROSA.getName());
  }

  @TestPlanName("billing-account-ids-TC004")
  @Test
  void shouldOrderBillingAccountIdsByBillingAccountId() {
    // Given: Two active ROSA subscriptions with sortable billing account IDs
    String firstBillingAccountId = "billing-a-" + RandomUtils.generateRandom();
    String secondBillingAccountId = "billing-z-" + RandomUtils.generateRandom();
    givenSubscription(
        Product.ROSA, secondBillingAccountId, clock.now().minusDays(1), clock.now().plusDays(1));
    givenSubscription(
        Product.ROSA, firstBillingAccountId, clock.now().minusDays(1), clock.now().plusDays(1));
    Map<String, String> requestHeaders = givenAuthorizedUserHeaders();

    // When: Billing account IDs are requested through the public API
    BillingAccountIdResponse accounts =
        whenGetBillingAccountIds(Product.ROSA.getName(), requestHeaders);

    // Then: Records are ordered by billing account ID
    assertEquals(
        List.of(firstBillingAccountId, secondBillingAccountId),
        accounts.getIds().stream().map(BillingAccount::getBillingAccountId).toList(),
        "Billing account IDs should be returned in ascending order");
    assertBillingAccount(accounts.getIds().get(0), firstBillingAccountId, Product.ROSA.getName());
    assertBillingAccount(accounts.getIds().get(1), secondBillingAccountId, Product.ROSA.getName());
  }

  @TestPlanName("billing-account-ids-TC005")
  @Test
  void shouldForbidBillingAccountIdsForAnotherOrg() {
    // Given: An authorized customer identity for the test organization
    Map<String, String> requestHeaders = givenAuthorizedUserHeaders();
    String otherOrgId = givenOrgIdWithSuffix("-other");

    // When: Billing account IDs are requested for another organization through
    // the public API
    Response forbidden =
        service.fetchBillingAccountIdsForOrg(otherOrgId, Product.ROSA.getName(), requestHeaders);

    // Then: The request is rejected
    assertEquals(
        HttpStatus.SC_FORBIDDEN,
        forbidden.statusCode(),
        "A customer must not access another organization's billing accounts");
  }

  private Map<String, String> givenAssociateHeaders() {
    String email = RandomUtils.generateRandom() + "@redhat.com";
    return SwatchUtils.securityHeadersWithAssociate(orgId, email);
  }

  private Map<String, String> givenAuthorizedUserHeaders() {
    String userId = RandomUtils.generateRandom();
    Map<String, String> requestHeaders = SwatchUtils.securityHeadersWithUserRole(orgId, userId);
    rbacHelper.givenUserHasSubscriptionsAccess(
        AuthorizationModel.RBAC,
        userId,
        requestHeaders.get(X_RH_IDENTITY_HEADER),
        SubscriptionsAccessLevel.GRANTED_READER);
    return requestHeaders;
  }

  private void givenSubscription(
      Product product, String billingAccountId, OffsetDateTime startDate, OffsetDateTime endDate) {
    String sku = "billing-account-" + RandomUtils.generateRandom();
    Offering offering =
        product == Product.ROSA
            ? Offering.buildRosaOffering(sku)
            : Offering.buildOpenShiftOffering(sku, 1.0, null);
    wiremock.forProductAPI().stubOfferingData(offering);
    assertEquals(
        HttpStatus.SC_OK, service.syncOffering(sku).statusCode(), "Offering sync should succeed");

    Subscription subscription =
        Subscription.builder()
            .orgId(orgId)
            .product(product)
            .subscriptionId(RandomUtils.generateRandom())
            .subscriptionNumber(RandomUtils.generateRandom())
            .offering(offering)
            .subscriptionMeasurements(Map.of(CORES, 1.0))
            .startDate(startDate)
            .endDate(endDate)
            .billingProvider(BillingProvider.AWS)
            .billingAccountId(billingAccountId)
            .quantity(1)
            .build();
    assertEquals(
        HttpStatus.SC_OK,
        service.saveSubscriptions(true, subscription).statusCode(),
        "Subscription creation should succeed");
  }

  private BillingAccountIdResponse whenGetBillingAccountIds(
      String productTag, Map<String, String> requestHeaders) {
    Response response = service.fetchBillingAccountIdsForOrg(orgId, productTag, requestHeaders);
    BillingAccountIdResponse billingAccountIds =
        response.then().statusCode(HttpStatus.SC_OK).extract().as(BillingAccountIdResponse.class);
    assertNotNull(billingAccountIds, "Billing account response should not be null");
    assertNotNull(billingAccountIds.getIds(), "Billing account IDs should be present");
    return billingAccountIds;
  }

  private void assertBillingAccount(
      BillingAccount account, String billingAccountId, String productTag) {
    assertNotNull(account, "Billing account record should not be null");
    assertEquals(orgId, account.getOrgId(), "Organization ID should match");
    assertEquals(
        billingAccountId, account.getBillingAccountId(), "Billing account ID should match");
    assertEquals(productTag, account.getProductTag(), "Product tag should match");
    assertEquals(
        BillingProvider.AWS.toApiModel(),
        account.getBillingProvider(),
        "Billing provider should match");
  }

  private void assertBillingAccountForProduct(
      List<BillingAccount> accounts, String billingAccountId, String productTag) {
    BillingAccount account =
        accounts.stream()
            .filter(candidate -> productTag.equals(candidate.getProductTag()))
            .findFirst()
            .orElseThrow(
                () ->
                    new AssertionError(
                        "No billing account record found for product tag " + productTag));
    assertBillingAccount(account, billingAccountId, productTag);
  }
}
