/*
 * Copyright (c) 2021 Red Hat, Inc.
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
package org.candlepin.subscriptions.marketplace;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.candlepin.subscriptions.files.ProductProfile;
import org.candlepin.subscriptions.files.ProductProfileRegistry;
import org.candlepin.subscriptions.json.TallyMeasurement;
import org.candlepin.subscriptions.json.TallySnapshot;
import org.candlepin.subscriptions.json.TallySummary;
import org.candlepin.subscriptions.marketplace.api.model.UsageEvent;
import org.candlepin.subscriptions.marketplace.api.model.UsageMeasurement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

class MarketplacePayloadMapperTest {

    @Mock
    ProductProfileRegistry profileRegistry;
    @InjectMocks
    MarketplacePayloadMapper marketplacePayloadMapper;

    @BeforeEach
    void init() {

        ProductProfile productProfile = new ProductProfile();
        productProfile.setMetricId("redhat.com:openshiftdedicated:cpu_hour");
        MockitoAnnotations.openMocks(this);

        when(profileRegistry.findProfileForSwatchProductId(anyString())).thenReturn(productProfile);
    }

    @ParameterizedTest
    @MethodSource("generateHardwareMeasurementPermutations")
    void testProduceUsageMeasurements(
        List<TallyMeasurement> tallyMeasurements, List<UsageMeasurement> expected) {

        String productId = "OpenShift-metrics";

        var snapshot = new TallySnapshot()
            .withId(UUID.fromString("c204074d-626f-4272-aa05-b6d69d6de16a"))
            .withProductId(productId)
            .withSnapshotDate(OffsetDateTime.now())
            .withUsage(TallySnapshot.Usage.PRODUCTION)
            .withTallyMeasurements(tallyMeasurements)
            .withSla(TallySnapshot.Sla.PREMIUM)
            .withGranularity(TallySnapshot.Granularity.HOURLY);

        var actual = marketplacePayloadMapper
            .produceUsageMeasurements(snapshot, productId);

        assertEquals(expected, actual);
    }

    @SuppressWarnings("linelength")
    static Stream<Arguments> generateHardwareMeasurementPermutations() {
        double value = 36.0;

        TallyMeasurement physicalCoreMeasurement = new TallyMeasurement()
            .withHardwareMeasurementType("PHYSICAL").withUom(TallyMeasurement.Uom.CORES).withValue(value);
        TallyMeasurement totalCoreMeasurment = new TallyMeasurement().withHardwareMeasurementType("TOTAL")
            .withUom(TallyMeasurement.Uom.CORES).withValue(value);
        TallyMeasurement virtualCoreMeasurment = new TallyMeasurement().withHardwareMeasurementType("VIRTUAL")
            .withUom(TallyMeasurement.Uom.CORES).withValue(value);

        UsageMeasurement usageMeasurement = new UsageMeasurement().value(value)
            .metricId("redhat.com:openshiftdedicated:cpu_hour");

        Arguments physical = Arguments.of(List.of(physicalCoreMeasurement), List.of(usageMeasurement));
        Arguments virtual = Arguments.of(List.of(virtualCoreMeasurment), List.of(usageMeasurement));
        Arguments physicalTotal = Arguments.of(List.of(physicalCoreMeasurement, totalCoreMeasurment), List.of(usageMeasurement));
        Arguments virtualTotal = Arguments.of(List.of(virtualCoreMeasurment, totalCoreMeasurment), List.of(usageMeasurement));
        Arguments physicalVirtual = Arguments.of(List.of(physicalCoreMeasurement, virtualCoreMeasurment), List.of(usageMeasurement, usageMeasurement));
        Arguments physicalVirtualTotal = Arguments.of(List.of(physicalCoreMeasurement, virtualCoreMeasurment, totalCoreMeasurment), List.of(usageMeasurement, usageMeasurement));

        return Stream
            .of(physical, virtual, physicalTotal, virtualTotal, physicalVirtual, physicalVirtualTotal);
    }

    @ParameterizedTest(name = "testIsSnapshotPAYGEligible [{index}]")
    @MethodSource("generateIsSnapshotPaygEligibleData")
    void testIsSnapshotPAYGEligible(TallySnapshot snapshot, boolean isEligible) {

        boolean actual = marketplacePayloadMapper.isSnapshotPAYGEligible(snapshot);
        assertEquals(isEligible, actual);
    }

    static Stream<Arguments> generateIsSnapshotPaygEligibleData() {

        Arguments eligbileOpenShiftMetrics = Arguments
            .of(new TallySnapshot().withProductId("OpenShift-metrics")
            .withUsage(TallySnapshot.Usage.PRODUCTION).withSla(TallySnapshot.Sla.PREMIUM)
            .withGranularity(TallySnapshot.Granularity.HOURLY), true);

        Arguments notEligibleBecauseSla = Arguments.of(new TallySnapshot().withProductId("OpenShift-metrics")
            .withUsage(TallySnapshot.Usage.PRODUCTION).withSla(TallySnapshot.Sla.ANY)
            .withGranularity(TallySnapshot.Granularity.HOURLY), false);

        Arguments notEligibleBecauseGranularity = Arguments
            .of(new TallySnapshot().withProductId("OpenShift-metrics")
            .withUsage(TallySnapshot.Usage.PRODUCTION).withSla(TallySnapshot.Sla.PREMIUM)
            .withGranularity(TallySnapshot.Granularity.DAILY), false);

        Arguments notElgibleBecauseUsage = Arguments
            .of(new TallySnapshot().withProductId("OpenShift-metrics").withUsage(TallySnapshot.Usage.ANY)
            .withSla(TallySnapshot.Sla.PREMIUM).withGranularity(TallySnapshot.Granularity.HOURLY), false);

        Arguments eligbileOpenShiftDedicatedMetrics = Arguments
            .of(new TallySnapshot().withProductId("OpenShift-dedicated-metrics")
            .withUsage(TallySnapshot.Usage.PRODUCTION).withSla(TallySnapshot.Sla.PREMIUM)
            .withGranularity(TallySnapshot.Granularity.HOURLY), true);

        Arguments notEligibleBecauseProductId = Arguments
            .of(new TallySnapshot().withProductId("RHEL").withUsage(TallySnapshot.Usage.PRODUCTION)
            .withSla(TallySnapshot.Sla.PREMIUM).withGranularity(TallySnapshot.Granularity.HOURLY), false);

        return Stream
            .of(eligbileOpenShiftMetrics, eligbileOpenShiftDedicatedMetrics, notEligibleBecauseProductId,
                notEligibleBecauseSla, notEligibleBecauseGranularity, notElgibleBecauseUsage);
    }

    @Test
    void testProduceUsageEvents() {
        TallyMeasurement physicalCoreMeasurement = new TallyMeasurement()
            .withHardwareMeasurementType("PHYSICAL").withUom(TallyMeasurement.Uom.CORES).withValue(36.0);

        var snapshotDateLong = 1616100754L;

        OffsetDateTime snapshotDate = OffsetDateTime
            .ofInstant(Instant.ofEpochMilli(snapshotDateLong), ZoneId.of("UTC"));
        var snapshot = new TallySnapshot()
            .withId(UUID.fromString("c204074d-626f-4272-aa05-b6d69d6de16a"))
            .withProductId("OpenShift-metrics")
            .withSnapshotDate(snapshotDate)
            .withUsage(TallySnapshot.Usage.PRODUCTION)
            .withTallyMeasurements(List.of(physicalCoreMeasurement))
            .withSla(TallySnapshot.Sla.PREMIUM)
            .withGranularity(TallySnapshot.Granularity.HOURLY);

        var summary = new TallySummary().withTallySnapshots(List.of(snapshot)).withAccountNumber("test123");

        var expected = List
            .of(new UsageEvent().start(1612500L).end(1616100L).eventId("c204074d-626f-4272-aa05-b6d69d6de16a")
            .measuredUsage(List.of(
            new UsageMeasurement().value(36.0).metricId("redhat.com:openshiftdedicated:cpu_hour"))));

        List<UsageEvent> actual = marketplacePayloadMapper.produceUsageEvents(summary);

        assertEquals(1, actual.size());
        assertEquals(expected.get(0).getEventId(), actual.get(0).getEventId());
        assertEquals(expected.get(0).getMeasuredUsage(), actual.get(0).getMeasuredUsage());
        assertEquals(expected.get(0).getStart(), actual.get(0).getStart());
        assertEquals(expected.get(0).getEnd(), actual.get(0).getEnd());
    }
}
