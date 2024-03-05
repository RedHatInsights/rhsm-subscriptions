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
import static org.mockito.Mockito.when;

import jakarta.ws.rs.BadRequestException;
import java.time.OffsetDateTime;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.db.EventRecordRepository;
import org.candlepin.subscriptions.retention.RemittanceRetentionController;
import org.candlepin.subscriptions.retention.TallyRetentionController;
import org.candlepin.subscriptions.security.SecurityProperties;
import org.candlepin.subscriptions.tally.MarketplaceResendTallyController;
import org.candlepin.subscriptions.tally.events.EventRecordsRetentionProperties;
import org.candlepin.subscriptions.tally.job.CaptureSnapshotsTaskManager;
import org.candlepin.subscriptions.test.TestClockConfiguration;
import org.candlepin.subscriptions.util.DateRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InternalTallyResourceTest {

  private static final String ORG_ID = "org1";

  @Mock private MarketplaceResendTallyController resendTallyController;
  @Mock private CaptureSnapshotsTaskManager snapshotTaskManager;
  @Mock private TallyRetentionController tallyRetentionController;
  @Mock private RemittanceRetentionController remittanceRetentionController;
  @Mock private InternalTallyDataController internalTallyDataController;
  @Mock private SecurityProperties properties;
  @Mock private EventRecordRepository eventRecordRepository;
  @Mock private EventRecordsRetentionProperties eventRecordsRetentionProperties;

  private InternalTallyResource resource;
  private ApplicationProperties appProps;
  private ApplicationClock clock;

  @BeforeEach
  void setupTest() {
    clock = new TestClockConfiguration().adjustableClock();
    appProps = new ApplicationProperties();
    resource =
        new InternalTallyResource(
            clock,
            appProps,
            resendTallyController,
            snapshotTaskManager,
            tallyRetentionController,
            remittanceRetentionController,
            internalTallyDataController,
            properties,
            eventRecordRepository,
            eventRecordsRetentionProperties);
  }

  @Test
  void ensurePerformHourlyTallyForOrgValidatesDateRange() {
    OffsetDateTime start = clock.startOfCurrentHour();
    OffsetDateTime end = start.plusMinutes(5L);
    // asynchronous
    IllegalArgumentException iae1 =
        assertThrows(
            IllegalArgumentException.class,
            () -> resource.performHourlyTallyForOrg(ORG_ID, start, end, false));
    assertEquals(
        "Start/End times must be at the top of the hour: [2019-05-24T12:00:00Z -> 2019-05-24T12:05:00Z]",
        iae1.getMessage());

    resource.performHourlyTallyForOrg(ORG_ID, start, clock.startOfHour(end.plusHours(1)), false);

    // synchronous
    IllegalArgumentException iae2 =
        assertThrows(
            IllegalArgumentException.class,
            () -> resource.performHourlyTallyForOrg(ORG_ID, start, end, true));
    assertEquals(
        "Start/End times must be at the top of the hour: [2019-05-24T12:00:00Z -> 2019-05-24T12:05:00Z]",
        iae2.getMessage());

    resource.performHourlyTallyForOrg(ORG_ID, start, clock.startOfHour(end.plusHours(1)), true);
  }

  @Test
  void allowSynchronousHourlyTallyForOrgWhenSynchronousOperationsEnabled() {
    appProps.setEnableSynchronousOperations(true);
    OffsetDateTime start = clock.startOfCurrentHour();
    OffsetDateTime end = start.plusHours(1L);
    resource.performHourlyTallyForOrg(ORG_ID, start, end, true);
    verify(snapshotTaskManager).tallyOrgByHourly("org1", new DateRange(start, end), true);
  }

  @Test
  void performAsyncHourlyTallyForOrgWhenSynchronousOperationsEnabled() {
    appProps.setEnableSynchronousOperations(true);
    OffsetDateTime start = clock.startOfCurrentHour();
    OffsetDateTime end = start.plusHours(1L);
    resource.performHourlyTallyForOrg("org1", start, end, false);
    verify(snapshotTaskManager).tallyOrgByHourly("org1", new DateRange(start, end), false);
  }

  @Test
  void performAsyncHourlyTallyForOrgWhenSynchronousOperationsDisabled() {
    OffsetDateTime start = clock.startOfCurrentHour();
    OffsetDateTime end = start.plusHours(1L);
    resource.performHourlyTallyForOrg(ORG_ID, start, end, false);
    verify(snapshotTaskManager).tallyOrgByHourly(ORG_ID, new DateRange(start, end), false);
  }

  @Test
  void testTallyOrgWhenAsyncRequest() {
    resource.tallyOrg(ORG_ID, false);
    verify(internalTallyDataController).tallyOrg(ORG_ID);
  }

  @Test
  void testTallyOrgWhenAsyncRequestAsNull() {
    resource.tallyOrg(ORG_ID, null);
    verify(internalTallyDataController).tallyOrg(ORG_ID);
  }

  @Test
  void testTallyOrgWhenSyncRequestAndNotConfigured() {
    appProps.setEnableSynchronousOperations(false);
    assertThrows(BadRequestException.class, () -> resource.tallyOrg(ORG_ID, true));
  }

  @Test
  void testTallyOrgWhenSyncRequestAndConfigured() {
    appProps.setEnableSynchronousOperations(true);
    resource.tallyOrg(ORG_ID, true);
    verify(internalTallyDataController).tallyOrgSync(ORG_ID);
  }

  @Test
  void testPurgeRemittances() {
    resource.purgeRemittances();
    verify(remittanceRetentionController).purgeRemittancesAsync();
  }

  @Test
  void testDeleteDataAssociatedWithOrg() {
    when(properties.isDevMode()).thenReturn(true);
    resource.deleteDataAssociatedWithOrg(ORG_ID);
    verify(internalTallyDataController).deleteDataAssociatedWithOrg(ORG_ID);
  }
}
