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

public class UtilizationSummaryValidatorTest {

  private final UtilizationSummaryValidator validator = new UtilizationSummaryValidator();

  @Test
  void testIsValid_withValidPaygPayload_returnsTrue() {
    UtilizationSummary payload = createValidPaygPayload();
    boolean result = whenIsValid(payload);
    assertTrue(result);
  }

  @Test
  void testIsValid_withCompletelyValidPayload_returnsTrue() {
    UtilizationSummary payload = createValidNonPaygPayload();
    boolean result = whenIsValid(payload);
    assertTrue(result);
  }

  @Test
  void testHasValidOrgId_withNullOrgId_returnsFalse() {
    UtilizationSummary payload = createValidNonPaygPayload().withOrgId(null);
    boolean result = whenIsValid(payload);
    assertFalse(result);
  }

  @Test
  void testHasValidProduct_withInvalidProduct_returnsFalse() {
    UtilizationSummary payload = createValidNonPaygPayload().withProductId("invalid-product");
    boolean result = whenIsValid(payload);
    assertFalse(result);
  }

  @Test
  void testHasValidProduct_withNullProduct_returnsFalse() {
    UtilizationSummary payload = createValidNonPaygPayload().withProductId(null);
    boolean result = whenIsValid(payload);
    assertFalse(result);
  }

  @Test
  void testHasValidMeasurements_withNullMeasurements_returnsFalse() {
    UtilizationSummary payload = createValidNonPaygPayload().withMeasurements(null);
    boolean result = whenIsValid(payload);
    assertFalse(result);
  }

  @Test
  void testHasValidMeasurements_withEmptyMeasurements_returnsFalse() {
    UtilizationSummary payload = createValidNonPaygPayload().withMeasurements(List.of());
    boolean result = whenIsValid(payload);
    assertFalse(result);
  }

  @Test
  void testHasValidMeasurements_withInvalidMetricId_returnsFalse() {
    UtilizationSummary payload =
        createValidNonPaygPayload()
            .withMeasurements(List.of(new Measurement().withMetricId("invalid-metric")));
    boolean result = whenIsValid(payload);
    assertFalse(result);
  }

  @Test
  void testHasValidMeasurements_withUnsupportedMetricForProduct_returnsFalse() {
    UtilizationSummary payload =
        createValidNonPaygPayload()
            .withMeasurements(
                List.of(new Measurement().withMetricId(MetricIdUtils.getCores().getValue())));
    boolean result = whenIsValid(payload);
    assertFalse(result);
  }

  @Test
  void testHasValidGranularity_withDailyGranularity_returnsTrue() {
    UtilizationSummary payload =
        createValidNonPaygPayload().withGranularity(UtilizationSummary.Granularity.DAILY);
    boolean result = whenIsValid(payload);
    assertTrue(result);
  }

  @Test
  void testHasValidGranularity_withHourlyGranularity_returnsTrue() {
    UtilizationSummary payload =
        createValidNonPaygPayload().withGranularity(UtilizationSummary.Granularity.HOURLY);
    boolean result = whenIsValid(payload);
    assertTrue(result);
  }

  @Test
  void testHasValidGranularity_withWeeklyGranularity_returnsFalse() {
    UtilizationSummary payload =
        createValidNonPaygPayload().withGranularity(UtilizationSummary.Granularity.WEEKLY);
    boolean result = whenIsValid(payload);
    assertFalse(result);
  }

  @Test
  void testHasValidGranularity_withNullGranularity_returnsFalse() {
    UtilizationSummary payload = createValidNonPaygPayload().withGranularity(null);
    boolean result = whenIsValid(payload);
    assertFalse(result);
  }

  @Test
  void testHasValidMeasurements_withUnlimitedCapacity_returnsFalse() {
    UtilizationSummary payload =
        createValidNonPaygPayload()
            .withMeasurements(
                List.of(
                    new Measurement()
                        .withMetricId(MetricIdUtils.getSockets().getValue())
                        .withUnlimited(true)
                        .withCapacity(100.0)
                        .withCurrentTotal(50.0)));
    boolean result = whenIsValid(payload);
    assertFalse(result);
  }

  @Test
  void testHasValidMeasurements_withNullCapacity_returnsFalse() {
    UtilizationSummary payload =
        createValidNonPaygPayload()
            .withMeasurements(
                List.of(
                    new Measurement()
                        .withMetricId(MetricIdUtils.getSockets().getValue())
                        .withUnlimited(false)
                        .withCapacity(null)
                        .withCurrentTotal(50.0)));
    boolean result = whenIsValid(payload);
    assertFalse(result);
  }

  @Test
  void testHasValidMeasurements_withZeroCapacity_returnsFalse() {
    UtilizationSummary payload =
        createValidNonPaygPayload()
            .withMeasurements(
                List.of(
                    new Measurement()
                        .withMetricId(MetricIdUtils.getSockets().getValue())
                        .withUnlimited(false)
                        .withCapacity(0.0)
                        .withCurrentTotal(50.0)));
    boolean result = whenIsValid(payload);
    assertFalse(result);
  }

  @Test
  void testHasValidMeasurements_withNegativeCapacity_returnsFalse() {
    UtilizationSummary payload =
        createValidNonPaygPayload()
            .withMeasurements(
                List.of(
                    new Measurement()
                        .withMetricId(MetricIdUtils.getSockets().getValue())
                        .withUnlimited(false)
                        .withCapacity(-10.0)
                        .withCurrentTotal(50.0)));
    boolean result = whenIsValid(payload);
    assertFalse(result);
  }

  @Test
  void testHasValidMeasurements_withNullCurrentTotal_returnsFalse() {
    UtilizationSummary payload =
        createValidNonPaygPayload()
            .withMeasurements(
                List.of(
                    new Measurement()
                        .withMetricId(MetricIdUtils.getSockets().getValue())
                        .withUnlimited(false)
                        .withCapacity(100.0)
                        .withCurrentTotal(null)));
    boolean result = whenIsValid(payload);
    assertFalse(result);
  }

  @Test
  void testHasValidMeasurements_withValidCapacityAndCurrentTotal_returnsTrue() {
    UtilizationSummary payload =
        createValidNonPaygPayload()
            .withMeasurements(
                List.of(
                    new Measurement()
                        .withMetricId(MetricIdUtils.getSockets().getValue())
                        .withUnlimited(false)
                        .withCapacity(100.0)
                        .withCurrentTotal(50.0)));
    boolean result = whenIsValid(payload);
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

  private UtilizationSummary createValidPaygPayload() {
    return new UtilizationSummary()
        .withOrgId("org123")
        .withProductId("rosa")
        .withBillingAccountId("billing-123")
        .withGranularity(UtilizationSummary.Granularity.DAILY)
        .withMeasurements(
            List.of(
                new Measurement()
                    .withMetricId(MetricIdUtils.getCores().getValue())
                    .withUnlimited(false)
                    .withCapacity(100.0)
                    .withCurrentTotal(50.0)));
  }

  private boolean whenIsValid(UtilizationSummary payload) {
    return validator.isValid(payload);
  }
}
