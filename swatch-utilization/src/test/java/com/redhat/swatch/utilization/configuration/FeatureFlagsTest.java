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
package com.redhat.swatch.utilization.configuration;

import static com.redhat.swatch.utilization.configuration.FeatureFlags.SEND_NOTIFICATIONS_CONFIG_VARIANT;
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

  private static final String EVENT_TYPE = "exceeded-utilization-threshold";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Mock Unleash unleash;
  FeatureFlags featureFlags;

  @BeforeEach
  void setUp() {
    featureFlags = new FeatureFlags(unleash, OBJECT_MAPPER);
  }

  @Test
  void shouldReturnFalse_whenSendNotificationsDisabled() {
    when(unleash.isEnabled(FeatureFlags.SEND_NOTIFICATIONS)).thenReturn(false);

    assertFalse(featureFlags.sendNotifications(EVENT_TYPE));
  }

  @Test
  void shouldReturnTrue_whenByEventTypesPayloadOmitsEventType() {
    givenSendNotificationsEnabledWithByEventTypesPayload(
        "{\"event_types_denylist\":[\"other-event\"]}");

    assertTrue(featureFlags.sendNotifications(EVENT_TYPE));
  }

  @Test
  void shouldReturnFalse_whenByEventTypesPayloadDisablesEventType() {
    givenSendNotificationsEnabledWithByEventTypesPayload(
        "{\"event_types_denylist\":[\"" + EVENT_TYPE + "\"]}");

    assertFalse(featureFlags.sendNotifications(EVENT_TYPE));
  }

  @Test
  void shouldReturnTrue_whenByEventTypesVariantHasEmptyPayloadValue() {
    givenSendNotificationsEnabledWithByEventTypesPayload("");

    assertTrue(featureFlags.sendNotifications(EVENT_TYPE));
  }

  @Test
  void shouldReturnTrue_whenByEventTypesVariantHasInvalidJsonPayloadValue() {
    givenSendNotificationsEnabledWithByEventTypesPayload("{invalid");

    assertTrue(featureFlags.sendNotifications(EVENT_TYPE));
  }

  @Test
  void shouldReturnTrue_whenOrgIsInAllowlist() {
    givenAllowlistFlagEnabledForOrgs("org1", "org2", "org3");

    assertTrue(featureFlags.isOrgAllowlistedForNotifications("org2"));
  }

  @Test
  void shouldReturnFalse_whenOrgIsNotInAllowlist() {
    givenAllowlistFlagEnabledForOrgs("org1", "org2", "org3");

    assertFalse(featureFlags.isOrgAllowlistedForNotifications("org999"));
  }

  @Test
  void shouldReturnFalse_whenAllowlistFlagIsDisabled() {
    when(unleash.isEnabled(FeatureFlags.SEND_NOTIFICATIONS_ORGS_ALLOWLIST)).thenReturn(false);

    assertFalse(featureFlags.isOrgAllowlistedForNotifications("org1"));
  }

  @Test
  void shouldReturnFalse_whenAllowlistPayloadIsEmpty() {
    givenAllowlistFlagEnabledForOrgs();

    assertFalse(featureFlags.isOrgAllowlistedForNotifications("org1"));
  }

  @Test
  void shouldReturnFalse_whenAllowlistPayloadIsNull() {
    givenAllowlistFlagEnabledWithoutOrgs();

    assertFalse(featureFlags.isOrgAllowlistedForNotifications("org1"));
  }

  @Test
  void shouldReturnTrue_whenAllowlistContainsSingleMatchingOrg() {
    givenAllowlistFlagEnabledForOrgs("org1");

    assertTrue(featureFlags.isOrgAllowlistedForNotifications("org1"));
  }

  private void givenSendNotificationsEnabledWithByEventTypesPayload(String json) {
    givenSendNotificationsEnabledWithByEventTypesVariant(new Payload("json", json));
  }

  private void givenSendNotificationsEnabledWithByEventTypesVariant(Payload payload) {
    when(unleash.isEnabled(FeatureFlags.SEND_NOTIFICATIONS)).thenReturn(true);
    Variant variant = new Variant(SEND_NOTIFICATIONS_CONFIG_VARIANT, payload, true, "any", true);
    when(unleash.getVariant(FeatureFlags.SEND_NOTIFICATIONS)).thenReturn(variant);
  }

  private void givenAllowlistFlagEnabledForOrgs(String... orgIds) {
    givenAllowlistFlagEnabled(new Payload("string", String.join(",", orgIds)));
  }

  private void givenAllowlistFlagEnabledWithoutOrgs() {
    givenAllowlistFlagEnabled(null);
  }

  private void givenAllowlistFlagEnabled(Payload payload) {
    when(unleash.isEnabled(FeatureFlags.SEND_NOTIFICATIONS_ORGS_ALLOWLIST)).thenReturn(true);
    Variant variant =
        new Variant(FeatureFlags.ORGS_VARIANT, payload, true, FeatureFlags.ORGS_VARIANT, true);
    when(unleash.getVariant(FeatureFlags.SEND_NOTIFICATIONS_ORGS_ALLOWLIST)).thenReturn(variant);
  }
}
