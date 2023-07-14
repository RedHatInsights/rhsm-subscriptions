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
package org.candlepin.subscriptions.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.json.Measurement;
import org.candlepin.subscriptions.json.TallyMeasurement.Uom;
import org.candlepin.subscriptions.utilization.api.model.ProductId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** A class to test the tag profile factory, parsing the tag profile. */
@SpringBootTest
@ActiveProfiles({"api", "test"})
class TagProfileFactoryTest {

  @Autowired private TagProfile tagProfile;

  @Test
  void testTagProfileLooksConsistent() {
    assertFalse(tagProfile.getTagMappings().isEmpty());
    assertFalse(tagProfile.getTagMetrics().isEmpty());
    assertFalse(tagProfile.getTagMetaData().isEmpty());
    tagProfile
        .getTagMetaData()
        .forEach(
            metadata -> {
              assertNotNull(metadata.getFinestGranularity());
              assertFalse(metadata.getTags().isEmpty());
            });
    Set<String> metadataTags =
        tagProfile.getTagMetaData().stream()
            .map(TagMetaData::getTags)
            .flatMap(Set::stream)
            .collect(Collectors.toSet());
    tagProfile
        .getTagMappings()
        .forEach(mapping -> assertTrue(metadataTags.containsAll(mapping.getTags())));
    tagProfile
        .getTagMetrics()
        .forEach(metric -> assertTrue(metadataTags.contains(metric.getTag())));
  }

  @Test
  void testCanLookupMetricIdByTagAndUom() {
    assertNotNull(
        tagProfile.rhmMetricIdForTagAndUom(ProductId.OPENSHIFT_METRICS.toString(), Uom.CORES));
  }

  @Test
  void testCanLookupAwsDimensionByTagAndUom() {
    String rosaCoresAwsDimension =
        tagProfile.awsDimensionForTagAndUom(ProductId.ROSA.toString(), Uom.CORES);
    assertNotNull(rosaCoresAwsDimension);
    assertEquals("four_vcpu_0", rosaCoresAwsDimension);
  }

  @Test
  void awsDimensionLookupReturnsNullWhenNotDefined() {
    assertNull(
        tagProfile.awsDimensionForTagAndUom(ProductId.OPENSHIFT_METRICS.toString(), Uom.CORES));
  }

  @Test
  void testCanLookupPrometheusEnabled() {
    assertTrue(tagProfile.tagIsPrometheusEnabled(ProductId.OPENSHIFT_METRICS.toString()));
  }

  @Test
  void testCanLookupEngIdSupport() {
    assertTrue(
        tagProfile.tagSupportsEngProduct(ProductId.OPENSHIFT_CONTAINER_PLATFORM.toString(), "290"));
  }

  @Test
  void testCanLookupTagByOfferingName() {
    assertNotNull(tagProfile.tagForOfferingProductName("OpenShift Container Platform"));
  }

  @Test
  void testCanLookupSupportedGranularity() {
    assertTrue(
        tagProfile.tagSupportsGranularity(
            ProductId.OPENSHIFT_METRICS.toString(), Granularity.HOURLY));
  }

  @Test
  void canLookupMetaDataByTag() {
    Optional<TagMetaData> ocpMeta = tagProfile.getTagMetaDataByTag("OpenShift-metrics");
    assertFalse(ocpMeta.isEmpty());
    assertTrue(ocpMeta.get().getTags().contains("OpenShift-metrics"));
  }

  @Test
  void lookupMetaDataByTagWhenNotFound() {
    assertTrue(tagProfile.getTagMetaDataByTag("UNKNOWN").isEmpty());
  }

  @Test
  void billingFrequencyIsSet() {
    Optional<TagMetric> metric =
        tagProfile.getTagMetric(ProductId.RHOSAK.toString(), Measurement.Uom.STORAGE_GIBIBYTES);
    assertEquals(BillingWindow.HOURLY, metric.get().getBillingWindow());
  }
}
