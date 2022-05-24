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
package org.candlepin.subscriptions.rhmarketplace;

import static org.candlepin.subscriptions.json.TallySnapshot.Granularity.DAILY;
import static org.candlepin.subscriptions.json.TallySnapshot.Granularity.HOURLY;
import static org.candlepin.subscriptions.utilization.api.model.ProductId.OPENSHIFT_DEDICATED_METRICS;
import static org.candlepin.subscriptions.utilization.api.model.ProductId.OPENSHIFT_METRICS;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.ParameterizedTest.DEFAULT_DISPLAY_NAME;
import static org.junit.jupiter.params.ParameterizedTest.DISPLAY_NAME_PLACEHOLDER;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.candlepin.subscriptions.json.TallyMeasurement;
import org.candlepin.subscriptions.json.TallyMeasurement.Uom;
import org.candlepin.subscriptions.json.TallySnapshot;
import org.candlepin.subscriptions.json.TallySnapshot.BillingProvider;
import org.candlepin.subscriptions.json.TallySnapshot.Sla;
import org.candlepin.subscriptions.json.TallySnapshot.Usage;
import org.candlepin.subscriptions.json.TallySummary;
import org.candlepin.subscriptions.registry.TagMetaData;
import org.candlepin.subscriptions.registry.TagProfile;
import org.candlepin.subscriptions.rhmarketplace.api.model.UsageEvent;
import org.candlepin.subscriptions.rhmarketplace.api.model.UsageMeasurement;
import org.candlepin.subscriptions.tally.UsageCalculation;
import org.candlepin.subscriptions.user.AccountService;
import org.candlepin.subscriptions.utilization.api.model.ProductId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RhMarketplacePayloadMapperTest {

  @Mock TagProfile tagProfile;
  @Mock RhMarketplaceProperties rhMarketplaceProperties;
  @Mock RhMarketplaceSubscriptionIdProvider mockProvider;

  @Mock AccountService accountService;

  @InjectMocks RhMarketplacePayloadMapper rhMarketplacePayloadMapper;

  @BeforeEach
  void init() {
    // Tell Mockito not to complain if some of these mocks aren't used in a particular test
    lenient()
        .when(tagProfile.metricIdForTagAndUom(OPENSHIFT_METRICS.toString(), Uom.CORES))
        .thenReturn("redhat.com:openshift:cpu_hour");

    lenient()
        .when(tagProfile.metricIdForTagAndUom(OPENSHIFT_DEDICATED_METRICS.toString(), Uom.CORES))
        .thenReturn(RhMarketplacePayloadMapper.OPENSHIFT_DEDICATED_4_CPU_HOUR);

    TagMetaData meta = new TagMetaData();
    meta.setBillingModel("PAYG");
    lenient()
        .when(tagProfile.getTagMetaDataByTag(OPENSHIFT_DEDICATED_METRICS.toString()))
        .thenReturn(Optional.of(meta));
    lenient()
        .when(tagProfile.getTagMetaDataByTag(OPENSHIFT_METRICS.toString()))
        .thenReturn(Optional.of(meta));
  }

  @ParameterizedTest(name = DISPLAY_NAME_PLACEHOLDER + " " + DEFAULT_DISPLAY_NAME)
  @MethodSource("generateHardwareMeasurementPermutations")
  void testProduceUsageMeasurements(
      ProductId productId,
      List<TallyMeasurement> tallyMeasurements,
      List<UsageMeasurement> expected) {

    var snapshot =
        new TallySnapshot()
            .withId(UUID.fromString("c204074d-626f-4272-aa05-b6d69d6de16a"))
            .withProductId(productId.toString())
            .withSnapshotDate(OffsetDateTime.now())
            .withUsage(Usage.PRODUCTION)
            .withTallyMeasurements(tallyMeasurements)
            .withSla(Sla.PREMIUM)
            .withBillingProvider(TallySnapshot.BillingProvider.RED_HAT)
            .withGranularity(HOURLY);

    var actual =
        rhMarketplacePayloadMapper.produceUsageMeasurements(snapshot, productId.toString());

    assertEquals(expected, actual);
  }

  @SuppressWarnings("linelength")
  static Stream<Arguments> generateHardwareMeasurementPermutations() {
    double value = 36.0;

    TallyMeasurement physicalCoreMeasurement =
        new TallyMeasurement()
            .withHardwareMeasurementType("PHYSICAL")
            .withUom(Uom.CORES)
            .withValue(value);
    TallyMeasurement totalCoreMeasurment =
        new TallyMeasurement()
            .withHardwareMeasurementType("TOTAL")
            .withUom(Uom.CORES)
            .withValue(value);
    TallyMeasurement virtualCoreMeasurment =
        new TallyMeasurement()
            .withHardwareMeasurementType("VIRTUAL")
            .withUom(Uom.CORES)
            .withValue(value);

    UsageMeasurement usageMeasurement =
        new UsageMeasurement().value(value).metricId("redhat.com:openshift:cpu_hour");
    UsageMeasurement divideByFourMeasurement =
        new UsageMeasurement()
            .value(value / 4)
            .metricId(RhMarketplacePayloadMapper.OPENSHIFT_DEDICATED_4_CPU_HOUR);

    Arguments physical =
        Arguments.of(
            OPENSHIFT_METRICS, List.of(physicalCoreMeasurement), List.of(usageMeasurement));
    Arguments virtual =
        Arguments.of(OPENSHIFT_METRICS, List.of(virtualCoreMeasurment), List.of(usageMeasurement));
    Arguments physicalTotal =
        Arguments.of(
            OPENSHIFT_METRICS,
            List.of(physicalCoreMeasurement, totalCoreMeasurment),
            List.of(usageMeasurement));
    Arguments virtualTotal =
        Arguments.of(
            OPENSHIFT_METRICS,
            List.of(virtualCoreMeasurment, totalCoreMeasurment),
            List.of(usageMeasurement));
    Arguments physicalVirtual =
        Arguments.of(
            OPENSHIFT_METRICS,
            List.of(physicalCoreMeasurement, virtualCoreMeasurment),
            List.of(usageMeasurement, usageMeasurement));
    Arguments physicalVirtualTotal =
        Arguments.of(
            OPENSHIFT_METRICS,
            List.of(physicalCoreMeasurement, virtualCoreMeasurment, totalCoreMeasurment),
            List.of(usageMeasurement, usageMeasurement));
    Arguments physicalOsd =
        Arguments.of(
            OPENSHIFT_DEDICATED_METRICS,
            List.of(physicalCoreMeasurement),
            List.of(divideByFourMeasurement));
    return Stream.of(
        physical,
        virtual,
        physicalTotal,
        virtualTotal,
        physicalVirtual,
        physicalVirtualTotal,
        physicalOsd);
  }

  @ParameterizedTest(name = DISPLAY_NAME_PLACEHOLDER + " " + DEFAULT_DISPLAY_NAME)
  @MethodSource("generateIsSnapshotPaygEligibleData")
  void testIsSnapshotPAYGEligible(TallySnapshot snapshot, boolean isEligible) {
    boolean actual = rhMarketplacePayloadMapper.isSnapshotPAYGEligible(snapshot);
    assertEquals(isEligible, actual);
  }

  static Stream<Arguments> generateIsSnapshotPaygEligibleData() {

    Arguments eligbileOpenShiftMetrics =
        Arguments.of(
            new TallySnapshot()
                .withProductId("OpenShift-metrics")
                .withUsage(Usage.PRODUCTION)
                .withSla(Sla.PREMIUM)
                .withBillingProvider(BillingProvider.RED_HAT)
                .withGranularity(HOURLY),
            true);

    Arguments notEligibleBecauseSla =
        Arguments.of(
            new TallySnapshot()
                .withProductId("OpenShift-metrics")
                .withUsage(Usage.PRODUCTION)
                .withSla(Sla.ANY)
                .withBillingProvider(BillingProvider.ANY)
                .withGranularity(HOURLY),
            false);

    Arguments notEligibleBecauseGranularity =
        Arguments.of(
            new TallySnapshot()
                .withProductId("OpenShift-metrics")
                .withUsage(Usage.PRODUCTION)
                .withSla(Sla.PREMIUM)
                .withBillingProvider(BillingProvider.ANY)
                .withGranularity(DAILY),
            false);

    Arguments notElgibleBecauseUsage =
        Arguments.of(
            new TallySnapshot()
                .withProductId("OpenShift-metrics")
                .withUsage(Usage.ANY)
                .withSla(Sla.PREMIUM)
                .withBillingProvider(BillingProvider.ANY)
                .withGranularity(HOURLY),
            false);

    Arguments eligbileOpenShiftDedicatedMetrics =
        Arguments.of(
            new TallySnapshot()
                .withProductId("OpenShift-dedicated-metrics")
                .withUsage(Usage.PRODUCTION)
                .withSla(Sla.PREMIUM)
                .withBillingProvider(BillingProvider.RED_HAT)
                .withGranularity(HOURLY),
            true);

    Arguments notEligibleBecauseProductId =
        Arguments.of(
            new TallySnapshot()
                .withProductId("RHEL")
                .withUsage(Usage.PRODUCTION)
                .withSla(Sla.PREMIUM)
                .withBillingProvider(BillingProvider.RED_HAT)
                .withGranularity(HOURLY),
            false);

    return Stream.of(
        eligbileOpenShiftMetrics,
        eligbileOpenShiftDedicatedMetrics,
        notEligibleBecauseProductId,
        notEligibleBecauseSla,
        notEligibleBecauseGranularity,
        notElgibleBecauseUsage);
  }

  @Test
  void testProduceUsageEvents() {
    TallyMeasurement physicalCoreMeasurement =
        new TallyMeasurement()
            .withHardwareMeasurementType("PHYSICAL")
            .withUom(Uom.CORES)
            .withValue(36.0);

    when(mockProvider.findSubscriptionId(
            any(String.class),
            any(String.class),
            any(UsageCalculation.Key.class),
            any(OffsetDateTime.class),
            any(OffsetDateTime.class)))
        .thenReturn(Optional.of("DUMMY"));

    var snapshotDateLong = 1616100754L;

    OffsetDateTime snapshotDate =
        OffsetDateTime.ofInstant(Instant.ofEpochMilli(snapshotDateLong), ZoneId.of("UTC"));
    var snapshot =
        new TallySnapshot()
            .withId(UUID.fromString("c204074d-626f-4272-aa05-b6d69d6de16a"))
            .withProductId("OpenShift-metrics")
            .withSnapshotDate(snapshotDate)
            .withUsage(Usage.PRODUCTION)
            .withTallyMeasurements(List.of(physicalCoreMeasurement))
            .withSla(Sla.PREMIUM)
            .withBillingProvider(BillingProvider.RED_HAT)
            .withGranularity(HOURLY);

    String account = "test123";
    String orgId = "org123";
    var summary =
        new TallySummary().withTallySnapshots(List.of(snapshot)).withAccountNumber(account);

    when(accountService.lookupOrgId(account)).thenReturn(orgId);

    var usageMeasurement =
        new UsageMeasurement().value(36.0).metricId("redhat.com:openshift:cpu_hour");
    var expected =
        List.of(
            new UsageEvent()
                .start(snapshotDateLong)
                .end(1619700754L)
                .eventId("c204074d-626f-4272-aa05-b6d69d6de16a")
                .measuredUsage(List.of(usageMeasurement)));

    List<UsageEvent> actual = rhMarketplacePayloadMapper.produceUsageEvents(summary);

    assertEquals(1, actual.size());
    assertEquals(expected.get(0).getEventId(), actual.get(0).getEventId());
    assertEquals(expected.get(0).getMeasuredUsage(), actual.get(0).getMeasuredUsage());
    assertEquals(expected.get(0).getStart(), actual.get(0).getStart());
    assertEquals(expected.get(0).getEnd(), actual.get(0).getEnd());
  }

  @Test
  void testProduceUsageEventsWithNullBillingProvider() {
    TallyMeasurement physicalCoreMeasurement =
        new TallyMeasurement()
            .withHardwareMeasurementType("PHYSICAL")
            .withUom(Uom.CORES)
            .withValue(36.0);

    when(mockProvider.findSubscriptionId(
            any(String.class),
            any(String.class),
            any(UsageCalculation.Key.class),
            any(OffsetDateTime.class),
            any(OffsetDateTime.class)))
        .thenReturn(Optional.of("DUMMY"));

    var snapshotDateLong = 1616100754L;

    OffsetDateTime snapshotDate =
        OffsetDateTime.ofInstant(Instant.ofEpochMilli(snapshotDateLong), ZoneId.of("UTC"));
    var snapshot =
        new TallySnapshot()
            .withId(UUID.fromString("c204074d-626f-4272-aa05-b6d69d6de16a"))
            .withProductId("OpenShift-metrics")
            .withSnapshotDate(snapshotDate)
            .withUsage(Usage.PRODUCTION)
            .withTallyMeasurements(List.of(physicalCoreMeasurement))
            .withSla(Sla.PREMIUM)
            .withGranularity(HOURLY);

    String account = "test123";
    String orgId = "org123";
    var summary =
        new TallySummary().withTallySnapshots(List.of(snapshot)).withAccountNumber(account);

    when(accountService.lookupOrgId(account)).thenReturn(orgId);

    var usageMeasurement =
        new UsageMeasurement().value(36.0).metricId("redhat.com:openshift:cpu_hour");
    var expected =
        List.of(
            new UsageEvent()
                .start(snapshotDateLong)
                .end(1619700754L)
                .eventId("c204074d-626f-4272-aa05-b6d69d6de16a")
                .measuredUsage(List.of(usageMeasurement)));

    List<UsageEvent> actual = rhMarketplacePayloadMapper.produceUsageEvents(summary);

    assertEquals(1, actual.size());
    assertEquals(expected.get(0).getEventId(), actual.get(0).getEventId());
    assertEquals(expected.get(0).getMeasuredUsage(), actual.get(0).getMeasuredUsage());
    assertEquals(expected.get(0).getStart(), actual.get(0).getStart());
    assertEquals(expected.get(0).getEnd(), actual.get(0).getEnd());
  }

  @ParameterizedTest(name = DISPLAY_NAME_PLACEHOLDER + " " + DEFAULT_DISPLAY_NAME)
  @MethodSource("generateIsSnapshotRHMarketplaceEligibleData")
  void testIsSnapshotRHMarketplaceEligible(TallySnapshot snapshot, boolean isEligible) {
    boolean actual = rhMarketplacePayloadMapper.isSnapshotRHMarketplaceEligible(snapshot);
    assertEquals(isEligible, actual);
  }

  static Stream<Arguments> generateIsSnapshotRHMarketplaceEligibleData() {

    Arguments eligibleRedHatBillingProvider =
        Arguments.of(
            new TallySnapshot()
                .withProductId("OpenShift-metrics")
                .withUsage(Usage.PRODUCTION)
                .withSla(Sla.PREMIUM)
                .withGranularity(HOURLY)
                .withBillingProvider(BillingProvider.RED_HAT),
            true);

    Arguments eligibleEmptyBillingProvider =
        Arguments.of(
            new TallySnapshot()
                .withProductId("OpenShift-metrics")
                .withUsage(Usage.PRODUCTION)
                .withSla(Sla.PREMIUM)
                .withGranularity(HOURLY)
                .withBillingProvider(BillingProvider.__EMPTY__),
            true);

    Arguments notEligibleAnyBillingProvider =
        Arguments.of(
            new TallySnapshot()
                .withProductId("OpenShift-metrics")
                .withUsage(Usage.PRODUCTION)
                .withSla(Sla.PREMIUM)
                .withGranularity(HOURLY)
                .withBillingProvider(BillingProvider.ANY),
            false);

    Arguments eligibleNullBillingProvider =
        Arguments.of(
            new TallySnapshot()
                .withProductId("OpenShift-metrics")
                .withUsage(Usage.PRODUCTION)
                .withSla(Sla.PREMIUM)
                .withGranularity(HOURLY),
            true);

    Arguments notEligibleAWSBillingProvider =
        Arguments.of(
            new TallySnapshot()
                .withProductId("OpenShift-metrics")
                .withUsage(Usage.PRODUCTION)
                .withSla(Sla.ANY)
                .withGranularity(HOURLY)
                .withBillingProvider(BillingProvider.AWS),
            false);

    Arguments notEligibleAzureBillingProvider =
        Arguments.of(
            new TallySnapshot()
                .withProductId("OpenShift-metrics")
                .withUsage(Usage.PRODUCTION)
                .withSla(Sla.PREMIUM)
                .withGranularity(DAILY)
                .withBillingProvider(BillingProvider.AZURE),
            false);

    Arguments notEligibleOracleBillingProvider =
        Arguments.of(
            new TallySnapshot()
                .withProductId("OpenShift-metrics")
                .withUsage(Usage.PRODUCTION)
                .withSla(Sla.PREMIUM)
                .withGranularity(DAILY)
                .withBillingProvider(BillingProvider.ORACLE),
            false);

    Arguments notEligibleGcpBillingProvider =
        Arguments.of(
            new TallySnapshot()
                .withProductId("OpenShift-metrics")
                .withUsage(Usage.PRODUCTION)
                .withSla(Sla.PREMIUM)
                .withGranularity(DAILY)
                .withBillingProvider(BillingProvider.GCP),
            false);

    return Stream.of(
        eligibleRedHatBillingProvider,
        eligibleEmptyBillingProvider,
        eligibleNullBillingProvider,
        notEligibleAnyBillingProvider,
        notEligibleAWSBillingProvider,
        notEligibleAzureBillingProvider,
        notEligibleOracleBillingProvider,
        notEligibleGcpBillingProvider);
  }
}
