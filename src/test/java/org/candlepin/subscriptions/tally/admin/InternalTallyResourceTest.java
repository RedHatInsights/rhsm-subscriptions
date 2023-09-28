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
import org.candlepin.subscriptions.tally.TallySnapshotController;
import org.candlepin.subscriptions.tally.events.EventRecordsRetentionProperties;
import org.candlepin.subscriptions.tally.job.CaptureSnapshotsTaskManager;
import org.candlepin.subscriptions.test.TestClockConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InternalTallyResourceTest {

  @Mock private MarketplaceResendTallyController resendTallyController;
  @Mock private TallySnapshotController snapshotController;
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
            snapshotController,
            snapshotTaskManager,
            tallyRetentionController,
            remittanceRetentionController,
            internalTallyDataController,
            properties,
            eventRecordRepository,
            eventRecordsRetentionProperties);
  }

  @Test
  void preventSynchronousHourlyTallyForOrgWhenSynchronousOperationsDisabled() {
    BadRequestException e =
        assertThrows(
            BadRequestException.class, () -> resource.performHourlyTallyForOrg("org1", true));
    assertEquals("Synchronous tally operations are not enabled.", e.getMessage());
  }

  @Test
  void allowSynchronousHourlyTallyForOrgWhenSynchronousOperationsEnabled() {
    appProps.setEnableSynchronousOperations(true);
    resource.performHourlyTallyForOrg("org1", true);
    verify(snapshotController).produceHourlySnapshotsForOrg("org1");
    verifyNoInteractions(snapshotTaskManager);
  }

  @Test
  void performAsyncHourlyTallyForOrgWhenSynchronousOperationsEnabled() {
    appProps.setEnableSynchronousOperations(true);
    resource.performHourlyTallyForOrg("org1", false);
    verify(snapshotTaskManager).tallyOrgByHourly("org1");
    verifyNoInteractions(snapshotController);
  }

  @Test
  void performAsyncHourlyTallyForOrgWhenSynchronousOperationsDisabled() {
    OffsetDateTime start = clock.startOfCurrentHour();
    OffsetDateTime end = start.plusHours(1L);
    resource.performHourlyTallyForOrg("org1", false);
    verify(snapshotTaskManager).tallyOrgByHourly("org1");
    verifyNoInteractions(snapshotController);
  }

  @Test
  void testPurgeRemittances() {
    resource.purgeRemittances();
    verify(remittanceRetentionController).purgeRemittancesAsync();
  }

  @Test
  void testDeleteDataAssociatedWithOrg() {
    String orgId = "org1";
    when(properties.isDevMode()).thenReturn(true);
    resource.deleteDataAssociatedWithOrg(orgId);
    verify(internalTallyDataController).deleteDataAssociatedWithOrg(orgId);
  }
}
