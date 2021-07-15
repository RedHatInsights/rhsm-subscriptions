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
package org.candlepin.subscriptions.metering.jmx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;
import org.candlepin.subscriptions.FixedClockConfiguration;
import org.candlepin.subscriptions.files.TagProfile;
import org.candlepin.subscriptions.metering.service.prometheus.MetricProperties;
import org.candlepin.subscriptions.metering.service.prometheus.task.PrometheusMetricsTaskManager;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MeteringJmxBeanTest {

  private static final String PRODUCT_PROFILE_ID = "OpenShift-metrics";

  @Mock private PrometheusMetricsTaskManager tasks;
  @Mock private TagProfile tagProfile;

  private ApplicationClock clock;
  private MetricProperties metricProps;
  private MeteringJmxBean jmx;

  @BeforeEach
  void setupTests() {
    metricProps = new MetricProperties();
    metricProps.setRangeInMinutes(60);

    clock = new FixedClockConfiguration().fixedClock();
    jmx = new MeteringJmxBean(clock, tasks, metricProps);
  }

  @Test
  void testMeteringForAccount() {
    String expectedAccount = "test-account";
    OffsetDateTime endDate = clock.startOfCurrentHour();
    OffsetDateTime startDate = endDate.minusMinutes(metricProps.getRangeInMinutes());

    jmx.performMeteringForAccount(expectedAccount, PRODUCT_PROFILE_ID);

    verify(tasks).updateMetricsForAccount(expectedAccount, PRODUCT_PROFILE_ID, startDate, endDate);
  }

  @Test
  void testCustomMeteringForAccount() {
    String expectedAccount = "test-account";
    int rangeInMins = 20;
    OffsetDateTime endDate = clock.startOfCurrentHour();
    OffsetDateTime startDate = endDate.minusMinutes(rangeInMins);
    jmx.performCustomMeteringForAccount(
        expectedAccount, PRODUCT_PROFILE_ID, clock.startOfCurrentHour().toString(), rangeInMins);

    verify(tasks).updateMetricsForAccount(expectedAccount, PRODUCT_PROFILE_ID, startDate, endDate);
  }

  @Test
  void testGetStartDateValidatesRequiredRangeInMinutes() {
    String account = "1234";
    String endDate = clock.startOfCurrentHour().toString();
    Throwable e =
        assertThrows(
            IllegalArgumentException.class,
            () -> jmx.performCustomMeteringForAccount(account, PRODUCT_PROFILE_ID, endDate, null));
    assertEquals("Required argument: rangeInMinutes", e.getMessage());
  }

  @Test
  void testGetStartDateValidatesRangeInMinutesGreaterEqualToZero() {
    String account = "1234";
    String endDate = clock.startOfCurrentHour().toString();
    Throwable e =
        assertThrows(
            IllegalArgumentException.class,
            () -> jmx.performCustomMeteringForAccount(account, PRODUCT_PROFILE_ID, endDate, -1));
    assertEquals("Invalid value specified (Must be >= 0): rangeInMinutes", e.getMessage());
  }

  @Test
  void testPerformMeteringForAllAccounts() {
    OffsetDateTime endDate = clock.startOfCurrentHour();
    OffsetDateTime startDate = endDate.minusMinutes(metricProps.getRangeInMinutes());

    jmx.performMetering(PRODUCT_PROFILE_ID);

    verify(tasks).updateMetricsForAllAccounts(PRODUCT_PROFILE_ID, startDate, endDate);
  }

  @Test
  void testPerformCustomMeteringForAllAccounts() {
    int rangeInMins = 20;
    OffsetDateTime endDate = clock.startOfCurrentHour();
    OffsetDateTime startDate = endDate.minusMinutes(rangeInMins);
    jmx.performCustomMetering(
        PRODUCT_PROFILE_ID, clock.startOfCurrentHour().toString(), rangeInMins);

    verify(tasks).updateMetricsForAllAccounts(PRODUCT_PROFILE_ID, startDate, endDate);
  }

  @Test
  void testEndDateMustBeAtStartOfHour() {
    String account = "1234";
    String endDate = clock.now().toString();
    Throwable e =
        assertThrows(
            IllegalArgumentException.class,
            () -> jmx.performCustomMeteringForAccount(account, PRODUCT_PROFILE_ID, endDate, 24));
    assertEquals("Date must start at top of the hour: " + endDate, e.getMessage());
  }
}
