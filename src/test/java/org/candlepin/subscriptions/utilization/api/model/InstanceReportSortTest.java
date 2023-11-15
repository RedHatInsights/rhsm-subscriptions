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
package org.candlepin.subscriptions.utilization.api.model;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import com.redhat.swatch.configuration.registry.MetricId;
import org.junit.jupiter.api.Test;

class InstanceReportSortTest {
  @Test
  void testAllMetricIdIsSetInInstanceReportSort() {
    for (MetricId metricId : MetricId.getAll()) {
      try {
        assertNotNull(InstanceReportSort.fromValue(metricId.getValue()));
      } catch (IllegalArgumentException ex) {
        fail(
            String.format(
                "Metric ID '%s' is not added into the InstanceReportSort enum. "
                    + "Please, add it to the "
                    + "'api/rhsm-subscriptions-api-spec.yaml#InstanceReportSort' enum type.",
                metricId.getValue()));
      }
    }
  }
}
