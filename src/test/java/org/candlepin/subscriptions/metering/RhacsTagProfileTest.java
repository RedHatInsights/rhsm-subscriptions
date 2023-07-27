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
package org.candlepin.subscriptions.metering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.json.Measurement;
import org.candlepin.subscriptions.json.Measurement.Uom;
import org.candlepin.subscriptions.json.TallyMeasurement;
import org.candlepin.subscriptions.registry.BillingWindow;
import org.candlepin.subscriptions.registry.TagMetaData;
import org.candlepin.subscriptions.registry.TagMetric;
import org.candlepin.subscriptions.registry.TagProfile;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"api", "test"})
class RhacsTagProfileTest {

  @Autowired private TagProfile tagProfile;

  @ParameterizedTest
  @MethodSource("tagMetricTestArgs")
  void testTagMetric(String tag, Measurement.Uom uom, TagMetric expectedTagMetric) {
    Optional<TagMetric> tagMetric = tagProfile.getTagMetric(tag, uom);
    assertTrue(tagMetric.isPresent());
    assertEquals(expectedTagMetric, tagMetric.get());
  }

  static Stream<Arguments> tagMetricTestArgs() {
    return Stream.of(
        Arguments.of(
            "rhacs",
            Uom.CORES,
            TagMetric.builder()
                .tag("rhacs")
                .metricId("Cores")
                .rhmMetricId("redhat.com:rhacs:cpu_hour")
                .billingWindow(BillingWindow.MONTHLY)
                .awsDimension("vCPU_Hour")
                .uom(Uom.CORES)
                .queryKey("default")
                .accountQueryKey("default")
                .queryParams(
                    Map.of(
                        "product", "rhacs",
                        "prometheusMetric",
                            "rhacs:rox_central_cluster_metrics_cpu_capacity:avg_over_time1h",
                        "prometheusMetadataMetric", "subscription_labels"))
                .build()));
  }

  @ParameterizedTest
  @MethodSource("tagMetaDataTestArgs")
  void testTagMetaData(String tag, TagMetaData expectedTagMetaData) {
    Optional<TagMetaData> md = tagProfile.getTagMetaDataByTag(tag);
    assertTrue(md.isPresent());
    assertEquals(expectedTagMetaData, md.get());
  }

  static Stream<Arguments> tagMetaDataTestArgs() {
    return Stream.of(
        Arguments.of(
            "rhacs",
            TagMetaData.builder()
                .defaultSla(ServiceLevel.PREMIUM)
                .finestGranularity(Granularity.HOURLY)
                .serviceType("Rhacs Cluster")
                .defaultUsage(Usage.PRODUCTION)
                .tags(Set.of("rhacs"))
                .billingModel("PAYG")
                .build()));
  }

  @ParameterizedTest
  @MethodSource("promethuesEnabledLookupArgs")
  void testPrometueusEnabledMeasurements(String tag, TallyMeasurement.Uom uom, String exMetricId) {
    assertTrue(tagProfile.getTagsWithPrometheusEnabledLookup().contains(tag));
    assertEquals(exMetricId, tagProfile.rhmMetricIdForTagAndUom(tag, uom));
  }

  static Stream<Arguments> promethuesEnabledLookupArgs() {
    return Stream.of(
        Arguments.of("rhacs", TallyMeasurement.Uom.CORES, "redhat.com:rhacs:cpu_hour"));
  }
}
