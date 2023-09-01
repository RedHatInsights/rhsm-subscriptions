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
package org.candlepin.subscriptions.tally.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import com.redhat.swatch.configuration.registry.SubscriptionDefinitionRegistry;
import com.redhat.swatch.configuration.registry.Variant;
import java.lang.reflect.Field;
import java.util.List;
import org.candlepin.subscriptions.db.model.BillableUsageRemittanceEntity;
import org.candlepin.subscriptions.json.BillableUsage;
import org.candlepin.subscriptions.json.BillableUsage.Uom;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QuantityTest {
  private static SubscriptionDefinitionRegistry originalReference;

  private SubscriptionDefinitionRegistry subscriptionDefinitionRegistry;

  @BeforeAll
  static void setupClass() throws Exception {
    Field instance = SubscriptionDefinitionRegistry.class.getDeclaredField("instance");
    instance.setAccessible(true);
    originalReference =
        (SubscriptionDefinitionRegistry) instance.get(SubscriptionDefinitionRegistry.class);
  }

  @AfterAll
  static void tearDown() throws Exception {
    Field instance = SubscriptionDefinitionRegistry.class.getDeclaredField("instance");
    instance.setAccessible(true);
    instance.set(instance, originalReference);
  }

  @BeforeEach
  void setupTest() {
    subscriptionDefinitionRegistry = mock(SubscriptionDefinitionRegistry.class);
    setMock(subscriptionDefinitionRegistry);
  }

  private void setMock(SubscriptionDefinitionRegistry mock) {
    try {
      Field instance = SubscriptionDefinitionRegistry.class.getDeclaredField("instance");
      instance.setAccessible(true);
      instance.set(instance, mock);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void createSubscriptionDefinition(String tag, String uom) {
    var variant = Variant.builder().tag(tag).build();
    var awsMetric =
        com.redhat.swatch.configuration.registry.Metric.builder()
            .awsDimension("AWS_METRIC_ID")
            .billingFactor(0.25)
            .id(uom)
            .build();
    var subscriptionDefinition =
        SubscriptionDefinition.builder()
            .variants(List.of(variant))
            .metrics(List.of(awsMetric))
            .build();
    variant.setSubscription(subscriptionDefinition);
    when(subscriptionDefinitionRegistry.getSubscriptions())
        .thenReturn(List.of(subscriptionDefinition));
  }

  @Test
  void testMetricUnitHasBillingFactorOf1() {
    var quantity = Quantity.of(1.0);
    assertEquals(1.0, quantity.getValue());
    assertEquals(1.0, quantity.getUnit().getBillingFactor());
  }

  @Test
  void testMetricUnitToMetricUnitPreservesValue() {
    var quantity = Quantity.of(1.456);
    var converted = quantity.to(new MetricUnit());
    assertEquals(converted, quantity);
  }

  @Test
  void testQuantityFromBillableUsage() {
    var billableUsage = new BillableUsage();
    billableUsage.setUom(Uom.SOCKETS);
    billableUsage.setProductId("foo");
    createSubscriptionDefinition(billableUsage.getProductId(), billableUsage.getUom().toString());
    var billingUnit = new BillingUnit(billableUsage);
    assertEquals(0.25, billingUnit.getBillingFactor());
    var billableQuantity = Quantity.of(4.0).to(billingUnit);
    assertEquals(billingUnit, billableQuantity.getUnit());
    assertEquals(1.0, billableQuantity.getValue());
  }

  @Test
  void testAddingBillableUnitToMetricUnit() {
    var quantity = Quantity.of(1.5);
    var billableUsage = new BillableUsage();
    billableUsage.setUom(Uom.SOCKETS);
    billableUsage.setProductId("productId");
    createSubscriptionDefinition(billableUsage.getProductId(), billableUsage.getUom().toString());
    var billingUnit = new BillingUnit(billableUsage);
    var billable = Quantity.of(4.0).to(billingUnit);
    assertEquals(1.0, billable.getValue());
    var result = quantity.add(billable);
    assertEquals(5.5, result.getValue());
    assertEquals(new MetricUnit(), result.getUnit());
  }

  @Test
  void testSubtractingBillableUnitFromMetricUnit() {
    var quantity = Quantity.of(1.5);
    var billableUsage = new BillableUsage();
    billableUsage.setUom(Uom.SOCKETS);
    billableUsage.setProductId("productId");
    createSubscriptionDefinition(billableUsage.getProductId(), billableUsage.getUom().toString());
    var billingUnit = new BillingUnit(billableUsage);
    var billable = Quantity.of(1.0).to(billingUnit);
    assertEquals(0.25, billable.getValue());
    var result = quantity.subtract(billable);
    assertEquals(0.5, result.getValue());
    assertEquals(new MetricUnit(), result.getUnit());
  }

  @Test
  void testCeil() {
    assertEquals(1.0, Quantity.of(0.1).ceil().getValue());
    assertEquals(1.0, Quantity.of(0.9).ceil().getValue());
  }

  @Test
  void testPositiveOrZero() {
    assertEquals(0.0, Quantity.of(-0.0).positiveOrZero().getValue());
    assertEquals(0.0, Quantity.of(-2.0).positiveOrZero().getValue());
    assertEquals(0.0, Quantity.of(0.0).positiveOrZero().getValue());
  }

  @Test
  void testFromRemittance() {
    var remittance = new BillableUsageRemittanceEntity();
    remittance.setRemittedPendingValue(2.5);
    var quantity = Quantity.fromRemittance(remittance, 0.25);
    assertEquals(0.25, quantity.getUnit().getBillingFactor());
    assertEquals(2.5, quantity.getValue());
    assertEquals(10.0, quantity.to(new MetricUnit()).getValue());
  }
}
