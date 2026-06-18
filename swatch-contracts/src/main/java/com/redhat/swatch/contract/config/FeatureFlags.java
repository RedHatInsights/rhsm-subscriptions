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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.contract.model.PartnerGatewayContractsFeatureFlagVariantPayload;
import io.getunleash.Unleash;
import io.getunleash.variant.Variant;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import java.util.function.Function;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
@AllArgsConstructor
public class FeatureFlags {
  public static final String PARTNER_GATEWAY_CONTRACTS =
      "swatch.swatch-contracts.enable-partner-gateway-contracts";
  public static final String CONFIG_VARIANT = "config";

  protected static final boolean DEFAULT_IS_ENABLED = true;

  private final Unleash unleash;
  private final ObjectMapper mapper;

  /** Whether the Kafka consumer for partner-gateway contracts is allowed. */
  public boolean isPartnerGatewayContractsKafkaConsumerEnabled() {
    return isPartnerGatewayContractsEnabled(
        PartnerGatewayContractsFeatureFlagVariantPayload::getKafkaConsumerEnabled);
  }

  /** Whether the UMB consumer for partner-gateway contracts is allowed. */
  public boolean isPartnerGatewayContractsUmbConsumerEnabled() {
    return isPartnerGatewayContractsEnabled(
        PartnerGatewayContractsFeatureFlagVariantPayload::getUmbConsumerEnabled);
  }

  /**
   * If {@value #PARTNER_GATEWAY_CONTRACTS} is disabled, returns {@code false}.
   *
   * <p>If the toggle is enabled and Unleash returns a variant whose name is not {@value
   * #CONFIG_VARIANT}, returns {@code true} (no structured payload to interpret).
   *
   * <p>If the variant is named {@value #CONFIG_VARIANT} but {@link Variant#isEnabled()} is {@code
   * false}, returns {@code true}.
   *
   * <p>If the variant is {@value #CONFIG_VARIANT} and enabled, the variant payload is parsed as
   * JSON; this method returns the consumer_enabled flag from that object. Missing payload, invalid
   * JSON, or a null/false flag yields {@code true}.
   */
  private boolean isPartnerGatewayContractsEnabled(
      Function<PartnerGatewayContractsFeatureFlagVariantPayload, Boolean> condition) {
    if (!unleash.isEnabled(PARTNER_GATEWAY_CONTRACTS, DEFAULT_IS_ENABLED)) {
      return false;
    }

    Variant variant = unleash.getVariant(PARTNER_GATEWAY_CONTRACTS);
    if (!CONFIG_VARIANT.equals(variant.getName())) {
      log.debug("Feature flag '{}' with no valid variant '{}'", PARTNER_GATEWAY_CONTRACTS, variant);
      return true;
    }

    if (!variant.isEnabled()) {
      return true;
    }

    return mapToPartnerGatewayContractsPayload(variant).map(condition).orElse(true);
  }

  private Optional<PartnerGatewayContractsFeatureFlagVariantPayload>
      mapToPartnerGatewayContractsPayload(Variant variant) {
    var payload = variant.getPayload();
    if (payload.isEmpty()) {
      return Optional.empty();
    }

    String payloadValue = payload.get().getValue();
    try {
      return Optional.ofNullable(
          mapper.readValue(payloadValue, PartnerGatewayContractsFeatureFlagVariantPayload.class));
    } catch (JsonProcessingException e) {
      log.warn(
          "Failed to parse the payload '{}' for feature flag '{}'",
          payloadValue,
          PARTNER_GATEWAY_CONTRACTS,
          e);
      return Optional.empty();
    }
  }
}
