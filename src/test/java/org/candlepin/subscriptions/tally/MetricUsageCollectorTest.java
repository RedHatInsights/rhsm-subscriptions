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
package org.candlepin.subscriptions.tally;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.candlepin.subscriptions.db.AccountRepository;
import org.candlepin.subscriptions.db.model.Account;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.Host;
import org.candlepin.subscriptions.db.model.HostBucketKey;
import org.candlepin.subscriptions.db.model.HostTallyBucket;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.event.EventController;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Measurement;
import org.candlepin.subscriptions.util.ApplicationClock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

@ExtendWith(MockitoExtension.class)
class MetricUsageCollectorTest {
    MetricUsageCollector metricUsageCollector;

    @Mock
    AccountRepository accountRepo;

    @Mock
    EventController eventController;

    ApplicationClock clock = new ApplicationClock();

    @BeforeEach
    void setup() {
        metricUsageCollector = new MetricUsageCollector(accountRepo, eventController, clock);
    }

    @Test
    void testCollectCreatesNewInstanceRecords() {
        Measurement measurement = new Measurement().withUom(Measurement.Uom.CORES).withValue(42.0);
        Event event = new Event()
            .withEventId(UUID.randomUUID())
            .withTimestamp(OffsetDateTime.parse("2021-02-26T00:00:00Z"))
            .withServiceType(MetricUsageCollector.ProductConfig.SERVICE_TYPE)
            .withInstanceId(UUID.randomUUID().toString())
            .withMeasurements(Collections.singletonList(measurement));
        Account account = new Account();
        account.setAccountNumber("account123");
        when(accountRepo.findById(any())).thenReturn(Optional.of(account));
        when(eventController.fetchEventsInTimeRange(any(), any(), any())).thenReturn(Stream.of(event));
        AccountUsageCalculation accountUsageCalculation = metricUsageCollector
            .collect("account123", OffsetDateTime.MIN, OffsetDateTime.MAX);
        Host instance = account.getServiceInstances().get(event.getInstanceId());
        assertNotNull(instance);
    }

    @Test
    void testPopulatesUsageCalculations() {
        Measurement measurement = new Measurement().withUom(Measurement.Uom.CORES).withValue(42.0);
        Event event = new Event()
            .withEventId(UUID.randomUUID())
            .withTimestamp(OffsetDateTime.parse("2021-02-26T00:00:00Z"))
            .withServiceType(MetricUsageCollector.ProductConfig.SERVICE_TYPE)
            .withInstanceId(UUID.randomUUID().toString())
            .withMeasurements(Collections.singletonList(measurement));
        Account account = new Account();
        account.setAccountNumber("account123");
        when(accountRepo.findById(any())).thenReturn(Optional.of(account));
        when(eventController.fetchEventsInTimeRange(any(), any(), any())).thenReturn(Stream.of(event));
        AccountUsageCalculation accountUsageCalculation = metricUsageCollector
            .collect("account123", OffsetDateTime.MIN, OffsetDateTime.MAX);
        assertNotNull(accountUsageCalculation);
        UsageCalculation.Key usageCalculationKey =
            new UsageCalculation.Key(MetricUsageCollector.ProductConfig.OPENSHIFT_PRODUCT_ID,
            ServiceLevel.PREMIUM, Usage.PRODUCTION);
        assertEquals(Double.valueOf(42.0),
            accountUsageCalculation.getCalculation(usageCalculationKey).getTotals(
            HardwareMeasurementType.PHYSICAL).getMeasurement(Measurement.Uom.CORES));
    }

    @ParameterizedTest
    @EnumSource(Event.HardwareType.class)
    void testCollectHandlesAllHardwareTypes(Event.HardwareType hardwareType) {
        Event event = new Event()
            .withEventId(UUID.randomUUID())
            .withTimestamp(OffsetDateTime.parse("2021-02-26T00:00:00Z"))
            .withServiceType(MetricUsageCollector.ProductConfig.SERVICE_TYPE)
            .withHardwareType(hardwareType)
            .withCloudProvider(Event.CloudProvider.__EMPTY__)
            .withInstanceId(UUID.randomUUID().toString());
        Account account = new Account();
        account.setAccountNumber("account123");
        when(accountRepo.findById(any())).thenReturn(Optional.of(account));
        when(eventController.fetchEventsInTimeRange(any(), any(), any())).thenReturn(Stream.of(event));
        AccountUsageCalculation accountUsageCalculation = metricUsageCollector
            .collect("account123", OffsetDateTime.MIN, OffsetDateTime.MAX);
        assertNotNull(accountUsageCalculation);
    }

