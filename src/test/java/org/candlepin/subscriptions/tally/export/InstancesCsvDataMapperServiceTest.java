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
package org.candlepin.subscriptions.tally.export;

import static org.candlepin.subscriptions.tally.export.InstancesCsvDataMapperService.METRIC_MAPPER;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.configuration.registry.MetricId;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class InstancesCsvDataMapperServiceTest {
  @ParameterizedTest
  @MethodSource("getMetricsFromConfiguration")
  void testGeneratedModelShouldHaveAllMetricsFromConfiguration(MetricId metricId) {
    assertTrue(METRIC_MAPPER.containsKey(metricId));
  }

  static Set<MetricId> getMetricsFromConfiguration() {
    return MetricId.getAll();
  }
}
