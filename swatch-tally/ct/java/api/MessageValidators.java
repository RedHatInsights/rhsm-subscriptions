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
package api;

import com.redhat.swatch.component.tests.api.DefaultMessageValidator;
import com.redhat.swatch.tally.test.model.TallySnapshot.Granularity;
import com.redhat.swatch.tally.test.model.TallySummary;

public class MessageValidators {

  public static DefaultMessageValidator<TallySummary> tallySummaryMatches(
      String orgId, String productId, String metricId, Granularity granularity) {
    return new DefaultMessageValidator<>(
        summary ->
            orgId.equals(summary.getOrgId())
                && summary.getTallySnapshots() != null
                && summary.getTallySnapshots().stream()
                    .anyMatch(
                        snapshot ->
                            productId.equalsIgnoreCase(snapshot.getProductId())
                                && granularity.equals(snapshot.getGranularity())
                                && snapshot.getTallyMeasurements() != null
                                && snapshot.getTallyMeasurements().stream()
                                    .anyMatch(
                                        measurement ->
                                            measurement.getMetricId() != null
                                                && metricId.equalsIgnoreCase(
                                                    measurement.getMetricId()))),
        TallySummary.class);
  }
}
