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
import org.candlepin.subscriptions.metering.service.prometheus.PrometheusMetricsProperties;
import org.candlepin.subscriptions.metering.service.prometheus.task.PrometheusMetricsTaskManager;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpenShiftJmxBeanTest {

  @Mock private PrometheusMetricsTaskManager tasks;

  private ApplicationClock clock;
  private PrometheusMetricsProperties metricProps;
  private OpenShiftJmxBean jmx;

  @BeforeEach
  void setupTests() {
    metricProps = new PrometheusMetricsProperties();
    metricProps.getOpenshift().setRangeInMinutes(60);

    clock = new FixedClockConfiguration().fixedClock();
    jmx = new OpenShiftJmxBean(clock, tasks, metricProps);
  }

  @Test
  void testMeteringForAccount() {
    String expectedAccount = "test-account";
    OffsetDateTime endDate = clock.startOfCurrentHour();
    OffsetDateTime startDate = endDate.minusMinutes(metricProps.getOpenshift().getRangeInMinutes());
    jmx.performOpenshiftMeteringForAccount(expectedAccount);

    verify(tasks).updateOpenshiftMetricsForAccount(expectedAccount, startDate, endDate);
  }

  @Test
  void testCustomMeteringForAccount() {
    String expectedAccount = "test-account";
    int rangeInMins = 20;
    OffsetDateTime endDate = clock.startOfCurrentHour();
    OffsetDateTime startDate = endDate.minusMinutes(rangeInMins);
    jmx.performCustomOpenshiftMeteringForAccount(
        expectedAccount, clock.startOfCurrentHour().toString(), rangeInMins);

    verify(tasks).updateOpenshiftMetricsForAccount(expectedAccount, startDate, endDate);
  }

  @Test
  void testGetStartDateValidatesRequiredRangeInMinutes() {
    String account = "1234";
    String endDate = clock.startOfCurrentHour().toString();
    Throwable e =
        assertThrows(
            IllegalArgumentException.class,
            () -> jmx.performCustomOpenshiftMeteringForAccount(account, endDate, null));
    assertEquals("Required argument: rangeInMinutes", e.getMessage());
  }

  @Test
  void testGetStartDateValidatesRangeInMinutesGreaterEqualToZero() {
    String account = "1234";
    String endDate = clock.startOfCurrentHour().toString();
    Throwable e =
        assertThrows(
            IllegalArgumentException.class,
            () -> jmx.performCustomOpenshiftMeteringForAccount(account, endDate, -1));
    assertEquals("Invalid value specified (Must be >= 0): rangeInMinutes", e.getMessage());
  }

  @Test
  void testPerformMeteringForAllAccounts() {
    OffsetDateTime endDate = clock.startOfCurrentHour();
    OffsetDateTime startDate = endDate.minusMinutes(metricProps.getOpenshift().getRangeInMinutes());
    jmx.performOpenshiftMetering();

    verify(tasks).updateOpenshiftMetricsForAllAccounts(startDate, endDate);
  }

  @Test
  void testPerformCustomMeteringForAllAccounts() {
    int rangeInMins = 20;
    OffsetDateTime endDate = clock.startOfCurrentHour();
    OffsetDateTime startDate = endDate.minusMinutes(rangeInMins);
    jmx.performCustomOpenshiftMetering(clock.startOfCurrentHour().toString(), rangeInMins);

    verify(tasks).updateOpenshiftMetricsForAllAccounts(startDate, endDate);
  }

  @Test
  void testEndDateMustBeAtStartOfHour() {
    String account = "1234";
    String endDate = clock.now().toString();
    Throwable e =
        assertThrows(
            IllegalArgumentException.class,
            () -> jmx.performCustomOpenshiftMeteringForAccount(account, endDate, 24));
    assertEquals("Date must start at top of the hour: " + endDate, e.getMessage());
  }
}
