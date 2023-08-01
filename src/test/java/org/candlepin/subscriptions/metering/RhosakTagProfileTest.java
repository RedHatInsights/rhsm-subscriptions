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

import static org.junit.jupiter.api.Assertions.*;

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
class RhosakTagProfileTest {

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
            "rhosak",
            Uom.STORAGE_GIBIBYTES,
            TagMetric.builder()
                .tag("rhosak")
                .metricId("Storage-gibibytes")
                .rhmMetricId("redhat.com:rhosak:storage_gb")
                .uom(Uom.STORAGE_GIBIBYTES)
                .billingWindow(BillingWindow.HOURLY)
                .queryKey("default")
                .accountQueryKey("default")
                .queryParams(
                    Map.of(
                        "product", "rhosak",
                        "prometheusMetric",
                            "kafka_id:kafka_broker_quota_totalstorageusedbytes:max_over_time1h_gibibytes",
                        "prometheusMetadataMetric", "ocm_subscription"))
                .build()),
        Arguments.of(
            "rhosak",
            Uom.TRANSFER_GIBIBYTES,
            TagMetric.builder()
                .tag("rhosak")
                .metricId("Transfer-gibibytes")
                .rhmMetricId("redhat.com:rhosak:transfer_gb")
                .awsDimension("transfer_gb")
                .uom(Uom.TRANSFER_GIBIBYTES)
                .billingWindow(BillingWindow.MONTHLY)
                .queryKey("default")
                .accountQueryKey("default")
                .queryParams(
                    Map.of(
                        "product",
                        "rhosak",
                        "prometheusMetric",
                        "kafka_id:haproxy_server_bytes_in_out_total:rate1h_gibibytes",
                        "prometheusMetadataMetric",
                        "ocm_subscription"))
                .build()),
        Arguments.of(
            "rhosak",
            Uom.INSTANCE_HOURS,
            TagMetric.builder()
                .tag("rhosak")
                .metricId("Instance-hours")
                .rhmMetricId("redhat.com:rhosak:cluster_hour")
                .awsDimension("cluster_hour")
                .uom(Uom.INSTANCE_HOURS)
                .queryKey("default")
                .billingWindow(BillingWindow.MONTHLY)
                .accountQueryKey("default")
                .queryParams(
                    Map.of(
                        "product", "rhosak",
                        "prometheusMetric", "kafka_id:strimzi_resource_state:max_over_time1h",
                        "prometheusMetadataMetric", "ocm_subscription"))
                .build()),
        Arguments.of(
            "rhosak",
            Uom.STORAGE_GIBIBYTE_MONTHS,
            TagMetric.builder()
                .tag("rhosak")
                .metricId("Storage-gibibyte-months")
                .awsDimension("storage_gb")
                .uom(Uom.STORAGE_GIBIBYTE_MONTHS)
                .billingWindow(BillingWindow.MONTHLY)
                .queryKey("default")
                .accountQueryKey("default")
                .queryParams(
                    Map.of(
                        "product",
                        "rhosak",
                        "prometheusMetric",
                        "kafka_id:kafka_broker_quota_totalstorageusedbytes:max_over_time1h_gibibyte_months",
                        "prometheusMetadataMetric",
                        "ocm_subscription"))
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
            "rhosak",
            TagMetaData.builder()
                .defaultSla(ServiceLevel.PREMIUM)
                .finestGranularity(Granularity.HOURLY)
                .serviceType("Kafka Cluster")
                .defaultUsage(Usage.PRODUCTION)
                .tags(Set.of("rhosak"))
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
        Arguments.of(
            "rhosak", TallyMeasurement.Uom.STORAGE_GIBIBYTES, "redhat.com:rhosak:storage_gb"),
        Arguments.of(
            "rhosak", TallyMeasurement.Uom.TRANSFER_GIBIBYTES, "redhat.com:rhosak:transfer_gb"),
        Arguments.of(
            "rhosak", TallyMeasurement.Uom.INSTANCE_HOURS, "redhat.com:rhosak:cluster_hour"));
  }
}
