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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Set;
import javax.ws.rs.BadRequestException;
import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.FixedClockConfiguration;
import org.candlepin.subscriptions.db.AccountConfigRepository;
import org.candlepin.subscriptions.json.Measurement.Uom;
import org.candlepin.subscriptions.metering.ResourceUtil;
import org.candlepin.subscriptions.metering.service.prometheus.PrometheusMeteringController;
import org.candlepin.subscriptions.metering.service.prometheus.task.PrometheusMetricsTaskManager;
import org.candlepin.subscriptions.registry.TagProfile;
import org.candlepin.subscriptions.util.ApplicationClock;
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

  private ApplicationProperties appProps;
  private ResourceUtil util;
  private ApplicationClock clock;
  private InternalMeteringResource resource;

  @BeforeEach
  void setupTest() {
    appProps = new ApplicationProperties();
    clock = new FixedClockConfiguration().fixedClock();
    util = new ResourceUtil(clock);
    lenient().when(tagProfile.tagIsPrometheusEnabled(VALID_PRODUCT)).thenReturn(true);
    lenient()
        .when(tagProfile.getSupportedMetricsForProduct(VALID_PRODUCT))
        .thenReturn(Set.of(Uom.INSTANCE_HOURS));
    lenient().when(accountConfigRepository.findOrgByAccountNumber("account1")).thenReturn("org1");
    resource =
        new InternalMeteringResource(
            util, appProps, tagProfile, tasks, controller, accountConfigRepository);
  }

  @Test
  void ensureBadRequestIfProductTagIsInvalid() {
    String productId = "test-product";
    when(tagProfile.tagIsPrometheusEnabled(productId)).thenReturn(false);

    OffsetDateTime end = clock.startOfCurrentHour();
    assertThrows(
        BadRequestException.class,
        () -> resource.meterProductForAccount(productId, 120, null, "org1", end, false));
  }

  @Test
  void ensureMeterProductValidatesDateRange() {
    OffsetDateTime start = clock.startOfCurrentHour();
    OffsetDateTime end = start.plusMinutes(5L);
    // asynchronous
    IllegalArgumentException iae1 =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                resource.meterProductForAccount(VALID_PRODUCT, 120, "account1", null, end, false));
    assertEquals("Date must start at top of the hour: 2019-05-24T12:05Z", iae1.getMessage());
    resource.meterProductForAccount(
        VALID_PRODUCT, 120, null, "org1", clock.startOfHour(end), false);

    // synchronous
    IllegalArgumentException iae2 =
        assertThrows(
            IllegalArgumentException.class,
            () -> resource.meterProductForAccount(VALID_PRODUCT, 120, null, "org1", end, true));
    assertEquals("Date must start at top of the hour: 2019-05-24T12:05Z", iae2.getMessage());

    // Avoid additional exception by enabling synchronous operations.
    appProps.setEnableSynchronousOperations(true);
    resource.meterProductForAccount(VALID_PRODUCT, 120, null, "org1", clock.startOfHour(end), true);
  }

  @Test
  void preventSynchronousMeteringForAccountWhenSyncRequestsDisabled() {
    OffsetDateTime end = clock.startOfCurrentHour();
    BadRequestException bre =
        assertThrows(
            BadRequestException.class,
            () -> resource.meterProductForAccount(VALID_PRODUCT, 120, "account1", null, end, true));
    assertEquals("Synchronous metering operations are not enabled.", bre.getMessage());
  }

  @Test
  void allowAsynchronousMeteringForAccountWhenSyncRequestsDisabled() {
    OffsetDateTime endDate = clock.startOfCurrentHour();
    OffsetDateTime startDate = endDate.minusMinutes(120);
    resource.meterProductForAccount(VALID_PRODUCT, 120, "account1", null, endDate, false);
    verify(tasks).updateMetricsForOrgId("org1", VALID_PRODUCT, startDate, endDate);
    verifyNoInteractions(controller);
  }

  @Test
  void allowSynchronousMeteringForAccountWhenSyncRequestsEnabled() {
    appProps.setEnableSynchronousOperations(true);

    OffsetDateTime endDate = clock.startOfCurrentHour();
    OffsetDateTime startDate = endDate.minusMinutes(120);
    resource.meterProductForAccount(VALID_PRODUCT, 120, "account1", null, endDate, true);
    verify(controller)
        .collectMetrics(VALID_PRODUCT, Uom.INSTANCE_HOURS, "org1", startDate, endDate);
    verifyNoInteractions(tasks);
  }

  @Test
  void performAsynchronousMeteringForAccountWhenHeaderIsFalseAndSynchronousEnabled() {
    appProps.setEnableSynchronousOperations(true);

    OffsetDateTime endDate = clock.startOfCurrentHour();
    OffsetDateTime startDate = endDate.minusMinutes(120);
    resource.meterProductForAccount(VALID_PRODUCT, 120, null, "org1", endDate, false);
    verify(tasks).updateMetricsForOrgId("org1", VALID_PRODUCT, startDate, endDate);
    verifyNoInteractions(controller);
  }

  @Test
  void meterProductForAccountLooksUpOrgIdWhenNotProvided() {
    OffsetDateTime endDate = clock.startOfCurrentHour();
    OffsetDateTime startDate = endDate.minusMinutes(120);
    resource.meterProductForAccount(VALID_PRODUCT, 120, "account1", null, endDate, false);
    verify(tasks).updateMetricsForOrgId("org1", VALID_PRODUCT, startDate, endDate);
    verifyNoInteractions(controller);
  }

  @Test
  void meterProductForAccountThrowExceptionWhenOrgIdCannotBeFound() {
    OffsetDateTime endDate = clock.startOfCurrentHour();
    BadRequestException bre =
        assertThrows(
            BadRequestException.class,
            () ->
                resource.meterProductForAccount(
                    VALID_PRODUCT, 120, "account2", null, endDate, false));
    assertEquals("No orgId found/specified for accountNumber: account2", bre.getMessage());
  }
}