    @ParameterizedTest
    @EnumSource(Event.CloudProvider.class)
    void testCollectHandlesAllCloudProviders(Event.CloudProvider cloudProvider) {
        Event event = new Event()
            .withEventId(UUID.randomUUID())
            .withTimestamp(OffsetDateTime.parse("2021-02-26T00:00:00Z"))
            .withServiceType(MetricUsageCollector.ProductConfig.SERVICE_TYPE)
            .withHardwareType(Event.HardwareType.CLOUD)
            .withCloudProvider(cloudProvider)
            .withInstanceId(UUID.randomUUID().toString());
        Account account = new Account();
        account.setAccountNumber("account123");
        when(accountRepo.findById(any())).thenReturn(Optional.of(account));
        when(eventController.fetchEventsInTimeRange(any(), any(), any())).thenReturn(Stream.of(event));
        AccountUsageCalculation accountUsageCalculation = metricUsageCollector
            .collect("account123", OffsetDateTime.MIN, OffsetDateTime.MAX);
        assertNotNull(accountUsageCalculation);
    }

    @Test
    void testCollectAddsBucketsForApplicableUsageKeys() {
        Measurement measurement = new Measurement().withUom(Measurement.Uom.CORES).withValue(42.0);
        Event event = new Event()
            .withEventId(UUID.randomUUID())
            .withTimestamp(OffsetDateTime.parse("2021-02-26T00:00:00Z"))
            .withServiceType(MetricUsageCollector.ProductConfig.SERVICE_TYPE)
            .withInstanceId(UUID.randomUUID().toString())
            .withMeasurements(Collections.singletonList(measurement))
            .withSla(Event.Sla.PREMIUM);
        Account account = new Account();
        account.setAccountNumber("account123");
        when(accountRepo.findById(any())).thenReturn(Optional.of(account));
        when(eventController.fetchEventsInTimeRange(any(), any(), any())).thenReturn(Stream.of(event));
        AccountUsageCalculation accountUsageCalculation = metricUsageCollector
            .collect("account123", OffsetDateTime.MIN, OffsetDateTime.MAX);
        Host instance = account.getServiceInstances().get(event.getInstanceId());
        assertNotNull(instance);
        Set<HostTallyBucket> expected = new HashSet<>();
        Set.of(Usage._ANY, Usage.PRODUCTION).forEach(usage ->
            Set.of(ServiceLevel._ANY, ServiceLevel.PREMIUM).forEach(sla -> {
                HostTallyBucket bucket = new HostTallyBucket();
                bucket.setHost(instance);
                HostBucketKey key = new HostBucketKey();
                key.setProductId(MetricUsageCollector.ProductConfig.OPENSHIFT_PRODUCT_ID);
                key.setSla(sla);
                key.setUsage(usage);
                key.setAsHypervisor(false);
                bucket.setKey(key);
                expected.add(bucket);
            })
        );
        assertEquals(expected, new HashSet<>(instance.getBuckets()));
    }

    @Test
    void testAddsAnySlaToBuckets() {
        Measurement measurement = new Measurement().withUom(Measurement.Uom.CORES).withValue(42.0);
        Event event = new Event()
            .withEventId(UUID.randomUUID())
            .withTimestamp(OffsetDateTime.parse("2021-02-26T00:00:00Z"))
            .withServiceType(MetricUsageCollector.ProductConfig.SERVICE_TYPE)
            .withInstanceId(UUID.randomUUID().toString())
            .withMeasurements(Collections.singletonList(measurement))
            .withSla(Event.Sla.PREMIUM);
        Account account = new Account();
        account.setAccountNumber("account123");
        when(accountRepo.findById(any())).thenReturn(Optional.of(account));
        when(eventController.fetchEventsInTimeRange(any(), any(), any())).thenReturn(Stream.of(event));
        AccountUsageCalculation accountUsageCalculation = metricUsageCollector
            .collect("account123", OffsetDateTime.MIN, OffsetDateTime.MAX);
        assertNotNull(accountUsageCalculation);
        UsageCalculation.Key usageCalculationKey =
            new UsageCalculation.Key(MetricUsageCollector.ProductConfig.OPENSHIFT_PRODUCT_ID,
            ServiceLevel._ANY, Usage.PRODUCTION);
        assertEquals(Double.valueOf(42.0),
            accountUsageCalculation.getCalculation(usageCalculationKey).getTotals(
            HardwareMeasurementType.PHYSICAL).getMeasurement(Measurement.Uom.CORES));
    }

