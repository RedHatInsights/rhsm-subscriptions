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
package com.redhat.swatch.contract.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.redhat.swatch.clients.swatch.internal.subscription.api.model.TagMetric;
import com.redhat.swatch.clients.swatch.internal.subscription.api.resources.ApiException;
import com.redhat.swatch.clients.swatch.internal.subscription.api.resources.InternalSubscriptionsApi;
import com.redhat.swatch.contract.repository.BillingProvider;
import com.redhat.swatch.contract.repository.SubscriptionEntity;
import com.redhat.swatch.contract.repository.SubscriptionMeasurementEntity;
import com.redhat.swatch.contract.repository.SubscriptionProductIdEntity;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MeasurementMetricIdTransformerTest {
  @Mock InternalSubscriptionsApi internalSubscriptionsApi;
  private MeasurementMetricIdTransformer transformer;

  @BeforeEach
  void setup() {
    transformer = new MeasurementMetricIdTransformer();
    transformer.internalSubscriptionsApi = internalSubscriptionsApi;
  }

  @Test
  void testMapsAwsDimensionToMetricId() throws ApiException, RuntimeException {
    var subscription = new SubscriptionEntity();
    subscription.setBillingProvider(BillingProvider.AWS);
    var measurement1 = new SubscriptionMeasurementEntity();
    measurement1.setMetricId("bar");
    var measurement2 = new SubscriptionMeasurementEntity();
    measurement2.setMetricId("foo");
    measurement2.setValue(100.0);
    subscription.addSubscriptionMeasurement(measurement1);
    subscription.addSubscriptionMeasurement(measurement2);
    SubscriptionProductIdEntity productId = new SubscriptionProductIdEntity();
    productId.setProductId("hello");
    subscription.addSubscriptionProductId(productId);

    when(internalSubscriptionsApi.getTagMetrics("hello"))
        .thenReturn(
            List.of(
                new TagMetric().metricId("foo1").awsDimension("foo").billingFactor(0.25),
                new TagMetric().metricId("bar2").awsDimension("bar").billingFactor(1.0)));
    transformer.translateContractMetricIdsToSubscriptionMetricIds(subscription);
    assertEquals(
        List.of("bar2", "foo1"),
        subscription.getSubscriptionMeasurements().stream()
            .map(SubscriptionMeasurementEntity::getMetricId)
            .collect(Collectors.toList()));
    assertEquals(
        400.0,
        subscription.getSubscriptionMeasurements().stream()
            .filter(m -> m.getMetricId().startsWith("foo"))
            .findFirst()
            .orElseThrow()
            .getValue());
  }

  @Test
  void testNoMappingAttemptedForMissingBillingProvider() throws ApiException, RuntimeException {
    var subscription = new SubscriptionEntity();
    var measurement1 = new SubscriptionMeasurementEntity();
    measurement1.setMetricId("bar");
    var measurement2 = new SubscriptionMeasurementEntity();
    measurement2.setMetricId("foo");
    subscription.addSubscriptionMeasurement(measurement1);
    subscription.addSubscriptionMeasurement(measurement2);
    SubscriptionProductIdEntity productId = new SubscriptionProductIdEntity();
    productId.setProductId("hello");
    subscription.addSubscriptionProductId(productId);

    transformer.translateContractMetricIdsToSubscriptionMetricIds(subscription);
    verifyNoInteractions(internalSubscriptionsApi);
  }
}
