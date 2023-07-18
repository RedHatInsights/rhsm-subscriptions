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
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SubscriptionTest {

  @Test
  void sanityCheck() {
    assertTrue(true);
  }

  @Test
  void testFindServiceTypeMatch() {

    var rosaSub = Subscription.findByServiceType("rosa Instance").get();

    var expected = "rosa";
    var actual = rosaSub.getId();

    assertEquals(expected, actual);
  }

  @Test
  void testFindServiceTypeNoMatch() {

    var expected = Optional.empty();
    var actual = Subscription.findByServiceType("bananas");

    assertEquals(expected, actual);
  }

  @Test
  void testGetAllServiceTypes() {

    var expected =
        List.of(
            "OpenShift Cluster",
            "OpenShift Cluster",
            "BASILISK Instance",
            "Rhacs Cluster",
            "Rhods Cluster",
            "Kafka Cluster",
            "rosa Instance");
    var actual = Subscription.getAllServiceTypes();

    assertThat(actual, Matchers.containsInAnyOrder(expected.toArray()));
  }

  @Test
  void testGetMetricIds() {

    var basiliskSub = Subscription.findById("basilisk-test").get();

    var actual = basiliskSub.getMetricIds();
    var expected =
        List.of(
            "redhat.com:BASILISK:transfer_gb",
            "redhat.com:BASILISK:cluster_hour",
            "redhat.com:BASILISK:storage_gib_months");

    assertThat(actual, Matchers.containsInAnyOrder(expected.toArray()));
  }

  @Test
  void testGetMetricNoMatch() {

    var basiliskSub = Subscription.findById("basilisk-test").get();

    var expected = Optional.empty();
    var actual = basiliskSub.getMetric("bananas");

    assertEquals(expected, actual);
  }

  @Test
  void testGetMetric() {

    var basiliskSub = Subscription.findById("basilisk-test").get();

    var metric = new Metric();
    metric.setId("redhat.com:BASILISK:cluster_hour");
    metric.setRhmMetricId("redhat.com:BASILISK:cluster_hour");
    metric.setAwsDimension("cluster_hour");

    var prometheusMetric = new PrometheusMetric();
    prometheusMetric.setQueryParams(
        Map.of(
            "product",
            "BASILISK",
            "metric",
            "kafka_id:strimzi_resource_state:max_over_time1h",
            "metadataMetric",
            "subscription_labels"));
    metric.setPrometheus(prometheusMetric);

    var expected = Optional.of(metric);
    var actual = basiliskSub.getMetric("redhat.com:BASILISK:cluster_hour");

    assertEquals(expected, actual);
  }

  @Test
  void testGetMetricIdsUom() {

    var openshiftContainerPlatformSub = Subscription.findById("openshift-container-platform").get();

    var expected = List.of("SOCKETS", "CORES");
    var actual = openshiftContainerPlatformSub.getMetricIds();

    assertThat(actual, Matchers.containsInAnyOrder(expected.toArray()));
  }

  @Test
  void testFindById() {

    var basiliskSub = Subscription.findById("basilisk-test").get();

    var expected = "basilisk-test";
    var actual = basiliskSub.getId();

    assertEquals(expected, actual);
  }

  @ParameterizedTest
  @CsvSource({"basilisk-test,true", "rhel-for-arm,false"})
  void testIsPrometheusEnabled(String input, boolean expected) {

    var subscription = Subscription.findById(input).get();

    assertEquals(subscription.isPrometheusEnabled(), expected);
  }

  @ParameterizedTest
  @CsvSource({"basilisk-test,HOURLY", "rhel-for-arm,DAILY"})
  void testGetFinestGranularity(String input, String expected) {
    var subscription = Subscription.findById(input).get();

    assertEquals(subscription.getFinestGranularity(), expected);
  }

  @Test
  void testGetSupportedGranularityProm() {
    var basiliskSub = Subscription.findById("basilisk-test").get();

    var actual = basiliskSub.getSupportedGranularity();
    var expected = List.of("HOURLY", "DAILY", "WEEKLY", "MONTHLY", "QUARTERLY", "YEARLY");

    assertEquals(expected, actual);
  }

  @Test
  void testGetSupportedGranularityNonProm() {
    var rhelForArmSub = Subscription.findById("rhel-for-arm").get();

    var actual = rhelForArmSub.getSupportedGranularity();
    var expected = List.of("DAILY", "WEEKLY", "MONTHLY", "QUARTERLY", "YEARLY");

    assertEquals(expected, actual);
  }

  @Test
  void testFingerprintEngIdLookup() {
    var satelliteCapsule = Subscription.lookupSubscriptionByEngId("269");

    var expected = "satellite-capsule";
    var actual = satelliteCapsule.get().getId();

    assertEquals(expected, actual);
  }

  @Test
  void testVariantEngIdLookup() {
    var rhelForX86 = Subscription.lookupSubscriptionByEngId("76");

    var expected = "rhel-for-x86";
    var actual = rhelForX86.get().getId();

    assertEquals(expected, actual);
  }

  @Test
  void testVariantProductNameLookup() {
    var openshiftContainerPlatform =
        Subscription.lookupSubscriptionByProductName("OpenShift Container Platform");

    var expected = "OpenShift-metrics";
    var actual = openshiftContainerPlatform.get().getId();

    assertEquals(expected, actual);
  }

  @Test
  void testVariantRoleLookup() {
    var rosa = Subscription.lookupSubscriptionByRole("moa-hostedcontrolplane");

    var expected = "rosa";
    var actual = rosa.get().getId();

    assertEquals(expected, actual);
  }
}
