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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.candlepin.subscriptions.db.AccountServiceInventoryRepository;
import org.candlepin.subscriptions.db.model.*;
import org.candlepin.subscriptions.event.EventController;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Event.Role;
import org.candlepin.subscriptions.json.Measurement;
import org.candlepin.subscriptions.json.Measurement.Uom;
import org.candlepin.subscriptions.registry.TagMapping;
import org.candlepin.subscriptions.registry.TagMetaData;
import org.candlepin.subscriptions.registry.TagMetric;
import org.candlepin.subscriptions.registry.TagProfile;
import org.candlepin.subscriptions.test.TestClockConfiguration;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.candlepin.subscriptions.util.DateRange;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MetricUsageCollectorTest {
  MetricUsageCollector metricUsageCollector;

  @Mock AccountServiceInventoryRepository accountRepo;

  @Mock EventController eventController;

  ApplicationClock clock = new TestClockConfiguration().adjustableClock();

  static final String SERVICE_TYPE = "SERVICE TYPE";
  static final String RHEL_SERVER_SWATCH_PRODUCT_ID = "RHEL_SERVER";
  static final String RHEL_WORKSTATION_SWATCH_PRODUCT_ID = "RHEL_WORKSTATION";
  static final String RHEL = "RHEL";
  static final String OSD_PRODUCT_ID = "OpenShift-dedicated-metrics";

  static final String OSD_METRIC_ID = "OSD-METRIC-ID";

  @BeforeEach
  void setup() {

    TagProfile profile =
        TagProfile.builder()
            .tagMappings(
                List.of(
                    TagMapping.builder()
                        .value("1234")
                        .valueType("engId")
                        .tags(Set.of("RHEL"))
                        .build(),
                    TagMapping.builder()
                        .value("Red Hat Enterprise Linux Server")
                        .valueType("productName")
                        .tags(Set.of("RHEL_SERVER"))
                        .build(),
                    TagMapping.builder()
                        .value("Red Hat Enterprise Linux Workstation")
                        .valueType("productName")
                        .tags(Set.of("RHEL_WORKSTATION"))
                        .build(),
                    TagMapping.builder()
                        .value("osd")
                        .valueType("role")
                        .tags(Set.of(OSD_PRODUCT_ID))
                        .build()))
            .tagMetaData(
                List.of(
                    TagMetaData.builder()
                        .tags(
                            Set.of(
                                RHEL,
                                RHEL_SERVER_SWATCH_PRODUCT_ID,
                                RHEL_WORKSTATION_SWATCH_PRODUCT_ID,
                                OSD_PRODUCT_ID))
                        .serviceType(SERVICE_TYPE)
                        .defaultUsage(Usage.PRODUCTION)
                        .defaultSla(ServiceLevel.PREMIUM)
                        .build()))
            .tagMetrics(
                List.of(
                    TagMetric.builder()
                        .tag(OSD_PRODUCT_ID)
                        .metricId(OSD_METRIC_ID)
                        .uom(Uom.CORES)
                        .build()))
            .build();
    profile.initLookups();

    metricUsageCollector = new MetricUsageCollector(profile, accountRepo, eventController, clock);
  }

  @Test
  void testCollectCreatesNewInstanceRecords() {
    Measurement measurement = new Measurement().withUom(Measurement.Uom.CORES).withValue(42.0);
    Event event =
        new Event()
            .withEventId(UUID.randomUUID())
            .withTimestamp(OffsetDateTime.parse("2021-02-26T00:00:00Z"))
            .withServiceType(SERVICE_TYPE)
            .withInstanceId(UUID.randomUUID().toString())
            .withMeasurements(Collections.singletonList(measurement))
            .withBillingProvider(Event.BillingProvider.RED_HAT)
            .withBillingAccountId(Optional.of("sellerAcct"));
    AccountServiceInventory accountServiceInventory = createTestAccountServiceInventory();
    when(eventController.fetchEventsInTimeRangeByServiceType(any(), any(), any(), any()))
        .thenReturn(Stream.of(event));

    metricUsageCollector.collectHour(accountServiceInventory, OffsetDateTime.MIN);
    Host instance = accountServiceInventory.getServiceInstances().get(event.getInstanceId());
    assertNotNull(instance);
  }

  @Test
  void testPopulatesUsageCalculations() {
    Measurement measurement = new Measurement().withUom(Measurement.Uom.CORES).withValue(42.0);
    Event event =
        new Event()
            .withEventId(UUID.randomUUID())
            .withProductIds(List.of("1234"))
            .withTimestamp(OffsetDateTime.parse("2021-02-26T00:00:00Z"))
            .withServiceType(SERVICE_TYPE)
            .withInstanceId(UUID.randomUUID().toString())
            .withMeasurements(Collections.singletonList(measurement))
            .withBillingProvider(Event.BillingProvider.RED_HAT)
            .withBillingAccountId(Optional.of("sellerAcct"));
    AccountServiceInventory accountServiceInventory = createTestAccountServiceInventory();
    when(eventController.fetchEventsInTimeRangeByServiceType(any(), any(), any(), any()))
        .thenReturn(Stream.of(event));
    AccountUsageCalculation accountUsageCalculation =
        metricUsageCollector.collectHour(accountServiceInventory, OffsetDateTime.MIN);
    assertNotNull(accountUsageCalculation);
    UsageCalculation.Key usageCalculationKey =
        new UsageCalculation.Key(
            RHEL, ServiceLevel.PREMIUM, Usage.PRODUCTION, BillingProvider.RED_HAT, "sellerAcct");
    assertTrue(accountUsageCalculation.containsCalculation(usageCalculationKey));
    assertEquals(
        Double.valueOf(42.0),
        accountUsageCalculation
            .getCalculation(usageCalculationKey)
            .getTotals(HardwareMeasurementType.PHYSICAL)
            .getMeasurement(Measurement.Uom.CORES));
  }

  @ParameterizedTest
  @EnumSource(Event.HardwareType.class)
  void testCollectHandlesAllHardwareTypes(Event.HardwareType hardwareType) {
    Event event =
        new Event()
            .withEventId(UUID.randomUUID())
            .withTimestamp(OffsetDateTime.parse("2021-02-26T00:00:00Z"))
            .withServiceType(SERVICE_TYPE)
            .withHardwareType(hardwareType)
            .withCloudProvider(Event.CloudProvider.__EMPTY__)
            .withInstanceId(UUID.randomUUID().toString());
    AccountServiceInventory accountServiceInventory = createTestAccountServiceInventory();
    when(eventController.fetchEventsInTimeRangeByServiceType(any(), any(), any(), any()))
        .thenReturn(Stream.of(event));
    AccountUsageCalculation accountUsageCalculation =
        metricUsageCollector.collectHour(accountServiceInventory, OffsetDateTime.MIN);
    assertNotNull(accountUsageCalculation);
  }

  @NotNull
  private AccountServiceInventory createTestAccountServiceInventory() {
    AccountServiceInventory accountServiceInventory = new AccountServiceInventory();
    accountServiceInventory.setAccountNumber("account123");
    accountServiceInventory.setOrgId("orgId");
    accountServiceInventory.setServiceType(SERVICE_TYPE);
    return accountServiceInventory;
  }

  @ParameterizedTest
  @EnumSource(Event.CloudProvider.class)
  void testCollectHandlesAllCloudProviders(Event.CloudProvider cloudProvider) {
    Event event =
        new Event()
            .withEventId(UUID.randomUUID())
            .withTimestamp(OffsetDateTime.parse("2021-02-26T00:00:00Z"))
            .withServiceType(SERVICE_TYPE)
            .withHardwareType(Event.HardwareType.CLOUD)
            .withCloudProvider(cloudProvider)
            .withInstanceId(UUID.randomUUID().toString());
    AccountServiceInventory accountServiceInventory = createTestAccountServiceInventory();
    when(eventController.fetchEventsInTimeRangeByServiceType(any(), any(), any(), any()))
        .thenReturn(Stream.of(event));
    AccountUsageCalculation accountUsageCalculation =
        metricUsageCollector.collectHour(accountServiceInventory, OffsetDateTime.MIN);
    assertNotNull(accountUsageCalculation);
  }

  @Test
  void testCollectAddsBucketsForApplicableUsageKeys() {
    Measurement measurement = new Measurement().withUom(Measurement.Uom.CORES).withValue(42.0);
    Event event =
        new Event()
            .withEventId(UUID.randomUUID())
            .withProductIds(List.of("1234"))
            .withTimestamp(OffsetDateTime.parse("2021-02-26T00:00:00Z"))
            .withServiceType(SERVICE_TYPE)
            .withInstanceId(UUID.randomUUID().toString())
            .withMeasurements(Collections.singletonList(measurement))
            .withSla(Event.Sla.PREMIUM)
            .withBillingProvider(Event.BillingProvider.RED_HAT)
            .withBillingAccountId(Optional.of("sellerAcctId"));
    ;
    AccountServiceInventory accountServiceInventory = createTestAccountServiceInventory();
    when(eventController.fetchEventsInTimeRangeByServiceType(any(), any(), any(), any()))
        .thenReturn(Stream.of(event));
    AccountUsageCalculation accountUsageCalculation =
        metricUsageCollector.collectHour(accountServiceInventory, OffsetDateTime.MIN);
    Host instance = accountServiceInventory.getServiceInstances().get(event.getInstanceId());
    assertNotNull(instance);
    Set<HostTallyBucket> expected = new HashSet<>();
    Set.of(Usage._ANY, Usage.PRODUCTION)
        .forEach(
            usage ->
                Set.of(ServiceLevel._ANY, ServiceLevel.PREMIUM)
                    .forEach(
                        sla -> {
                          for (BillingProvider billingProvider :
                              Set.of(BillingProvider._ANY, BillingProvider.RED_HAT)) {
                            for (String billingAcctId : Set.of("sellerAcctId", "_ANY")) {
                              HostBucketKey key = new HostBucketKey();
                              key.setProductId(RHEL);
                              key.setSla(sla);
                              key.setBillingProvider(billingProvider);
                              key.setBillingAccountId(billingAcctId);
                              key.setUsage(usage);
                              key.setAsHypervisor(false);
                              HostTallyBucket bucket = new HostTallyBucket();
                              bucket.setKey(key);
                              bucket.setHost(instance);
                              expected.add(bucket);
                            }
                          }
                        }));
    assertEquals(expected, new HashSet<>(instance.getBuckets()));
  }

  @Test
  void testAddsAnySlaToBuckets() {
    Measurement measurement = new Measurement().withUom(Measurement.Uom.CORES).withValue(42.0);
    Event event =
        new Event()
            .withEventId(UUID.randomUUID())
            .withProductIds(List.of("1234"))
            .withTimestamp(OffsetDateTime.parse("2021-02-26T00:00:00Z"))
            .withServiceType(SERVICE_TYPE)
            .withInstanceId(UUID.randomUUID().toString())
            .withMeasurements(Collections.singletonList(measurement))
            .withSla(Event.Sla.PREMIUM)
            .withBillingProvider(Event.BillingProvider.RED_HAT)
            .withBillingAccountId(Optional.of("sellerAcctId"));
    AccountServiceInventory accountServiceInventory = createTestAccountServiceInventory();
    when(eventController.fetchEventsInTimeRangeByServiceType(any(), any(), any(), any()))
        .thenReturn(Stream.of(event));
    AccountUsageCalculation accountUsageCalculation =
        metricUsageCollector.collectHour(accountServiceInventory, OffsetDateTime.MIN);
    assertNotNull(accountUsageCalculation);
    UsageCalculation.Key usageCalculationKey =
        new UsageCalculation.Key(
            RHEL, ServiceLevel._ANY, Usage.PRODUCTION, BillingProvider.RED_HAT, "sellerAcctId");
    assertTrue(accountUsageCalculation.containsCalculation(usageCalculationKey));
    assertEquals(
        Double.valueOf(42.0),
        accountUsageCalculation
            .getCalculation(usageCalculationKey)
            .getTotals(HardwareMeasurementType.PHYSICAL)
            .getMeasurement(Measurement.Uom.CORES));
  }

  @Test
  void testAddsAnyUsageToBuckets() {
    Measurement measurement = new Measurement().withUom(Measurement.Uom.CORES).withValue(42.0);
    Event event =
        new Event()
            .withEventId(UUID.randomUUID())
            .withProductIds(List.of("1234"))
            .withTimestamp(OffsetDateTime.parse("2021-02-26T00:00:00Z"))
            .withServiceType(SERVICE_TYPE)
            .withInstanceId(UUID.randomUUID().toString())
            .withMeasurements(Collections.singletonList(measurement))
            .withUsage(Event.Usage.PRODUCTION)
            .withBillingProvider(Event.BillingProvider.RED_HAT)
            .withBillingAccountId(Optional.of("sellerAcctId"));
    AccountServiceInventory accountServiceInventory = createTestAccountServiceInventory();
    when(eventController.fetchEventsInTimeRangeByServiceType(any(), any(), any(), any()))
        .thenReturn(Stream.of(event));
    AccountUsageCalculation accountUsageCalculation =
        metricUsageCollector.collectHour(accountServiceInventory, OffsetDateTime.MIN);
    assertNotNull(accountUsageCalculation);
    UsageCalculation.Key usageCalculationKey =
        new UsageCalculation.Key(
            RHEL, ServiceLevel.PREMIUM, Usage._ANY, BillingProvider.RED_HAT, "sellerAcctId");
    assertTrue(accountUsageCalculation.containsCalculation(usageCalculationKey));
    assertEquals(
        Double.valueOf(42.0),
        accountUsageCalculation
            .getCalculation(usageCalculationKey)
            .getTotals(HardwareMeasurementType.PHYSICAL)
            .getMeasurement(Measurement.Uom.CORES));
  }

  @Test
  void productsDefinedInRolesAreIncludedInBucketsWhenSetOnEvent() {
    Measurement measurement = new Measurement().withUom(Measurement.Uom.CORES).withValue(42.0);
    Event event =
        new Event()
            .withEventId(UUID.randomUUID())
            .withEventType("snapshot_" + OSD_METRIC_ID)
            .withTimestamp(OffsetDateTime.parse("2021-02-26T00:00:00Z"))
            .withServiceType(SERVICE_TYPE)
            .withInstanceId(UUID.randomUUID().toString())
            .withMeasurements(Collections.singletonList(measurement))
            .withUsage(Event.Usage.PRODUCTION)
            .withBillingProvider(Event.BillingProvider.RED_HAT)
            .withRole(Role.OSD);

    AccountServiceInventory accountServiceInventory = createTestAccountServiceInventory();
    when(eventController.fetchEventsInTimeRangeByServiceType(any(), any(), any(), any()))
        .thenReturn(Stream.of(event));

    AccountUsageCalculation accountUsageCalculation =
        metricUsageCollector.collectHour(accountServiceInventory, OffsetDateTime.MIN);
    assertNotNull(accountUsageCalculation);

    UsageCalculation.Key serverKey =
        new UsageCalculation.Key(
            OSD_PRODUCT_ID, ServiceLevel.PREMIUM, Usage._ANY, BillingProvider.RED_HAT, "_ANY");
    assertTrue(accountUsageCalculation.containsCalculation(serverKey));
    assertEquals(
        Double.valueOf(42.0),
        accountUsageCalculation
            .getCalculation(serverKey)
            .getTotals(HardwareMeasurementType.PHYSICAL)
            .getMeasurement(Measurement.Uom.CORES));

    // Not defined on the event, should not exist.
    UsageCalculation.Key wsKey =
        new UsageCalculation.Key(
            RHEL_WORKSTATION_SWATCH_PRODUCT_ID,
            ServiceLevel.PREMIUM,
            Usage._ANY,
            BillingProvider._ANY,
            "sellerAcctId");
    assertFalse(accountUsageCalculation.containsCalculation(wsKey));
  }

  @Test
  void productsAreIncludedInBucketsWhenEngIdIsSetOnEvent() {
    Measurement measurement = new Measurement().withUom(Measurement.Uom.CORES).withValue(42.0);
    Event event =
        new Event()
            .withEventId(UUID.randomUUID())
            .withTimestamp(OffsetDateTime.parse("2021-02-26T00:00:00Z"))
            .withServiceType(SERVICE_TYPE)
            .withInstanceId(UUID.randomUUID().toString())
            .withMeasurements(Collections.singletonList(measurement))
            .withUsage(Event.Usage.PRODUCTION)
            .withBillingProvider(Event.BillingProvider.RED_HAT)
            .withProductIds(List.of("1234"));

    AccountServiceInventory accountServiceInventory = createTestAccountServiceInventory();
    when(eventController.fetchEventsInTimeRangeByServiceType(any(), any(), any(), any()))
        .thenReturn(Stream.of(event));

    AccountUsageCalculation accountUsageCalculation =
        metricUsageCollector.collectHour(accountServiceInventory, OffsetDateTime.MIN);
    assertNotNull(accountUsageCalculation);

    UsageCalculation.Key engIdKey =
        new UsageCalculation.Key(
            RHEL, ServiceLevel.PREMIUM, Usage._ANY, BillingProvider.RED_HAT, "_ANY");
    assertTrue(accountUsageCalculation.containsCalculation(engIdKey));
    assertEquals(
        Double.valueOf(42.0),
        accountUsageCalculation
            .getCalculation(engIdKey)
            .getTotals(HardwareMeasurementType.PHYSICAL)
            .getMeasurement(Measurement.Uom.CORES));

    // Not defined on the event, should not exist.
    List.of(RHEL_SERVER_SWATCH_PRODUCT_ID, RHEL_WORKSTATION_SWATCH_PRODUCT_ID)
        .forEach(
            swatchProdId -> {
              UsageCalculation.Key key =
                  new UsageCalculation.Key(
                      swatchProdId,
                      ServiceLevel.PREMIUM,
                      Usage._ANY,
                      BillingProvider._ANY,
                      "sellerAcctId");
              assertFalse(
                  accountUsageCalculation.containsCalculation(key),
                  "Unexpected calculation: " + swatchProdId);
            });
  }

  @EnumSource
  @ParameterizedTest
  void testHandlesDuplicateEvents(Measurement.Uom uom) {
    Measurement measurement = new Measurement().withUom(uom).withValue(42.0);
    Event event =
        new Event()
            .withEventId(UUID.randomUUID())
            .withProductIds(List.of("1234"))
            .withTimestamp(OffsetDateTime.parse("2021-02-26T00:00:00Z"))
            .withServiceType(SERVICE_TYPE)
            .withInstanceId(UUID.randomUUID().toString())
            .withMeasurements(Collections.singletonList(measurement))
            .withUsage(Event.Usage.PRODUCTION)
            .withBillingProvider(Event.BillingProvider.RED_HAT)
            .withBillingAccountId(Optional.of("sellerAcctId"));
    AccountServiceInventory accountServiceInventory = createTestAccountServiceInventory();
    when(eventController.fetchEventsInTimeRangeByServiceType(any(), any(), any(), any()))
        .thenReturn(Stream.of(event, event));
    AccountUsageCalculation accountUsageCalculation =
        metricUsageCollector.collectHour(accountServiceInventory, OffsetDateTime.MIN);
    assertNotNull(accountUsageCalculation);
    UsageCalculation.Key usageCalculationKey =
        new UsageCalculation.Key(
            RHEL, ServiceLevel.PREMIUM, Usage.PRODUCTION, BillingProvider.RED_HAT, "sellerAcctId");
    assertTrue(accountUsageCalculation.containsCalculation(usageCalculationKey));
    assertEquals(
        Double.valueOf(42.0),
        accountUsageCalculation
            .getCalculation(usageCalculationKey)
            .getTotals(HardwareMeasurementType.PHYSICAL)
            .getMeasurement(uom));
  }

  @Test
  void testUpdatesMonthlyTotal() {
    Measurement measurement = new Measurement().withUom(Measurement.Uom.CORES).withValue(42.0);
    String instanceId = UUID.randomUUID().toString();
    Event event =
        new Event()
            .withEventId(UUID.randomUUID())
            .withTimestamp(OffsetDateTime.parse("2021-02-26T00:00:00Z"))
            .withServiceType(SERVICE_TYPE)
            .withInstanceId(instanceId)
            .withMeasurements(Collections.singletonList(measurement))
            .withUsage(Event.Usage.PRODUCTION);

    Measurement instanceHoursMeasurement =
        new Measurement().withUom(Uom.INSTANCE_HOURS).withValue(43.0);
    Event instanceHoursEvent =
        new Event()
            .withEventId(UUID.randomUUID())
            .withTimestamp(OffsetDateTime.parse("2021-02-26T00:00:00Z"))
            .withServiceType(SERVICE_TYPE)
            .withInstanceId(instanceId)
            .withMeasurements(Collections.singletonList(instanceHoursMeasurement))
            .withUsage(Event.Usage.PRODUCTION);
    AccountServiceInventory accountServiceInventory = createTestAccountServiceInventory();
    when(eventController.fetchEventsInTimeRangeByServiceType(any(), any(), any(), any()))
        .thenReturn(Stream.of(event, event, instanceHoursEvent, instanceHoursEvent));

    metricUsageCollector.collectHour(accountServiceInventory, OffsetDateTime.MIN);
    Host instance =
        accountServiceInventory.getServiceInstances().values().stream().findFirst().orElseThrow();
    assertEquals(Double.valueOf(84.0), instance.getMonthlyTotal("2021-02", Measurement.Uom.CORES));
    assertEquals(
        Double.valueOf(86.0), instance.getMonthlyTotal("2021-02", Measurement.Uom.INSTANCE_HOURS));
  }

  @Test
  void testRecalculatesMonthlyTotalWhenEventsAreOld() {
    Measurement measurement = new Measurement().withUom(Measurement.Uom.CORES).withValue(42.0);
    String instanceId = UUID.randomUUID().toString();
    OffsetDateTime eventDate = clock.startOfCurrentHour();
    Event event =
        new Event()
            .withEventId(UUID.randomUUID())
            .withTimestamp(eventDate)
            .withServiceType(SERVICE_TYPE)
            .withInstanceId(instanceId)
            .withMeasurements(Collections.singletonList(measurement))
            .withUsage(Event.Usage.PRODUCTION);
    AccountServiceInventory accountServiceInventory = createTestAccountServiceInventory();

    OffsetDateTime instanceDate = eventDate.minusDays(1);
    Host activeInstance = new Host();
    activeInstance.setInstanceId(instanceId);
    activeInstance.setInstanceType(SERVICE_TYPE);
    activeInstance.setLastSeen(instanceDate);
    accountServiceInventory.getServiceInstances().put(instanceId, activeInstance);

    String monthId = InstanceMonthlyTotalKey.formatMonthId(instanceDate);
    when(accountRepo.findById(any())).thenReturn(Optional.of(accountServiceInventory));
    when(eventController.fetchEventsInTimeRangeByServiceType(any(), any(), any(), any()))
        .thenAnswer(
            m -> {
              OffsetDateTime begin = m.getArgument(2, OffsetDateTime.class);
              OffsetDateTime end = m.getArgument(3, OffsetDateTime.class);
              if (begin.equals(eventDate) && end.equals(eventDate.plusHours(1))) {
                return Stream.of(event);
              }
              return Stream.of();
            });
    when(eventController.hasEventsInTimeRange(any(), any(), any(), any())).thenReturn(true);

    metricUsageCollector.collect(
        SERVICE_TYPE,
        "account123",
        "org123",
        new DateRange(instanceDate.minusHours(1), instanceDate.plusHours(1)));
    assertEquals(
        Double.valueOf(42.0), activeInstance.getMonthlyTotal(monthId, Measurement.Uom.CORES));
  }

  @Test
  void testClearsMeasurementsOnInactiveInstancesWhenRecalculating() {
    Measurement measurement = new Measurement().withUom(Measurement.Uom.CORES).withValue(42.0);
    String instanceId = UUID.randomUUID().toString();
    OffsetDateTime eventDate = clock.startOfCurrentHour();
    Event event =
        new Event()
            .withEventId(UUID.randomUUID())
            .withTimestamp(eventDate)
            .withServiceType(SERVICE_TYPE)
            .withInstanceId(instanceId)
            .withMeasurements(Collections.singletonList(measurement))
            .withUsage(Event.Usage.PRODUCTION);
    AccountServiceInventory accountServiceInventory = createTestAccountServiceInventory();

    OffsetDateTime instanceDate = eventDate.minusDays(1);
    Host activeInstance = new Host();
    activeInstance.setInstanceId(instanceId);
    activeInstance.setInstanceType(SERVICE_TYPE);
    activeInstance.setLastSeen(instanceDate);
    accountServiceInventory.getServiceInstances().put(instanceId, activeInstance);

    String monthId = InstanceMonthlyTotalKey.formatMonthId(instanceDate);
    Host staleInstance = new Host();
    staleInstance.addToMonthlyTotal(monthId, Measurement.Uom.CORES, 11.0);
    staleInstance.setInstanceType(SERVICE_TYPE);
    staleInstance.setInstanceId(UUID.randomUUID().toString());
    staleInstance.setLastSeen(instanceDate);
    accountServiceInventory.getServiceInstances().put(staleInstance.getInstanceId(), staleInstance);

    when(accountRepo.findById(any())).thenReturn(Optional.of(accountServiceInventory));
    when(eventController.fetchEventsInTimeRangeByServiceType(any(), any(), any(), any()))
        .thenAnswer(
            m -> {
              OffsetDateTime begin = m.getArgument(2, OffsetDateTime.class);
              OffsetDateTime end = m.getArgument(3, OffsetDateTime.class);
              if (begin.equals(eventDate) && end.equals(eventDate.plusHours(1))) {
                return Stream.of(event);
              }
              return Stream.of();
            });
    when(eventController.hasEventsInTimeRange(any(), any(), any(), any())).thenReturn(true);

    metricUsageCollector.collect(
        SERVICE_TYPE,
        "account123",
        "org123",
        new DateRange(instanceDate.minusHours(1), instanceDate.plusHours(1)));
    assertEquals(
        Double.valueOf(42.0), activeInstance.getMonthlyTotal(monthId, Measurement.Uom.CORES));
    assertEquals(0.0, staleInstance.getMonthlyTotal(monthId, Measurement.Uom.CORES));
  }

  @Test
  void testRecalculatesWhenEventLastSeenEqualToRangeStart() {
    Measurement measurement = new Measurement().withUom(Measurement.Uom.CORES).withValue(42.0);
    String instanceId = UUID.randomUUID().toString();
    OffsetDateTime eventDate = clock.startOfCurrentHour();
    Event event =
        new Event()
            .withEventId(UUID.randomUUID())
            .withTimestamp(eventDate)
            .withServiceType(SERVICE_TYPE)
            .withInstanceId(instanceId)
            .withMeasurements(Collections.singletonList(measurement))
            .withUsage(Event.Usage.PRODUCTION);

    AccountServiceInventory accountServiceInventory = createTestAccountServiceInventory();

    String monthId = InstanceMonthlyTotalKey.formatMonthId(eventDate);
    Host activeInstance = new Host();
    activeInstance.setInstanceId(instanceId);
    activeInstance.setInstanceType(SERVICE_TYPE);
    activeInstance.addToMonthlyTotal(monthId, Measurement.Uom.CORES, 11.0);
    activeInstance.setLastSeen(eventDate);
    accountServiceInventory.getServiceInstances().put(instanceId, activeInstance);

    when(accountRepo.findById(any())).thenReturn(Optional.of(accountServiceInventory));

    when(eventController.fetchEventsInTimeRangeByServiceType(any(), any(), any(), any()))
        .thenAnswer(
            m -> {
              OffsetDateTime begin = m.getArgument(2, OffsetDateTime.class);
              OffsetDateTime end = m.getArgument(3, OffsetDateTime.class);
              if (begin.equals(eventDate) && end.equals(eventDate.plusHours(1))) {
                return Stream.of(event);
              }
              return Stream.of();
            });

    when(eventController.hasEventsInTimeRange(any(), any(), any(), any())).thenReturn(true);

    metricUsageCollector.collect(
        SERVICE_TYPE, "account123", "org123", new DateRange(eventDate, eventDate.plusHours(1)));
    assertEquals(
        Double.valueOf(42.0), activeInstance.getMonthlyTotal(monthId, Measurement.Uom.CORES));
  }

  @Test
  void collectionThrowsExceptionWhenDateRangeIsNotRounded() {
    DateRange range = new DateRange(clock.startOfCurrentHour(), clock.now());
    assertThrows(
        IllegalArgumentException.class,
        () -> metricUsageCollector.collect(SERVICE_TYPE, "account123", "org123", range));
  }

  @Test
  void collectHourClearsAllMeasurementsForInstanceBeforeApplyingEvents() {
    String accountNumber = "account123";
    String instanceId = UUID.randomUUID().toString();
    OffsetDateTime eventDate = clock.startOfCurrentHour();
    double expectedCoresMeasurement = 150.0;

    AccountServiceInventory accountServiceInventory = createTestAccountServiceInventory();

    OffsetDateTime instanceDate = eventDate.minusDays(1);
    Host activeInstance = new Host();
    activeInstance.setInstanceId(instanceId);
    activeInstance.setInstanceType(SERVICE_TYPE);
    activeInstance.setLastSeen(instanceDate);
    activeInstance.setMeasurement(Uom.CORES, 122.5);
    activeInstance.setMeasurement(Uom.INSTANCE_HOURS, 50.0);
    accountServiceInventory.getServiceInstances().put(instanceId, activeInstance);

    Measurement coresMeasurement =
        new Measurement().withUom(Uom.CORES).withValue(expectedCoresMeasurement);
    Event coresEvent =
        new Event()
            .withEventId(UUID.randomUUID())
            .withTimestamp(eventDate)
            .withServiceType(SERVICE_TYPE)
            .withInstanceId(instanceId)
            .withMeasurements(Collections.singletonList(coresMeasurement))
            .withUsage(Event.Usage.PRODUCTION);

    when(eventController.fetchEventsInTimeRangeByServiceType(any(), any(), any(), any()))
        .thenReturn(Stream.of(coresEvent));

    metricUsageCollector.collectHour(accountServiceInventory, eventDate);
    // Cores measurement should be present and updated to the new expected value from the event.
    assertEquals(
        Double.valueOf(expectedCoresMeasurement), activeInstance.getMeasurement(Uom.CORES));
    // Instance hours measurement should no longer be present since it was not reported by an event.
    assertNull(activeInstance.getMeasurement(Uom.INSTANCE_HOURS));
  }

  @Test
  void testAccountRepoNotTouchedIfNoEventsExist() {
    when(eventController.hasEventsInTimeRange(any(), any(), any(), any())).thenReturn(false);
    metricUsageCollector.collect(
        SERVICE_TYPE,
        "account123",
        "org123",
        new DateRange(
            clock.startOfCurrentHour().minusHours(1), clock.startOfCurrentHour().plusHours(1)));
    Mockito.verifyNoInteractions(accountRepo);
  }

  @Test
  void testEventWithNullFieldsProcessed() {
    // NOTE: null in the JSON gets represented as Optional.empty()
    Measurement measurement = new Measurement().withUom(Measurement.Uom.CORES).withValue(42.0);
    Event event =
        new Event()
            .withEventId(UUID.randomUUID())
            .withTimestamp(OffsetDateTime.parse("2021-02-26T00:00:00Z"))
            .withServiceType(SERVICE_TYPE)
            .withInstanceId(UUID.randomUUID().toString())
            .withBillingAccountId(Optional.empty())
            .withDisplayName(Optional.empty())
            .withHypervisorUuid(Optional.empty())
            .withInsightsId(Optional.empty())
            .withInventoryId(Optional.empty())
            .withSubscriptionManagerId(Optional.empty());
    AccountServiceInventory accountServiceInventory = createTestAccountServiceInventory();
    when(eventController.fetchEventsInTimeRangeByServiceType(any(), any(), any(), any()))
        .thenReturn(Stream.of(event));

    metricUsageCollector.collectHour(accountServiceInventory, OffsetDateTime.MIN);
    Host instance = accountServiceInventory.getServiceInstances().get(event.getInstanceId());
    assertNotNull(instance);
  }

  @Test
  void testCreateInstanceDefaultBillingProvider() {
    Measurement measurement = new Measurement().withUom(Measurement.Uom.CORES).withValue(42.0);
    Event event =
        new Event()
            .withEventId(UUID.randomUUID())
            .withTimestamp(OffsetDateTime.parse("2021-02-26T00:00:00Z"))
            .withServiceType(SERVICE_TYPE)
            .withInstanceId(UUID.randomUUID().toString())
            .withMeasurements(Collections.singletonList(measurement))
            .withBillingAccountId(Optional.of("sellerAcct"));
    AccountServiceInventory accountServiceInventory = createTestAccountServiceInventory();
    when(eventController.fetchEventsInTimeRangeByServiceType(any(), any(), any(), any()))
        .thenReturn(Stream.of(event));

    metricUsageCollector.collectHour(accountServiceInventory, OffsetDateTime.MIN);
    Host instance = accountServiceInventory.getServiceInstances().get(event.getInstanceId());
    assertNotNull(instance);
    assertEquals(BillingProvider.RED_HAT, instance.getBillingProvider());
  }

  @Test
  void testInstanceHasOrgIdSet() {
    Measurement measurement = new Measurement().withUom(Measurement.Uom.CORES).withValue(42.0);
    Event event =
        new Event()
            .withEventId(UUID.randomUUID())
            .withAccountNumber("account123")
            .withOrgId("test-org")
            .withTimestamp(OffsetDateTime.parse("2021-02-26T00:00:00Z"))
            .withServiceType(SERVICE_TYPE)
            .withInstanceId(UUID.randomUUID().toString())
            .withMeasurements(Collections.singletonList(measurement));

    AccountServiceInventory accountServiceInventory = createTestAccountServiceInventory();
    when(eventController.fetchEventsInTimeRangeByServiceType(any(), any(), any(), any()))
        .thenReturn(Stream.of(event));

    metricUsageCollector.collectHour(accountServiceInventory, OffsetDateTime.MIN);
    Host instance = accountServiceInventory.getServiceInstances().get(event.getInstanceId());
    assertNotNull(instance);
    assertEquals("test-org", instance.getOrgId());
  }
}
