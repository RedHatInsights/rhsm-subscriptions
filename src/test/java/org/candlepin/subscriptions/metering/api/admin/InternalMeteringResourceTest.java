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
package org.candlepin.subscriptions.metering.api.admin;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.BadRequestException;
import java.time.OffsetDateTime;
import java.util.Set;
import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.db.AccountConfigRepository;
import org.candlepin.subscriptions.db.EventRecordRepository;
import org.candlepin.subscriptions.json.Measurement.Uom;
import org.candlepin.subscriptions.metering.ResourceUtil;
import org.candlepin.subscriptions.metering.retention.EventRecordsRetentionProperties;
import org.candlepin.subscriptions.metering.service.prometheus.MetricProperties;
import org.candlepin.subscriptions.metering.service.prometheus.PrometheusMeteringController;
import org.candlepin.subscriptions.metering.service.prometheus.task.PrometheusMetricsTaskManager;
import org.candlepin.subscriptions.registry.TagProfile;
import org.candlepin.subscriptions.test.TestClockConfiguration;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InternalMeteringResourceTest {

  private static final String VALID_PRODUCT = "rhosak";

  @Mock private TagProfile tagProfile;
  @Mock private PrometheusMetricsTaskManager tasks;
  @Mock private PrometheusMeteringController controller;
  @Mock private AccountConfigRepository accountConfigRepository;
  @Mock private EventRecordsRetentionProperties eventRecordsRetentionProperties;
  @Mock private EventRecordRepository eventRecordRepository;

  private ApplicationProperties appProps;
  private ResourceUtil util;
  private ApplicationClock clock;
  private InternalMeteringResource resource;
  private MetricProperties metricProps;

  @BeforeEach
  void setupTest() {
    appProps = new ApplicationProperties();
    clock = new TestClockConfiguration().adjustableClock();
    util = new ResourceUtil(clock);
    lenient().when(tagProfile.tagIsPrometheusEnabled(VALID_PRODUCT)).thenReturn(true);
    lenient()
        .when(tagProfile.getSupportedMetricsForProduct(VALID_PRODUCT))
        .thenReturn(Set.of(Uom.INSTANCE_HOURS));
    lenient().when(accountConfigRepository.findOrgByAccountNumber("account1")).thenReturn("org1");

    metricProps = new MetricProperties();
    metricProps.setRangeInMinutes(60);

    resource =
        new InternalMeteringResource(
            util,
            appProps,
            eventRecordsRetentionProperties,
            tagProfile,
            tasks,
            controller,
            eventRecordRepository,
            metricProps);
  }

  @Test
  void ensureBadRequestIfProductTagIsInvalid() {
    String productId = "test-product";
    when(tagProfile.tagIsPrometheusEnabled(productId)).thenReturn(false);

    OffsetDateTime end = clock.startOfCurrentHour();
    assertThrows(
        BadRequestException.class,
        () -> resource.meterProductForOrgIdAndRange(productId, "org1", end, 120, false));
  }

  @Test
  void testMetersUsingDefaultRange() {
    OffsetDateTime endDate = clock.startOfCurrentHour();
    OffsetDateTime startDate = endDate.minusMinutes(60);
    resource.meterProductForOrgIdAndRange(VALID_PRODUCT, "org1", endDate, null, false);
    verify(tasks).updateMetricsForOrgId("org1", VALID_PRODUCT, startDate, endDate);
    verifyNoInteractions(controller);
  }

  @Test
  void ensureMeterProductValidatesDateRange() {
    OffsetDateTime start = clock.startOfCurrentHour();
    OffsetDateTime end = start.plusMinutes(5L);
    // asynchronous
    IllegalArgumentException iae1 =
        assertThrows(
            IllegalArgumentException.class,
            () -> resource.meterProductForOrgIdAndRange(VALID_PRODUCT, null, end, 120, false));
    assertEquals("Date must start at top of the hour: 2019-05-24T12:05Z", iae1.getMessage());
    resource.meterProductForOrgIdAndRange(
        VALID_PRODUCT, "org1", clock.startOfHour(end), 120, false);

    // synchronous
    IllegalArgumentException iae2 =
        assertThrows(
            IllegalArgumentException.class,
            () -> resource.meterProductForOrgIdAndRange(VALID_PRODUCT, "org1", end, 120, true));
    assertEquals("Date must start at top of the hour: 2019-05-24T12:05Z", iae2.getMessage());

    // Avoid additional exception by enabling synchronous operations.
    appProps.setEnableSynchronousOperations(true);
    resource.meterProductForOrgIdAndRange(VALID_PRODUCT, "org1", clock.startOfHour(end), 120, true);
  }

  @Test
  void preventSynchronousMeteringForAccountWhenSyncRequestsDisabled() {
    OffsetDateTime end = clock.startOfCurrentHour();
    BadRequestException bre =
        assertThrows(
            BadRequestException.class,
            () -> resource.meterProductForOrgIdAndRange(VALID_PRODUCT, "org1", end, 120, true));
    assertEquals("Synchronous metering operations are not enabled.", bre.getMessage());
  }

  @Test
  void allowAsynchronousMeteringForAccountWhenSyncRequestsDisabled() {
    OffsetDateTime endDate = clock.startOfCurrentHour();
    OffsetDateTime startDate = endDate.minusMinutes(120);
    resource.meterProductForOrgIdAndRange(VALID_PRODUCT, "org1", endDate, 120, false);
    verify(tasks).updateMetricsForOrgId("org1", VALID_PRODUCT, startDate, endDate);
    verifyNoInteractions(controller);
  }

  @Test
  void allowSynchronousMeteringForAccountWhenSyncRequestsEnabled() {
    appProps.setEnableSynchronousOperations(true);

    OffsetDateTime endDate = clock.startOfCurrentHour();
    OffsetDateTime startDate = endDate.minusMinutes(120);
    resource.meterProductForOrgIdAndRange(VALID_PRODUCT, "org1", endDate, 120, true);
    verify(controller)
        .collectMetrics(VALID_PRODUCT, Uom.INSTANCE_HOURS, "org1", startDate, endDate);
    verifyNoInteractions(tasks);
  }

  @Test
  void performAsynchronousMeteringForAccountWhenHeaderIsFalseAndSynchronousEnabled() {
    appProps.setEnableSynchronousOperations(true);

    OffsetDateTime endDate = clock.startOfCurrentHour();
    OffsetDateTime startDate = endDate.minusMinutes(120);
    resource.meterProductForOrgIdAndRange(VALID_PRODUCT, "org1", endDate, 120, false);
    verify(tasks).updateMetricsForOrgId("org1", VALID_PRODUCT, startDate, endDate);
    verifyNoInteractions(controller);
  }

  @Test
  void rangeInMinutesMustBeNonNegative() {
    OffsetDateTime endDate = clock.startOfCurrentHour();
    IllegalArgumentException ie =
        assertThrows(
            IllegalArgumentException.class,
            () -> resource.meterProductForOrgIdAndRange(VALID_PRODUCT, "org1", endDate, -1, false));
    assertEquals("Invalid value specified (Must be >= 0): rangeInMinutes", ie.getMessage());
  }

  @Test
  void endDateMustBeAtStartOfHour() {
    OffsetDateTime endDate = clock.now();
    IllegalArgumentException ie =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                resource.meterProductForOrgIdAndRange(VALID_PRODUCT, "org1", endDate, 120, false));
    assertEquals("Date must start at top of the hour: " + endDate, ie.getMessage());
  }

  @Test
  void badRangeThrowsException() {
    OffsetDateTime endDate = clock.startOfCurrentHour();
    IllegalArgumentException ie =
        assertThrows(
            IllegalArgumentException.class,
            () -> resource.meterProductForOrgIdAndRange(VALID_PRODUCT, "org1", endDate, 13, false));
    assertThat(ie.getMessage(), Matchers.matchesRegex(".*produces time not at top of the hour.*"));
  }
}
