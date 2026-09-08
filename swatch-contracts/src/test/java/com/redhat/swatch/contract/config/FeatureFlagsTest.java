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
package com.redhat.swatch.contract.config;

import static com.redhat.swatch.contract.config.FeatureFlags.CONFIG_VARIANT;
import static com.redhat.swatch.contract.config.FeatureFlags.DEFAULT_IS_ENABLED;
import static com.redhat.swatch.contract.config.FeatureFlags.IT_SUBSCRIPTION_SERVICE;
import static com.redhat.swatch.contract.config.FeatureFlags.PRODUCT_SERVICE_CONSUMER;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.getunleash.Unleash;
import io.getunleash.variant.Payload;
import io.getunleash.variant.Variant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeatureFlagsTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Mock Unleash unleash;
  FeatureFlags featureFlags;

  @BeforeEach
  void setUp() {
    featureFlags = new FeatureFlags(unleash, OBJECT_MAPPER);
  }

  @Test
  void shouldReturnFalse_whenItSubscriptionServiceDisabled() {
    when(unleash.isEnabled(IT_SUBSCRIPTION_SERVICE, DEFAULT_IS_ENABLED)).thenReturn(false);

    assertFalse(featureFlags.isItSubscriptionServiceKafkaConsumerEnabled());
    assertFalse(featureFlags.isItSubscriptionServiceUmbConsumerEnabled());
  }

  @Test
  void shouldRespectItSubscriptionServiceKafkaConsumerFlag() {
    givenItSubscriptionServiceEnabledWithConfigPayload("{\"kafka_consumer_enabled\":false}");

    assertFalse(featureFlags.isItSubscriptionServiceKafkaConsumerEnabled());
  }

  @Test
  void shouldRespectItSubscriptionServiceUmbConsumerFlag() {
    givenItSubscriptionServiceEnabledWithConfigPayload("{\"umb_consumer_enabled\":false}");

    assertFalse(featureFlags.isItSubscriptionServiceUmbConsumerEnabled());
  }

  @Test
  void shouldDecoupleKafkaAndUmbForItSubscriptionService() {
    givenItSubscriptionServiceEnabledWithConfigPayload(
        "{\"kafka_consumer_enabled\":false,\"umb_consumer_enabled\":true}");

    assertFalse(featureFlags.isItSubscriptionServiceKafkaConsumerEnabled());
    assertTrue(featureFlags.isItSubscriptionServiceUmbConsumerEnabled());
  }

  private void givenItSubscriptionServiceEnabledWithConfigPayload(String json) {
    givenItSubscriptionServiceEnabledWithVariant(
        new Variant(CONFIG_VARIANT, new Payload("json", json), true, "any", true));
  }

  private void givenItSubscriptionServiceEnabledWithVariant(Variant variant) {
    when(unleash.isEnabled(IT_SUBSCRIPTION_SERVICE, DEFAULT_IS_ENABLED)).thenReturn(true);
    when(unleash.getVariant(IT_SUBSCRIPTION_SERVICE)).thenReturn(variant);
  }

  @Test
  void shouldReturnFalse_whenProductServiceConsumerDisabled() {
    when(unleash.isEnabled(PRODUCT_SERVICE_CONSUMER, DEFAULT_IS_ENABLED)).thenReturn(false);

    assertFalse(featureFlags.isProductServiceKafkaConsumerEnabled());
    assertFalse(featureFlags.isProductServiceUmbConsumerEnabled());
  }

  @Test
  void shouldRespectProductServiceKafkaConsumerFlag() {
    givenProductServiceConsumerEnabledWithConfigPayload("{\"kafka_consumer_enabled\":false}");

    assertFalse(featureFlags.isProductServiceKafkaConsumerEnabled());
  }

  @Test
  void shouldRespectProductServiceUmbConsumerFlag() {
    givenProductServiceConsumerEnabledWithConfigPayload("{\"umb_consumer_enabled\":false}");

    assertFalse(featureFlags.isProductServiceUmbConsumerEnabled());
  }

  @Test
  void shouldDecoupleKafkaAndUmbForProductServiceConsumer() {
    givenProductServiceConsumerEnabledWithConfigPayload(
        "{\"kafka_consumer_enabled\":false,\"umb_consumer_enabled\":true}");

    assertFalse(featureFlags.isProductServiceKafkaConsumerEnabled());
    assertTrue(featureFlags.isProductServiceUmbConsumerEnabled());
  }

  private void givenProductServiceConsumerEnabledWithConfigPayload(String json) {
    givenProductServiceConsumerEnabledWithVariant(
        new Variant(CONFIG_VARIANT, new Payload("json", json), true, "any", true));
  }

  private void givenProductServiceConsumerEnabledWithVariant(Variant variant) {
    when(unleash.isEnabled(PRODUCT_SERVICE_CONSUMER, DEFAULT_IS_ENABLED)).thenReturn(true);
    when(unleash.getVariant(PRODUCT_SERVICE_CONSUMER)).thenReturn(variant);
  }
}
