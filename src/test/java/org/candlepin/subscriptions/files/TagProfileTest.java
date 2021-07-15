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
package org.candlepin.subscriptions.files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.json.Measurement.Uom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A class to test the tag profile contents. */
class TagProfileTest {

  private TagProfile tagProfile;

  @BeforeEach
  void setup() {
    TagMapping tagMapping1 =
        TagMapping.builder()
            .value("69")
            .valueType("engId")
            .tags(Set.of("RHEL", "RHEL for Desktop"))
            .build();
    TagMapping tagMapping2 =
        TagMapping.builder().value("x86_64").valueType("arch").tags(Set.of("RHEL for x86")).build();

    Map<String, String> params = new HashMap<>();
    params.put("prometheusMetric", "cluster:usage:workload:capacity_physical_cpu_cores:max:5m");
    params.put("prometheusMetadataMetric", "subscription_labels");

    // Mutable List for purpose of modifying during testing.
    List<TagMetric> tagMetrics = new LinkedList<>();
    tagMetrics.add(
        TagMetric.builder()
            .tag("OpenShift-metrics")
            .uom(Uom.CORES)
            .metricId("Cores")
            .queryParams(params)
            .build());
    tagMetrics.add(
        TagMetric.builder()
            .tag("OpenShift-metrics")
            .uom(Uom.INSTANCE_HOURS)
            .metricId("Cores")
            .queryParams(params)
            .build());

    TagMetaData tagMetaData =
        TagMetaData.builder()
            .tags(Set.of("OpenShift-metrics", "OpenShift-dedicated-metrics"))
            .serviceType("OpenShift Cluster")
            .finestGranularity(Granularity.HOURLY)
            .defaultSla(ServiceLevel.PREMIUM)
            .defaultUsage(Usage.PRODUCTION)
            .build();
    tagProfile =
        TagProfile.builder()
            .tagMappings(List.of(tagMapping1, tagMapping2))
            .tagMetrics(tagMetrics)
            .tagMetaData(List.of(tagMetaData))
            .build();

    // Manually invoke @PostConstruct so that the class is properly initialized.
    tagProfile.initLookups();
  }

  @Test
  void spotCheck() {
    assertNotNull(tagProfile);
    assertNotNull(tagProfile.getTagMappings());
    assertNotNull(tagProfile.getTagMetrics());
    assertNotNull(tagProfile.getTagMetaData());
  }

  @Test
  void findMetaDataByTag() {
    assertFalse(tagProfile.getTagMetaDataByTag("OpenShift-metrics").isEmpty());
  }

  @Test
  void testOptionalEmptyIfTagIsEmptyWhenLookingUpMetaData() {
    assertTrue(tagProfile.getTagMetaDataByTag("").isEmpty());
    assertTrue(tagProfile.getTagMetaDataByTag(null).isEmpty());
  }

  @Test
  void testOptionalEmptyIfMetaDataNotFoundForTag() {
    assertTrue(tagProfile.getTagMetaDataByTag("DOES_NOT_EXIST").isEmpty());
  }

  @Test
  void testGetTagMetricByProductTagAndUom() {
    Optional<TagMetric> metric = tagProfile.getTagMetric("OpenShift-metrics", Uom.CORES);
    assertFalse(metric.isEmpty());
    assertEquals("OpenShift-metrics", metric.get().getTag());
    assertEquals(Uom.CORES, metric.get().getUom());
  }

  @Test
  void testGetTagMetricByProductTagAndUomThrowsExceptionWhenDuplicateDefined() {
    tagProfile.getTagMetrics().add(tagProfile.getTagMetric("OpenShift-metrics", Uom.CORES).get());

    assertThrows(
        IllegalStateException.class, () -> tagProfile.getTagMetric("OpenShift-metrics", Uom.CORES));
  }

  @Test
  void testUomsForTag() {
    List<Uom> uoms = tagProfile.uomsForTag("OpenShift-metrics");
    assertEquals(2, uoms.size());
    assertTrue(uoms.containsAll(List.of(Uom.CORES, Uom.INSTANCE_HOURS)));
  }

  @Test
  void emptyListOfUomsWhenTagDoesNotExist() {
    List<Uom> uoms = tagProfile.uomsForTag("NOT_FOUND");
    assertTrue(uoms.isEmpty());
  }

  @Test
  void getSupportedMetricsForProduct() {
    Set<Uom> supportedMetricsForProduct =
        tagProfile.getSupportedMetricsForProduct("OpenShift-metrics");
    assertEquals(2, supportedMetricsForProduct.size());
    assertTrue(supportedMetricsForProduct.containsAll(List.of(Uom.CORES, Uom.INSTANCE_HOURS)));
  }

  @Test
  void getSupportedMetricsForProductThrowsExceptionWhenTagNotFound() {
    assertThrows(
        UnsupportedOperationException.class,
        () -> tagProfile.getSupportedMetricsForProduct("NOT_FOUND"));
  }
}
