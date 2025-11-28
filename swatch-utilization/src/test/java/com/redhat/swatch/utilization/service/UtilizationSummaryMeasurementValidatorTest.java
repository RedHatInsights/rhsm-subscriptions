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
package com.redhat.swatch.utilization.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.configuration.util.MetricIdUtils;
import com.redhat.swatch.utilization.model.Measurement;
import com.redhat.swatch.utilization.model.UtilizationSummary;
import java.util.List;
import org.junit.jupiter.api.Test;

public class UtilizationSummaryMeasurementValidatorTest {

  private final UtilizationSummaryMeasurementValidator validator =
      new UtilizationSummaryMeasurementValidator();

  @Test
  void testHasValidMeasurements_withInvalidMetricId_returnsFalse() {
    var measurement = new Measurement().withMetricId("invalid-metric");
    var payload = createValidNonPaygPayload();
    boolean result = whenIsMeasurementValid(payload, measurement);
    assertFalse(result);
  }

  @Test
  void testHasValidMeasurements_withUnsupportedMetricForProduct_returnsFalse() {
    var measurement = new Measurement().withMetricId(MetricIdUtils.getCores().getValue());
    UtilizationSummary payload = createValidNonPaygPayload();
    boolean result = whenIsMeasurementValid(payload, measurement);
    assertFalse(result);
  }

  @Test
  void testHasValidMeasurements_withUnlimitedCapacity_returnsFalse() {
    var measurement =
        new Measurement()
            .withMetricId(MetricIdUtils.getSockets().getValue())
            .withUnlimited(true)
            .withCapacity(100.0)
            .withCurrentTotal(50.0);
    var payload = createValidNonPaygPayload();
    boolean result = whenIsMeasurementValid(payload, measurement);
    assertFalse(result);
  }

  @Test
  void testHasValidMeasurements_withNullCapacity_returnsFalse() {
    var measurement =
        new Measurement()
            .withMetricId(MetricIdUtils.getSockets().getValue())
            .withUnlimited(false)
            .withCapacity(null)
            .withCurrentTotal(50.0);
    var payload = createValidNonPaygPayload();
    boolean result = whenIsMeasurementValid(payload, measurement);
    assertFalse(result);
  }

  @Test
  void testHasValidMeasurements_withZeroCapacity_returnsFalse() {
    var measurement =
        new Measurement()
            .withMetricId(MetricIdUtils.getSockets().getValue())
            .withUnlimited(false)
            .withCapacity(0.0)
            .withCurrentTotal(50.0);
    var payload = createValidNonPaygPayload();
    boolean result = whenIsMeasurementValid(payload, measurement);
    assertFalse(result);
  }

  @Test
  void testHasValidMeasurements_withNegativeCapacity_returnsFalse() {
    var measurement =
        new Measurement()
            .withMetricId(MetricIdUtils.getSockets().getValue())
            .withUnlimited(false)
            .withCapacity(-10.0)
            .withCurrentTotal(50.0);
    var payload = createValidNonPaygPayload();
    boolean result = whenIsMeasurementValid(payload, measurement);
    assertFalse(result);
  }

  @Test
  void testHasValidMeasurements_withNullCurrentTotal_returnsFalse() {
    var measurement =
        new Measurement()
            .withMetricId(MetricIdUtils.getSockets().getValue())
            .withUnlimited(false)
            .withCapacity(100.0)
            .withCurrentTotal(null);
    var payload = createValidNonPaygPayload();
    boolean result = whenIsMeasurementValid(payload, measurement);
    assertFalse(result);
  }

  @Test
  void testHasValidMeasurements_withValidCapacityAndCurrentTotal_returnsTrue() {
    var measurement =
        new Measurement()
            .withMetricId(MetricIdUtils.getSockets().getValue())
            .withUnlimited(false)
            .withCapacity(100.0)
            .withCurrentTotal(50.0);
    var payload = createValidNonPaygPayload();
    boolean result = whenIsMeasurementValid(payload, measurement);
    assertTrue(result);
  }

  private UtilizationSummary createValidNonPaygPayload() {
    return new UtilizationSummary()
        .withOrgId("org123")
        .withProductId("RHEL for x86")
        .withGranularity(UtilizationSummary.Granularity.DAILY)
        .withMeasurements(
            List.of(
                new Measurement()
                    .withMetricId(MetricIdUtils.getSockets().getValue())
                    .withUnlimited(false)
                    .withCapacity(100.0)
                    .withCurrentTotal(50.0)));
  }

  private boolean whenIsMeasurementValid(UtilizationSummary payload, Measurement measurement) {
    return validator.isMeasurementValid(payload, measurement);
  }
}
