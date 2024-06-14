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
package com.redhat.swatch.configuration.registry;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class SubscriptionDefinitionTest {
  @Test
  void testFindServiceTypeMatch() {
    var rosaSub = SubscriptionDefinition.findByServiceType("rosa Instance").get(0);

    var expected = "rosa";
    var actual = rosaSub.getId();

    assertEquals(expected, actual);
  }

  @Test
  void testFindServiceTypeNoMatch() {
    var actual = SubscriptionDefinition.findByServiceType("bananas");
    assertTrue(actual.isEmpty());
  }

  @Test
  void testGetAllServiceTypes() {
    var expected =
        List.of(
            "OpenShift Cluster",
            "BASILISK Instance",
            "Rhacs Cluster",
            "Rhods Cluster",
            "rosa Instance",
            "RHEL System");
    var actual = SubscriptionDefinition.getAllServiceTypes();

    assertThat(actual, Matchers.containsInAnyOrder(expected.toArray()));
  }

  @Test
  void testGetMetricIds() {
    var basiliskSub = SubscriptionDefinition.findById("basilisk-test").orElseThrow();

    var actual = basiliskSub.getMetricIds();
    var expected = List.of("Transfer-gibibytes", "Instance-hours", "Storage-gibibyte-months");

    assertThat(actual, Matchers.containsInAnyOrder(expected.toArray()));
  }

  @Test
  void testGetMetricNoMatch() {
    var basiliskSub = SubscriptionDefinition.findById("basilisk-test").orElseThrow();

    var expected = Optional.empty();
    var actual = basiliskSub.getMetric("bananas");

    assertEquals(expected, actual);
  }

  @Test
  void testGetMetric() {
    var basiliskSub = SubscriptionDefinition.findById("basilisk-test").orElseThrow();

    var metric = new Metric();
    metric.setId("Instance-hours");
    metric.setRhmMetricId("redhat.com:BASILISK:cluster_hour");
    metric.setAwsDimension("cluster_hour");
    metric.setAzureDimension("cluster_hour");

    var prometheusMetric = new PrometheusMetric();
    prometheusMetric.setQueryKey("default");
    prometheusMetric.setQueryParams(
        Map.of(
            "instanceKey",
            "_id",
            "product",
            "BASILISK",
            "metric",
            "kafka_id:strimzi_resource_state:max_over_time1h",
            "metadataMetric",
            "ocm_subscription"));
    metric.setPrometheus(prometheusMetric);

    var expected = Optional.of(metric);
    var actual = basiliskSub.getMetric("Instance-hours");

    assertEquals(expected, actual);
  }

  @Test
  void testGetMetricIdsUom() {
    var openshiftContainerPlatformSub =
        SubscriptionDefinition.findById("openshift-container-platform").orElseThrow();

    var expected = List.of("Sockets", "Cores");
    var actual = openshiftContainerPlatformSub.getMetricIds();

    assertThat(actual, Matchers.containsInAnyOrder(expected.toArray()));
  }

  @Test
  void testFindById() {
    var basiliskSub = SubscriptionDefinition.findById("basilisk-test").orElseThrow();

    var expected = "basilisk-test";
    var actual = basiliskSub.getId();

    assertEquals(expected, actual);
  }

  @ParameterizedTest
  @CsvSource({"basilisk-test,true", "rhel-for-arm,false"})
  void testIsPrometheusEnabled(String input, boolean expected) {
    var subscription = SubscriptionDefinition.findById(input).orElseThrow();

    assertEquals(subscription.isPrometheusEnabled(), expected);
  }

  @ParameterizedTest
  @MethodSource("generateFinestGranularityCases")
  void testGetFinestGranularity(
      String subscriptionDefinitionId, SubscriptionDefinitionGranularity expected) {
    var subscription = SubscriptionDefinition.findById(subscriptionDefinitionId).orElseThrow();
    assertEquals(subscription.getFinestGranularity(), expected);
  }

  private static Stream<Arguments> generateFinestGranularityCases() {
    return Stream.of(
        Arguments.of("basilisk-test", SubscriptionDefinitionGranularity.HOURLY),
        Arguments.of("rhel-for-arm", SubscriptionDefinitionGranularity.DAILY));
  }

  @Test
  void testGetSupportedGranularityProm() {
    var basiliskSub = SubscriptionDefinition.findById("basilisk-test").orElseThrow();

    var actual = basiliskSub.getSupportedGranularity();
    var expected =
        List.of(
            SubscriptionDefinitionGranularity.HOURLY,
            SubscriptionDefinitionGranularity.DAILY,
            SubscriptionDefinitionGranularity.WEEKLY,
            SubscriptionDefinitionGranularity.MONTHLY,
            SubscriptionDefinitionGranularity.QUARTERLY,
            SubscriptionDefinitionGranularity.YEARLY);

    assertThat(actual, Matchers.containsInAnyOrder(expected.toArray()));
  }

  @Test
  void testGetSupportedGranularityNonProm() {
    var rhelForArmSub = SubscriptionDefinition.findById("rhel-for-arm").orElseThrow();

    var actual = rhelForArmSub.getSupportedGranularity();
    var expected =
        List.of(
            SubscriptionDefinitionGranularity.DAILY,
            SubscriptionDefinitionGranularity.WEEKLY,
            SubscriptionDefinitionGranularity.MONTHLY,
            SubscriptionDefinitionGranularity.QUARTERLY,
            SubscriptionDefinitionGranularity.YEARLY);

    assertThat(actual, Matchers.containsInAnyOrder(expected.toArray()));
  }

  @Test
  void testFingerprintEngIdLookup() {
    var satelliteCapsule = SubscriptionDefinition.lookupSubscriptionByEngId("269");

    var expected = "satellite-capsule";
    var actual = satelliteCapsule.stream().findFirst();
    actual.ifPresent(
        subscriptionDefinition -> assertEquals(expected, subscriptionDefinition.getId()));
  }

  @Test
  void testVariantEngIdLookup() {
    var rhelForX86 = SubscriptionDefinition.lookupSubscriptionByEngId("76");

    var expected = "rhel-for-x86";
    var actual = rhelForX86.stream().findFirst();

    actual.ifPresent(
        subscriptionDefinition -> assertEquals(expected, subscriptionDefinition.getId()));
  }

  @Test
  void testVariantRoleLookup() {
    var rosa = SubscriptionDefinition.lookupSubscriptionByRole("moa-hostedcontrolplane");

    var expected = "rosa";
    var actual = rosa.orElseThrow().getId();

    assertEquals(expected, actual);
  }

  @Test
  void testLookupSubscriptionByTag() {
    assertNotNull(SubscriptionDefinition.lookupSubscriptionByTag("BASILISK"));
  }

  @Test
  void testGetRhmDimension() {
    assertEquals(
        "redhat.com:BASILISK:cluster_hour",
        SubscriptionDefinition.getRhmMetricId("BASILISK", "Instance-hours"));
  }

  @Test
  void testGetAwsDimension() {
    assertEquals(
        "cluster_hour", SubscriptionDefinition.getAwsDimension("BASILISK", "Instance-hours"));
  }

  @Test
  void testGetAzureDimension() {
    assertEquals(
        "cluster_hour", SubscriptionDefinition.getAzureDimension("BASILISK", "Instance-hours"));
  }

  @SuppressWarnings({"linelength", "indentation"})
  @ParameterizedTest(
      name =
          "[engineeringIds: {0}, isMetered: {1}, is3rdPartyConverted {2}] matches product tags {3}")
  @MethodSource("elsAndGeneralRhelCombos")
  void testElsDetectionByEngineeringIds(
      Set<String> engineeringIds,
      boolean isMetered,
      boolean is3rdPartyConverted,
      Set<String> expectedProductTags) {

    var actual =
        SubscriptionDefinition.getAllProductTagsByRoleOrEngIds(
            null, engineeringIds, null, isMetered, is3rdPartyConverted);

    assertEquals(expectedProductTags, actual);
  }

  @SuppressWarnings({"linelength", "indentation"})
  private static Stream<Arguments> elsAndGeneralRhelCombos() {
    var isMetered = true;
    var is3rdPartyConverted = true;

    var generalRhel = "479";
    var els = "204";

    return Stream.of(
        Arguments.of(Set.of(generalRhel), !isMetered, !is3rdPartyConverted, Set.of("RHEL for x86")),
        Arguments.of(
            Set.of(generalRhel, els),
            !isMetered,
            !is3rdPartyConverted,
            Set.of("RHEL for x86", "rhel-for-x86-els-unconverted")),
        Arguments.of(Set.of(generalRhel), !isMetered, is3rdPartyConverted, Set.of()),
        Arguments.of(
            Set.of(generalRhel, els),
            !isMetered,
            is3rdPartyConverted,
            Set.of("rhel-for-x86-els-converted")),
        Arguments.of(
            Set.of(els), !isMetered, is3rdPartyConverted, Set.of("rhel-for-x86-els-converted")),
        Arguments.of(Set.of(generalRhel), isMetered, !is3rdPartyConverted, Set.of()),
        Arguments.of(Set.of(generalRhel), isMetered, is3rdPartyConverted, Set.of()),
        Arguments.of(
            Set.of(generalRhel, els),
            isMetered,
            is3rdPartyConverted,
            Set.of("rhel-for-x86-els-payg")),
        Arguments.of(
            Set.of(generalRhel, els),
            isMetered,
            !is3rdPartyConverted,
            Set.of("rhel-for-x86-els-payg-addon", "RHEL for x86")),
        Arguments.of(
            Set.of(els), isMetered, !is3rdPartyConverted, Set.of("rhel-for-x86-els-payg-addon")));
  }
}