    @Test
    void testAddsAnyUsageToBuckets() {
        Measurement measurement = new Measurement().withUom(Measurement.Uom.CORES).withValue(42.0);
        Event event = new Event()
            .withEventId(UUID.randomUUID())
            .withTimestamp(OffsetDateTime.parse("2021-02-26T00:00:00Z"))
            .withServiceType(MetricUsageCollector.ProductConfig.SERVICE_TYPE)
            .withInstanceId(UUID.randomUUID().toString())
            .withMeasurements(Collections.singletonList(measurement))
            .withUsage(Event.Usage.PRODUCTION);
        Account account = new Account();
        account.setAccountNumber("account123");
        when(accountRepo.findById(any())).thenReturn(Optional.of(account));
        when(eventController.fetchEventsInTimeRange(any(), any(), any())).thenReturn(Stream.of(event));
        AccountUsageCalculation accountUsageCalculation = metricUsageCollector
            .collect("account123", OffsetDateTime.MIN, OffsetDateTime.MAX);
        assertNotNull(accountUsageCalculation);
        UsageCalculation.Key usageCalculationKey =
            new UsageCalculation.Key(MetricUsageCollector.ProductConfig.OPENSHIFT_PRODUCT_ID,
            ServiceLevel.PREMIUM, Usage._ANY);
        assertEquals(Double.valueOf(42.0),
            accountUsageCalculation.getCalculation(usageCalculationKey).getTotals(
            HardwareMeasurementType.PHYSICAL).getMeasurement(Measurement.Uom.CORES));
    }

    @Test
    void testHandlesDuplicateEvents() {
        Measurement measurement = new Measurement().withUom(Measurement.Uom.CORES).withValue(42.0);
        Event event = new Event()
            .withEventId(UUID.randomUUID())
            .withTimestamp(OffsetDateTime.parse("2021-02-26T00:00:00Z"))
            .withServiceType(MetricUsageCollector.ProductConfig.SERVICE_TYPE)
            .withInstanceId(UUID.randomUUID().toString())
            .withMeasurements(Collections.singletonList(measurement))
            .withUsage(Event.Usage.PRODUCTION);
        Account account = new Account();
        account.setAccountNumber("account123");
        when(accountRepo.findById(any())).thenReturn(Optional.of(account));
        when(eventController.fetchEventsInTimeRange(any(), any(), any())).thenReturn(Stream.of(event, event));
        AccountUsageCalculation accountUsageCalculation = metricUsageCollector
            .collect("account123", OffsetDateTime.MIN, OffsetDateTime.MAX);
        assertNotNull(accountUsageCalculation);
        UsageCalculation.Key usageCalculationKey =
            new UsageCalculation.Key(MetricUsageCollector.ProductConfig.OPENSHIFT_PRODUCT_ID,
            ServiceLevel.PREMIUM, Usage._ANY);
        assertEquals(Double.valueOf(42.0),
            accountUsageCalculation.getCalculation(usageCalculationKey).getTotals(
            HardwareMeasurementType.PHYSICAL).getMeasurement(Measurement.Uom.CORES));
    }

    @Test
    void testUpdatesMonthlyTotal() {
        Measurement measurement = new Measurement().withUom(Measurement.Uom.CORES).withValue(42.0);
        Event event = new Event()
            .withEventId(UUID.randomUUID())
            .withTimestamp(OffsetDateTime.parse("2021-02-26T00:00:00Z"))
            .withServiceType(MetricUsageCollector.ProductConfig.SERVICE_TYPE)
            .withInstanceId(UUID.randomUUID().toString())
            .withMeasurements(Collections.singletonList(measurement))
            .withUsage(Event.Usage.PRODUCTION);
        Account account = new Account();
        account.setAccountNumber("account123");
        when(accountRepo.findById(any())).thenReturn(Optional.of(account));
        when(eventController.fetchEventsInTimeRange(any(), any(), any())).thenReturn(Stream.of(event, event));

        metricUsageCollector.collect("account123", OffsetDateTime.MIN, OffsetDateTime.MAX);
        Host instance = account.getServiceInstances().values().stream().findFirst().orElseThrow();
        assertEquals(Double.valueOf(84.0), instance.getMonthlyTotal("2021-02", Measurement.Uom.CORES));
    }

    @Test
    void testRecalculatesMonthlyTotalWhenEventsAreOld() {
        Measurement measurement = new Measurement().withUom(Measurement.Uom.CORES).withValue(42.0);
        String instanceId = UUID.randomUUID().toString();
        Event event = new Event()
            .withEventId(UUID.randomUUID())
            .withTimestamp(OffsetDateTime.parse("2021-02-26T00:00:00Z"))
            .withServiceType(MetricUsageCollector.ProductConfig.SERVICE_TYPE)
            .withInstanceId(instanceId)
            .withMeasurements(Collections.singletonList(measurement))
            .withUsage(Event.Usage.PRODUCTION);
        Account account = new Account();
        account.setAccountNumber("account123");
        Host existing = new Host();
        existing.addToMonthlyTotal("2021-02", Measurement.Uom.CORES, 10.0);
        existing.setInstanceId(instanceId);
        existing.setInstanceType(MetricUsageCollector.ProductConfig.SERVICE_TYPE);
        existing.setLastSeen(OffsetDateTime.parse("2021-02-25T00:00:01Z"));
        account.getServiceInstances().put(instanceId, existing);
        when(accountRepo.findById(any())).thenReturn(Optional.of(account));
        when(eventController.fetchEventsInTimeRange(any(), any(), any())).thenReturn(Stream.of(event, event));

        metricUsageCollector.collect("account123", OffsetDateTime.parse("2021-01-01T00:00:00Z"),
            OffsetDateTime.parse("2021-03-01T00:00:00Z"));
        Host instance = account.getServiceInstances().values().stream().findFirst().orElseThrow();
        assertEquals(Double.valueOf(84.0), instance.getMonthlyTotal("2021-02", Measurement.Uom.CORES));
    }

