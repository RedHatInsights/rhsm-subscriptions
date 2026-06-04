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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.contract.test.model.ReportCategory;
import com.redhat.swatch.contract.test.model.SkuCapacityV2;
import domain.Product;
import domain.Subscription;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("capacity")
public class CapacityComponentTest extends BaseContractComponentTest {

  private static final double RHEL_CORES_CAPACITY = 4.0;
  private static final double RHEL_SOCKETS_CAPACITY = 1.0;

  private OffsetDateTime beginning;
  private OffsetDateTime ending;
  private String sku;

  @BeforeEach
  void setUp() {
    super.setUp();
    beginning = clock.now().minusDays(1);
    ending = clock.now().plusDays(1);
    sku = RandomUtils.generateRandom();
  }

  @Test
  void shouldValidateSumOfAllSocketsForHypervisorSkus() {
    // Given: Get initial hypervisor capacity via REST API
    double initialHypervisorSockets = getDailyHypervisorSocketCapacity();

    Subscription hypervisorSubscription =
        givenHypervisorSubscriptionIsCreated(sku, RHEL_CORES_CAPACITY, RHEL_SOCKETS_CAPACITY);

    // Then: Verify hypervisor capacity increased via REST API
    double expectedCapacity = initialHypervisorSockets + RHEL_SOCKETS_CAPACITY;
    double finalHypervisorSockets =
        AwaitilityUtils.until(
            this::getDailyHypervisorSocketCapacity, capacity -> capacity >= expectedCapacity);

    assertEquals(
        expectedCapacity,
        finalHypervisorSockets,
        "Hypervisor sockets capacity should increase by subscription amount");

    // Then: Verify the hypervisor SKU details via subscription table API
    Optional<SkuCapacityV2> skuCapacity =
        service.getSkuCapacityByProductIdForOrgAndSku(Product.RHEL, orgId, sku);
    assertTrue(skuCapacity.isPresent(), "Hypervisor SKU should be present in subscription table");

    assertNotNull(
        skuCapacity.get().getProductName(), "Hypervisor SKU product name should not be null");

    assertTrue(
        containsSubscription(skuCapacity.get(), hypervisorSubscription),
        "Hypervisor SKU should contain the created subscription");
  }

  @Test
  void shouldValidateSumOfAllSocketsForPhysicalSkus() {
    // Given: Get initial physical capacity via REST API
    double initialPhysicalSockets = getDailyPhysicalSocketCapacity();

    Subscription physicalSubscription =
        givenPhysicalSubscriptionIsCreated(sku, RHEL_CORES_CAPACITY, RHEL_SOCKETS_CAPACITY);

    // Then: Verify physical capacity increased via REST API
    double expectedCapacity = initialPhysicalSockets + RHEL_SOCKETS_CAPACITY;
    double finalPhysicalSockets =
        AwaitilityUtils.until(
            this::getDailyPhysicalSocketCapacity, capacity -> capacity >= expectedCapacity);

    assertEquals(
        expectedCapacity,
        finalPhysicalSockets,
        "Physical sockets capacity should increase by subscription amount");

    // Then: Verify the physical SKU details via subscription table API
    Optional<SkuCapacityV2> skuCapacity =
        service.getSkuCapacityByProductIdForOrgAndSku(Product.RHEL, orgId, sku);
    assertTrue(skuCapacity.isPresent(), "Physical SKU should be present in subscription table");

    assertNotNull(
        skuCapacity.get().getProductName(), "Physical SKU product name should not be null");

    assertTrue(
        containsSubscription(skuCapacity.get(), physicalSubscription),
        "Physical SKU should contain the created subscription");
  }

  private double getDailyPhysicalSocketCapacity() {
    return getDailyCapacityByCategoryAndMetric(
        Product.RHEL, orgId, beginning, ending, ReportCategory.PHYSICAL, SOCKETS);
  }

  private double getDailyHypervisorSocketCapacity() {
    return getDailyCapacityByCategoryAndMetric(
        Product.RHEL, orgId, beginning, ending, ReportCategory.HYPERVISOR, SOCKETS);
  }

  private boolean containsSubscription(SkuCapacityV2 skuCapacity, Subscription subscription) {
    return skuCapacity.getSubscriptions() != null
        && skuCapacity.getSubscriptions().stream()
            .anyMatch(s -> subscription.getSubscriptionId().equals(s.getId()));
  }
}
