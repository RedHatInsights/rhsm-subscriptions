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
package org.candlepin.subscriptions.tally.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.OffsetDateTime;
import javax.ws.rs.BadRequestException;
import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.FixedClockConfiguration;
import org.candlepin.subscriptions.retention.TallyRetentionController;
import org.candlepin.subscriptions.tally.MarketplaceResendTallyController;
import org.candlepin.subscriptions.tally.TallySnapshotController;
import org.candlepin.subscriptions.tally.billing.RemittanceController;
import org.candlepin.subscriptions.tally.job.CaptureSnapshotsTaskManager;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.candlepin.subscriptions.util.DateRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InternalTallyResourceTest {

  @Mock private MarketplaceResendTallyController resendTallyController;
  @Mock private RemittanceController remittanceController;
  @Mock private TallySnapshotController snapshotController;
  @Mock private CaptureSnapshotsTaskManager snapshotTaskManager;
  @Mock private TallyRetentionController tallyRetentionController;

  private InternalTallyResource resource;
  private ApplicationProperties appProps;
  private ApplicationClock clock;

  @BeforeEach
  void setupTest() {
    clock = new FixedClockConfiguration().fixedClock();
    appProps = new ApplicationProperties();
    resource =
        new InternalTallyResource(
            clock,
            appProps,
            resendTallyController,
            remittanceController,
            snapshotController,
            snapshotTaskManager,
            tallyRetentionController);
  }

  @Test
  void ensurePerformHourlyTallyForOrgValidatesDateRange() {
    OffsetDateTime start = clock.startOfCurrentHour();
    OffsetDateTime end = start.plusMinutes(5L);
    // asynchronous
    IllegalArgumentException iae1 =
        assertThrows(
            IllegalArgumentException.class,
            () -> resource.performHourlyTallyForOrg("org1", start, end, false));
    assertEquals(
        "Start/End times must be at the top of the hour: [2019-05-24T12:00:00Z -> 2019-05-24T12:05:00Z]",
        iae1.getMessage());

    resource.performHourlyTallyForOrg("org1", start, clock.startOfHour(end.plusHours(1)), false);

    // synchronous
    IllegalArgumentException iae2 =
        assertThrows(
            IllegalArgumentException.class,
            () -> resource.performHourlyTallyForOrg("org1", start, end, true));
    assertEquals(
        "Start/End times must be at the top of the hour: [2019-05-24T12:00:00Z -> 2019-05-24T12:05:00Z]",
        iae2.getMessage());

    // Avoid additional exception by enabling synchronous operations.
    appProps.setEnableSynchronousOperations(true);
    resource.performHourlyTallyForOrg("org1", start, clock.startOfHour(end.plusHours(1)), true);
  }

  @Test
  void preventSynchronousHourlyTallyForOrgWhenSynchronousOperationsDisabled() {
    OffsetDateTime start = clock.startOfCurrentHour();
    OffsetDateTime end = start.plusHours(1L);
    BadRequestException e =
        assertThrows(
            BadRequestException.class,
            () -> resource.performHourlyTallyForOrg("org1", start, end, true));
    assertEquals("Synchronous tally operations are not enabled.", e.getMessage());
  }

  @Test
  void allowSynchronousHourlyTallyForOrgWhenSynchronousOperationsEnabled() {
    appProps.setEnableSynchronousOperations(true);
    OffsetDateTime start = clock.startOfCurrentHour();
    OffsetDateTime end = start.plusHours(1L);
    resource.performHourlyTallyForOrg("org1", start, end, true);
    verify(snapshotController).produceHourlySnapshotsForOrg("org1", new DateRange(start, end));
    verifyNoInteractions(snapshotTaskManager);
  }

  @Test
  void performAsyncHourlyTallyForOrgWhenSynchronousOperationsEnabled() {
    appProps.setEnableSynchronousOperations(true);
    OffsetDateTime start = clock.startOfCurrentHour();
    OffsetDateTime end = start.plusHours(1L);
    resource.performHourlyTallyForOrg("org1", start, end, false);
    verify(snapshotTaskManager).tallyOrgByHourly("org1", new DateRange(start, end));
    verifyNoInteractions(snapshotController);
  }

  @Test
  void performAsyncHourlyTallyForOrgWhenSynchronousOperationsDisabled() {
    OffsetDateTime start = clock.startOfCurrentHour();
    OffsetDateTime end = start.plusHours(1L);
    resource.performHourlyTallyForOrg("org1", start, end, false);
    verify(snapshotTaskManager).tallyOrgByHourly("org1", new DateRange(start, end));
    verifyNoInteractions(snapshotController);
  }
}
