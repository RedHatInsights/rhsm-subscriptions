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
package org.candlepin.subscriptions.tally;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Set;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.configuration.FeatureFlags;
import org.candlepin.subscriptions.db.TallyStateRepository;
import org.candlepin.subscriptions.event.EventController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.retry.support.RetryTemplate;

@ExtendWith(MockitoExtension.class)
class TallySnapshotControllerFeatureFlagsTest {

  private static final String ORG_ID = "test-org";

  @Mock private ApplicationProperties appProps;
  @Mock private InventoryAccountUsageCollector usageCollector;
  @Mock private EventController eventController;
  @Mock private MaxSeenSnapshotStrategy maxSeenSnapshotStrategy;
  @Mock private MetricUsageCollector metricUsageCollector;
  @Mock private CombiningRollupSnapshotStrategy combiningRollupSnapshotStrategy;
  @Mock private SnapshotSummaryProducer summaryProducer;
  @Mock private TallyStateRepository tallyStateRepository;
  @Mock private MeterRegistry meterRegistry;
  @Mock private ApplicationClock clock;
  @Mock private FeatureFlags featureFlags;
  @Captor private ArgumentCaptor<Set<String>> applicableProductsCaptor;

  private TallySnapshotController controller;

  @BeforeEach
  void setUp() {
    controller =
        new TallySnapshotController(
            appProps,
            featureFlags,
            usageCollector,
            eventController,
            maxSeenSnapshotStrategy,
            new RetryTemplate(),
            metricUsageCollector,
            combiningRollupSnapshotStrategy,
            summaryProducer,
            tallyStateRepository,
            meterRegistry,
            clock);

    var calculation = new AccountUsageCalculation(ORG_ID);
    when(usageCollector.tally(ORG_ID)).thenReturn(calculation);
    when(maxSeenSnapshotStrategy.produceSnapshotsFromCalculations(any())).thenReturn(List.of());
  }

  @Test
  void skipsNightlyTallyHostReconciliationWhenFeatureFlagIsEnabled() {
    when(featureFlags.isEnabled(FeatureFlags.DISABLE_NIGHTLY_TALLY_HOST_UPDATE, false))
        .thenReturn(true);

    controller.produceSnapshotsForOrg(ORG_ID);

    verify(usageCollector, never()).reconcileSystemDataWithHbi(eq(ORG_ID), any());
    verify(usageCollector).tally(ORG_ID);
  }

  @Test
  void performsNightlyTallyHostReconciliationWhenFeatureFlagIsDisabled() {
    when(featureFlags.isEnabled(FeatureFlags.DISABLE_NIGHTLY_TALLY_HOST_UPDATE, false))
        .thenReturn(false);

    controller.produceSnapshotsForOrg(ORG_ID);

    verify(usageCollector)
        .reconcileSystemDataWithHbi(eq(ORG_ID), applicableProductsCaptor.capture());

    Set<String> applicableProducts = applicableProductsCaptor.getValue();
    assertTrue(applicableProducts.contains("RHEL for x86"));
    assertTrue(applicableProducts.contains("RHEL Workstation"));
    assertTrue(applicableProducts.contains("RHEL Compute Node"));
    assertTrue(applicableProducts.contains("rhel-for-x86-els-unconverted"));
    assertFalse(applicableProducts.contains("rhel-for-x86-els-payg"));
    assertFalse(applicableProducts.contains("rhel-for-x86-els-payg-addon"));
    verify(usageCollector).tally(ORG_ID);
  }
}
