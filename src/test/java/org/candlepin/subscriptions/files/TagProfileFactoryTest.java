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

import java.util.Set;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.json.Measurement.Uom;
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
  void whenFactoryProvidedThenYamlPropertiesInjected() {
    assertEquals(2, tagProfile.getTagMappings().size());
    assertEquals("69", tagProfile.getTagMappings().get(0).getValue());
    assertEquals("engId", tagProfile.getTagMappings().get(0).getValueType());
    assertEquals(Set.of("RHEL", "RHEL for x86"), tagProfile.getTagMappings().get(0).getTags());
    assertEquals("x86_64", tagProfile.getTagMappings().get(1).getValue());
    assertEquals("arch", tagProfile.getTagMappings().get(1).getValueType());
    assertEquals(Set.of("RHEL for x86"), tagProfile.getTagMappings().get(1).getTags());

    assertEquals("OpenShift-metrics", tagProfile.getTagMetrics().get(0).getTag());
    assertEquals("Cores", tagProfile.getTagMetrics().get(0).getMetricId());
    assertEquals(Uom.CORES, tagProfile.getTagMetrics().get(0).getUom());
    assertEquals(
        "cluster:usage:workload:capacity_physical_cpu_cores:max:5m",
        tagProfile.getTagMetrics().get(0).getPrometheusMetric());
    assertEquals(
        "subscription_labels", tagProfile.getTagMetrics().get(0).getPrometheusMetadataMetric());

    assertEquals(
        Set.of("OpenShift-metrics", "OpenShift-dedicated-metrics"),
        tagProfile.getTagMetaData().get(0).getTags());
    assertEquals("OpenShift Cluster", tagProfile.getTagMetaData().get(0).getServiceType());
    assertEquals(Granularity.HOURLY, tagProfile.getTagMetaData().get(0).getFinestGranularity());
    assertEquals(ServiceLevel.PREMIUM, tagProfile.getTagMetaData().get(0).getDefaultSla());
    assertEquals(Usage.PRODUCTION, tagProfile.getTagMetaData().get(0).getDefaultUsage());
  }
}
