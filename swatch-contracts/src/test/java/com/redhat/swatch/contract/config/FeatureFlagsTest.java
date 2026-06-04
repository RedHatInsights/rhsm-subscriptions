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
import static com.redhat.swatch.contract.config.FeatureFlags.PARTNER_GATEWAY_CONTRACTS;
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
  void shouldReturnFalse_whenPartnerGatewayContractsDisabled_forKafka() {
    when(unleash.isEnabled(PARTNER_GATEWAY_CONTRACTS, DEFAULT_IS_ENABLED)).thenReturn(false);

    assertFalse(featureFlags.isPartnerGatewayContractsKafkaConsumerEnabled());
  }

  @Test
  void shouldReturnFalse_whenPartnerGatewayContractsDisabled_forUmb() {
    when(unleash.isEnabled(PARTNER_GATEWAY_CONTRACTS, DEFAULT_IS_ENABLED)).thenReturn(false);

    assertFalse(featureFlags.isPartnerGatewayContractsUmbConsumerEnabled());
  }

  @Test
  void shouldReturnTrue_whenToggleEnabledAndVariantIsNotConfig_forKafka() {
    givenPartnerGatewayContractsEnabledWithVariant(
        new Variant("disabled", new Payload("json", "{}"), true, "any", true));

    assertTrue(featureFlags.isPartnerGatewayContractsKafkaConsumerEnabled());
  }

  @Test
  void shouldReturnTrue_whenToggleEnabledAndVariantIsNotConfig_forUmb() {
    givenPartnerGatewayContractsEnabledWithVariant(
        new Variant("disabled", new Payload("json", "{}"), true, "any", true));

    assertTrue(featureFlags.isPartnerGatewayContractsUmbConsumerEnabled());
  }

  @Test
  void shouldReturnTrue_whenConfigVariantNotEnabled_forKafka() {
    givenPartnerGatewayContractsEnabledWithVariant(
        new Variant(
            CONFIG_VARIANT,
            new Payload("json", "{\"kafka_consumer_enabled\":false}"),
            false,
            "any",
            true));

    assertTrue(featureFlags.isPartnerGatewayContractsKafkaConsumerEnabled());
  }

  @Test
  void shouldReturnTrue_whenConfigVariantNotEnabled_forUmb() {
    givenPartnerGatewayContractsEnabledWithVariant(
        new Variant(
            CONFIG_VARIANT,
            new Payload("json", "{\"umb_consumer_enabled\":false}"),
            false,
            "any",
            true));

    assertTrue(featureFlags.isPartnerGatewayContractsUmbConsumerEnabled());
  }

  @Test
  void shouldReturnTrue_whenConfigVariantEnabled_withKafkaTrueInPayload() {
    givenPartnerGatewayContractsEnabledWithConfigPayload("{\"kafka_consumer_enabled\":true}");

    assertTrue(featureFlags.isPartnerGatewayContractsKafkaConsumerEnabled());
  }

  @Test
  void shouldReturnFalse_whenConfigVariantEnabled_withKafkaFalseInPayload() {
    givenPartnerGatewayContractsEnabledWithConfigPayload("{\"kafka_consumer_enabled\":false}");

    assertFalse(featureFlags.isPartnerGatewayContractsKafkaConsumerEnabled());
  }

  @Test
  void shouldReturnTrue_whenConfigVariantEnabled_withUmbTrueInPayload() {
    givenPartnerGatewayContractsEnabledWithConfigPayload("{\"umb_consumer_enabled\":true}");

    assertTrue(featureFlags.isPartnerGatewayContractsUmbConsumerEnabled());
  }

  @Test
  void shouldReturnFalse_whenConfigVariantEnabled_withUmbFalseInPayload() {
    givenPartnerGatewayContractsEnabledWithConfigPayload("{\"umb_consumer_enabled\":false}");

    assertFalse(featureFlags.isPartnerGatewayContractsUmbConsumerEnabled());
  }

  @Test
  void shouldReturnTrue_whenConfigVariantEnabled_withEmptyJsonObject_forKafka() {
    givenPartnerGatewayContractsEnabledWithConfigPayload("{}");

    assertTrue(featureFlags.isPartnerGatewayContractsKafkaConsumerEnabled());
  }

  @Test
  void shouldReturnTrue_whenConfigVariantEnabled_withEmptyJsonObject_forUmb() {
    givenPartnerGatewayContractsEnabledWithConfigPayload("{}");

    assertTrue(featureFlags.isPartnerGatewayContractsUmbConsumerEnabled());
  }

  @Test
  void shouldDecoupleKafkaAndUmb_whenConfigVariantEnabled_withBothFlagsInPayload() {
    givenPartnerGatewayContractsEnabledWithConfigPayload(
        "{\"kafka_consumer_enabled\":false,\"umb_consumer_enabled\":true}");

    assertFalse(featureFlags.isPartnerGatewayContractsKafkaConsumerEnabled());
    assertTrue(featureFlags.isPartnerGatewayContractsUmbConsumerEnabled());
  }

  @Test
  void shouldReturnTrue_whenConfigVariantEnabled_withEmptyPayloadValue_forKafka() {
    givenPartnerGatewayContractsEnabledWithConfigPayload("");

    assertTrue(featureFlags.isPartnerGatewayContractsKafkaConsumerEnabled());
  }

  @Test
  void shouldReturnTrue_whenConfigVariantEnabled_withInvalidJsonPayload_forKafka() {
    givenPartnerGatewayContractsEnabledWithConfigPayload("{invalid");

    assertTrue(featureFlags.isPartnerGatewayContractsKafkaConsumerEnabled());
  }

  @Test
  void shouldReturnTrue_whenConfigVariantEnabled_withInvalidJsonPayload_forUmb() {
    givenPartnerGatewayContractsEnabledWithConfigPayload("{invalid");

    assertTrue(featureFlags.isPartnerGatewayContractsUmbConsumerEnabled());
  }

  private void givenPartnerGatewayContractsEnabledWithConfigPayload(String json) {
    givenPartnerGatewayContractsEnabledWithVariant(
        new Variant(CONFIG_VARIANT, new Payload("json", json), true, "any", true));
  }

  private void givenPartnerGatewayContractsEnabledWithVariant(Variant variant) {
    when(unleash.isEnabled(PARTNER_GATEWAY_CONTRACTS, DEFAULT_IS_ENABLED)).thenReturn(true);
    when(unleash.getVariant(PARTNER_GATEWAY_CONTRACTS)).thenReturn(variant);
  }
}
