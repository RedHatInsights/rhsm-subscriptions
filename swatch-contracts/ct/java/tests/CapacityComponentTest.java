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

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.contract.test.model.SkuCapacityV2;
import domain.Product;
import domain.Subscription;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("capacity")
public class CapacityComponentTest extends BaseContractComponentTest {

  private static final double RHEL_CORES_CAPACITY = 4.0;
  private static final double RHEL_SOCKETS_CAPACITY = 1.0;

  @Test
  void shouldValidateSumOfAllSocketsForHypervisorSkus() {
    // Given: Get initial hypervisor capacity via REST API
    final OffsetDateTime beginning = clock.now().minusDays(1);
    final OffsetDateTime ending = clock.now().plusDays(1);
    final String hypervisor_sku = RandomUtils.generateRandom();
    double initialHypervisorSockets =
        getHypervisorSocketCapacity(Product.RHEL, orgId, beginning, ending);

    Subscription hypervisorSubscription =
        givenHypervisorSubscriptionIsCreated(
            hypervisor_sku, RHEL_CORES_CAPACITY, RHEL_SOCKETS_CAPACITY);

    // Then: Verify hypervisor capacity increased via REST API
    double expectedCapacity = initialHypervisorSockets + RHEL_SOCKETS_CAPACITY;
    double finalHypervisorSockets =
        await("Hypervisor capacity should increase")
            .atMost(1, MINUTES)
            .pollInterval(2, SECONDS)
            .until(
                () -> getHypervisorSocketCapacity(Product.RHEL, orgId, beginning, ending),
                capacity -> capacity >= expectedCapacity);

    assertThat(
        "Hypervisor sockets capacity should increase by subscription amount",
        finalHypervisorSockets,
        equalTo(expectedCapacity));

    // Then: Verify the hypervisor SKU details via subscription table API
    Optional<SkuCapacityV2> skuCapacity =
        service.getSkuCapacityByProductIdForOrgAndSku(Product.RHEL, orgId, hypervisor_sku);
    assertTrue(skuCapacity.isPresent(), "Hypervisor SKU should be present in subscription table");

    assertThat(
        "Hypervisor SKU product name should not be null",
        skuCapacity.get().getProductName(),
        notNullValue());

    assertTrue(
        containsSubscription(skuCapacity.get(), hypervisorSubscription),
        "Hypervisor SKU should contain the created subscription");
  }

  @Test
  void shouldValidateSumOfAllSocketsForPhysicalSkus() {
    // Given: Get initial physical capacity via REST API
    final OffsetDateTime beginning = clock.now().minusDays(1);
    final OffsetDateTime ending = clock.now().plusDays(1);
    final String physicalSku = RandomUtils.generateRandom();
    double initialPhysicalSockets =
        getPhysicalSocketCapacity(Product.RHEL, orgId, beginning, ending);

    Subscription physicalSubscription =
        givenPhysicalSubscriptionIsCreated(physicalSku, RHEL_CORES_CAPACITY, RHEL_SOCKETS_CAPACITY);

    // Then: Verify physical capacity increased via REST API
    double expectedCapacity = initialPhysicalSockets + RHEL_SOCKETS_CAPACITY;
    double finalPhysicalSockets =
        await("Physical capacity should increase")
            .atMost(1, MINUTES)
            .pollInterval(2, SECONDS)
            .until(
                () -> getPhysicalSocketCapacity(Product.RHEL, orgId, beginning, ending),
                capacity -> capacity >= expectedCapacity);

    assertThat(
        "Physical sockets capacity should increase by subscription amount",
        finalPhysicalSockets,
        equalTo(expectedCapacity));

    // Then: Verify the physical SKU details via subscription table API
    Optional<SkuCapacityV2> skuCapacity =
        service.getSkuCapacityByProductIdForOrgAndSku(Product.RHEL, orgId, physicalSku);
    assertTrue(skuCapacity.isPresent(), "Physical SKU should be present in subscription table");

    assertThat(
        "Physical SKU product name should not be null",
        skuCapacity.get().getProductName(),
        notNullValue());

    assertTrue(
        containsSubscription(skuCapacity.get(), physicalSubscription),
        "Physical SKU should contain the created subscription");
  }

  private boolean containsSubscription(SkuCapacityV2 skuCapacity, Subscription subscription) {
    return skuCapacity.getSubscriptions() != null
        && skuCapacity.getSubscriptions().stream()
            .anyMatch(s -> subscription.getSubscriptionId().equals(s.getId()));
  }
}
