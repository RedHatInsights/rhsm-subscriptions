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

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.Set;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
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
    TagMetric tagMetric1 =
        TagMetric.builder()
            .tag("OpenShift-metrics")
            .metricId("Cores")
            .prometheusMetric("cluster:usage:workload:capacity_physical_cpu_cores:max:5m")
            .prometheusMetadataMetric("subscription_labels")
            .build();
    TagMetaData tagMetaData =
        TagMetaData.builder()
            .tags(Set.of("Openshift-metrics", "Openshift-dedicated-metrics"))
            .serviceType("Openshift Cluster")
            .finestGranularity(Granularity.HOURLY)
            .defaultSla(ServiceLevel.PREMIUM)
            .defaultUsage(Usage.PRODUCTION)
            .build();
    tagProfile =
        TagProfile.builder()
            .tagMappings(List.of(tagMapping1, tagMapping2))
            .tagMetrics(List.of(tagMetric1))
            .tagMetaData(List.of(tagMetaData))
            .build();
  }

  @Test
  void spotCheck() {
    assertNotNull(tagProfile);
    assertNotNull(tagProfile.getTagMappings());
    assertNotNull(tagProfile.getTagMetrics());
    assertNotNull(tagProfile.getTagMetaData());
  }
}
