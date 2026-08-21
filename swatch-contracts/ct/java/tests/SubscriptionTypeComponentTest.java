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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.contract.test.model.SkuCapacityReportV1;
import com.redhat.swatch.contract.test.model.SkuCapacityReportV2;
import com.redhat.swatch.contract.test.model.SubscriptionType;
import domain.Product;
import org.junit.jupiter.api.Test;

public class SubscriptionTypeComponentTest extends BaseContractComponentTest {

  private static final double CORES_CAPACITY = 8.0;
  private static final double SOCKETS_CAPACITY = 2.0;

  @TestPlanName("subscription-type-TC001")
  @Test
  void shouldReportOnDemandSubscriptionTypeOnV1() {
    // Given: A PAYG ROSA contract for the org
    givenPaygContractIsCreated();

    // When: Querying the v1 SKU capacity report
    SkuCapacityReportV1 report = whenGetSkuCapacityReportV1(Product.ROSA);

    // Then: Meta reports On-demand subscription type
    thenSubscriptionTypeIs(report, SubscriptionType.ON_DEMAND);
  }

  @TestPlanName("subscription-type-TC002")
  @Test
  void shouldReportOnDemandSubscriptionTypeOnV2() {
    // Given: A PAYG ROSA contract for the org
    givenPaygContractIsCreated();

    // When: Querying the v2 SKU capacity report
    SkuCapacityReportV2 report = whenGetSkuCapacityReportV2(Product.ROSA);

    // Then: Meta reports On-demand subscription type
    thenSubscriptionTypeIs(report, SubscriptionType.ON_DEMAND);
  }

  @TestPlanName("subscription-type-TC003")
  @Test
  void shouldReportAnnualSubscriptionTypeOnV1() {
    // Given: A non-PAYG (annual) RHEL subscription for the org
    givenAnnualSubscriptionIsCreated();

    // When: Querying the v1 SKU capacity report
    SkuCapacityReportV1 report = whenGetSkuCapacityReportV1(Product.RHEL);

    // Then: Meta reports Annual subscription type
    thenSubscriptionTypeIs(report, SubscriptionType.ANNUAL);
  }

  @TestPlanName("subscription-type-TC004")
  @Test
  void shouldReportAnnualSubscriptionTypeOnV2() {
    // Given: A non-PAYG (annual) RHEL subscription for the org
    givenAnnualSubscriptionIsCreated();

    // When: Querying the v2 SKU capacity report
    SkuCapacityReportV2 report = whenGetSkuCapacityReportV2(Product.RHEL);

    // Then: Meta reports Annual subscription type
    thenSubscriptionTypeIs(report, SubscriptionType.ANNUAL);
  }

  private void givenPaygContractIsCreated() {
    givenRosaContractIsCreated(CORES_CAPACITY);
  }

  private void givenAnnualSubscriptionIsCreated() {
    givenPhysicalSubscriptionIsCreated(
        RandomUtils.generateRandom(), CORES_CAPACITY, SOCKETS_CAPACITY);
  }

  private SkuCapacityReportV1 whenGetSkuCapacityReportV1(Product product) {
    return service.getSkuCapacityReportV1(product, orgId);
  }

  private SkuCapacityReportV2 whenGetSkuCapacityReportV2(Product product) {
    return service.getSkuCapacityReportV2(product, orgId);
  }

  private void thenSubscriptionTypeIs(SkuCapacityReportV1 report, SubscriptionType expected) {
    assertNotNull(report, "v1 SKU capacity report should not be null");
    assertNotNull(report.getMeta(), "v1 report meta should not be null");
    thenSubscriptionTypeIs(report.getMeta().getSubscriptionType(), expected);
  }

  private void thenSubscriptionTypeIs(SkuCapacityReportV2 report, SubscriptionType expected) {
    assertNotNull(report, "v2 SKU capacity report should not be null");
    assertNotNull(report.getMeta(), "v2 report meta should not be null");
    thenSubscriptionTypeIs(report.getMeta().getSubscriptionType(), expected);
  }

  private void thenSubscriptionTypeIs(SubscriptionType actual, SubscriptionType expected) {
    assertNotNull(actual, "meta.subscriptionType should not be null");
    assertEquals(expected, actual, "meta.subscriptionType should be " + expected.getValue());
  }
}
