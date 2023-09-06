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

import com.redhat.swatch.configuration.registry.MetricId;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.registry.BillingWindow;
import org.candlepin.subscriptions.registry.TagMetaData;
import org.candlepin.subscriptions.registry.TagMetric;
import org.candlepin.subscriptions.registry.TagProfile;
import org.candlepin.subscriptions.util.MetricIdUtils;
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
  private static String STORAGE_GIBIBYTES = "Storage-gibibytes";
  private static String TRANSFER_GIBIBYTES = "Transfer-gibibytes";
  private static String STORAGE_GIBIBYTE_MONTHS = "Storage-gibibyte-months";

  @ParameterizedTest
  @MethodSource("tagMetricTestArgs")
  void testTagMetric(String tag, MetricId uom, TagMetric expectedTagMetric) {
    Optional<TagMetric> tagMetric = tagProfile.getTagMetric(tag, uom);
    assertTrue(tagMetric.isPresent());
    assertEquals(expectedTagMetric, tagMetric.get());
  }

  static Stream<Arguments> tagMetricTestArgs() {
    return Stream.of(
        Arguments.of(
            "rhosak",
            MetricId.fromString(STORAGE_GIBIBYTES),
            TagMetric.builder()
                .tag("rhosak")
                .metricId(STORAGE_GIBIBYTES)
                .uom("STORAGE_GIBIBYTES")
                .rhmMetricId("redhat.com:rhosak:storage_gb")
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
            MetricId.fromString(TRANSFER_GIBIBYTES),
            TagMetric.builder()
                .tag("rhosak")
                .metricId(TRANSFER_GIBIBYTES)
                .uom("TRANSFER_GIBIBYTES")
                .rhmMetricId("redhat.com:rhosak:transfer_gb")
                .awsDimension("transfer_gb")
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
            MetricIdUtils.getInstanceHours(),
            TagMetric.builder()
                .tag("rhosak")
                .metricId(MetricIdUtils.getInstanceHours().toString())
                .uom("INSTANCE_HOURS")
                .rhmMetricId("redhat.com:rhosak:cluster_hour")
                .awsDimension("cluster_hour")
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
            MetricId.fromString(STORAGE_GIBIBYTE_MONTHS),
            TagMetric.builder()
                .tag("rhosak")
                .metricId(STORAGE_GIBIBYTE_MONTHS)
                .uom("STORAGE_GIBIBYTE_MONTHS")
                .awsDimension("storage_gb")
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
}