    @Test
    void testRecalculationQueriesEventsForAllAffectedMonths() {
        Measurement measurement = new Measurement().withUom(Measurement.Uom.CORES).withValue(42.0);
        String instanceId = UUID.randomUUID().toString();
        Event event = new Event()
            .withEventId(UUID.randomUUID())
            .withTimestamp(OffsetDateTime.parse("2021-02-26T00:00:00Z"))
            .withServiceType(MetricUsageCollector.ProductConfig.SERVICE_TYPE)
            .withInstanceId(instanceId)
            .withMeasurements(Collections.singletonList(measurement))
            .withUsage(Event.Usage.PRODUCTION);
        Account account = new Account();
        account.setAccountNumber("account123");
        Host existing = new Host();
        existing.addToMonthlyTotal("2021-02", Measurement.Uom.CORES, 10.0);
        existing.setInstanceId(instanceId);
        existing.setInstanceType(MetricUsageCollector.ProductConfig.SERVICE_TYPE);
        existing.setLastSeen(OffsetDateTime.parse("2021-02-25T00:00:01Z"));
        account.getServiceInstances().put(instanceId, existing);
        when(accountRepo.findById(any())).thenReturn(Optional.of(account));
        when(eventController.fetchEventsInTimeRange(any(), any(), any())).thenReturn(Stream.of(event, event));

        metricUsageCollector.collect("account123", OffsetDateTime.parse("2021-01-29T00:00:00Z"),
            OffsetDateTime.parse("2021-02-01T00:00:00Z"));
        verify(eventController).fetchEventsInTimeRange("account123",
            OffsetDateTime.parse("2021-01-01T00:00:00Z"),
            OffsetDateTime.parse("2021-02-28T23:59:59.999999999Z"));
    }

    @Test
    void testClearsMeasurementsOnInactiveInstancesWhenRecalculating() {
        Measurement measurement = new Measurement().withUom(Measurement.Uom.CORES).withValue(42.0);
        String instanceId = UUID.randomUUID().toString();
        Event event = new Event()
            .withEventId(UUID.randomUUID())
            .withTimestamp(OffsetDateTime.parse("2021-02-26T00:00:00Z"))
            .withServiceType(MetricUsageCollector.ProductConfig.SERVICE_TYPE)
            .withInstanceId(instanceId)
            .withMeasurements(Collections.singletonList(measurement))
            .withUsage(Event.Usage.PRODUCTION);
        Account account = new Account();
        account.setAccountNumber("account123");

        Host activeInstance = new Host();
        activeInstance.setInstanceId(instanceId);
        activeInstance.setInstanceType(MetricUsageCollector.ProductConfig.SERVICE_TYPE);
        activeInstance.setLastSeen(OffsetDateTime.parse("2021-02-25T00:00:01Z"));
        account.getServiceInstances().put(instanceId, activeInstance);

        Host staleInstance = new Host();
        staleInstance.addToMonthlyTotal("2021-02", Measurement.Uom.CORES, 11.0);
        staleInstance.setInstanceType(MetricUsageCollector.ProductConfig.SERVICE_TYPE);
        staleInstance.setInstanceId(UUID.randomUUID().toString());
        staleInstance.setLastSeen(OffsetDateTime.parse("2021-02-25T00:00:01Z"));
        account.getServiceInstances().put(staleInstance.getInstanceId(), staleInstance);

        when(accountRepo.findById(any())).thenReturn(Optional.of(account));
        when(eventController.fetchEventsInTimeRange(any(), any(), any())).thenReturn(Stream.of(event, event));

        metricUsageCollector.collect("account123", OffsetDateTime.parse("2021-01-01T00:00:00Z"),
            OffsetDateTime.parse("2021-03-01T00:00:00Z"));
        assertEquals(Double.valueOf(84.0), activeInstance.getMonthlyTotal("2021-02", Measurement.Uom.CORES));
        assertNull(staleInstance.getMonthlyTotal("2021-02", Measurement.Uom.CORES));
    }
}
