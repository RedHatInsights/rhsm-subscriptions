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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.Sets;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.db.AccountServiceInventoryRepository;
import org.candlepin.subscriptions.db.HostRepository;
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.*;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Event.CloudProvider;
import org.candlepin.subscriptions.json.Event.HardwareType;
import org.candlepin.subscriptions.json.Event.Role;
import org.candlepin.subscriptions.json.Measurement;
import org.candlepin.subscriptions.test.TestClockConfiguration;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MetricUsageCollectorTest {

  public static final String RHEL_ENG_ID = "69";
  public static final String RHEL_ELS_PAYG_ENG_ID = "204";
  MetricUsageCollector metricUsageCollector;

  @Mock AccountServiceInventoryRepository accountRepo;

  @Mock HostRepository hostRepository;

  @Mock TallySnapshotRepository tallySnapshotRepository;

  ApplicationClock clock = new TestClockConfiguration().adjustableClock();

  static final String ORG_ID = "orgId";
  static final String SERVICE_TYPE = "OpenShift Cluster";
  static final String RHEL_FOR_X86 = "RHEL for x86";
  static final String RHEL_FOR_X86_ELS_PAYG = "rhel-for-x86-els-payg";

  static final String RHEL_WORKSTATION_SWATCH_PRODUCT_ID = "RHEL Workstation";
  static final String RHEL_COMPUTE_NODE_SWATCH_PRODUCT_ID = "RHEL Compute Node";
  static final String OSD_PRODUCT_TAG = "OpenShift-dedicated-metrics";
  static final String OCP_PRODUCT_TAG = "OpenShift-metrics";

  static final String OSD_METRIC_ID = "redhat.com:openshift_dedicated:4cpu_hour";

  @BeforeEach
  void setup() {
    metricUsageCollector =
        new MetricUsageCollector(accountRepo, clock, hostRepository, tallySnapshotRepository);
  }

  @Test
  void updateHosts_noIteractionsWhenNoEventsFound() {
    metricUsageCollector.updateHosts(ORG_ID, SERVICE_TYPE, List.of());
    verifyNoInteractions(accountRepo, hostRepository, tallySnapshotRepository);
  }

  @Test
  void testUpdateHostsCreatesAccountServiceInventoryWhenItDoesNotExist() {
    Measurement measurement =
        new Measurement().withUom(MetricIdUtils.getCores().toString()).withValue(42.0);
    Event event =
        createEvent()
            .withEventId(UUID.randomUUID())
            .withTimestamp(OffsetDateTime.parse("2021-02-26T00:00:00Z"))
            .withOrgId(ORG_ID)
            .withServiceType(SERVICE_TYPE)
            .withMeasurements(Collections.singletonList(measurement))
            .withBillingProvider(Event.BillingProvider.RED_HAT)
            .withBillingAccountId(Optional.of("sellerAcct"));
    AccountServiceInventory accountServiceInventory = createTestAccountServiceInventory();
    when(accountRepo.existsById(accountServiceInventory.getId())).thenReturn(false);

    metricUsageCollector.updateHosts(ORG_ID, SERVICE_TYPE, List.of(event));
    ArgumentCaptor<AccountServiceInventory> captor =
        ArgumentCaptor.forClass(AccountServiceInventory.class);
    verify(accountRepo, times(1)).save(captor.capture());

    AccountServiceInventory inventory = captor.getValue();
    assertNotNull(inventory);
    // NOTE The inventory instance will not have Hosts associated with it after the updateHosts call
    //      since we use the Host repository directly to persist the Hosts (avoiding the need to
    //      load all hosts into memory via the AccountServiceInventory.
    assertEquals(ORG_ID, inventory.getOrgId());
    assertEquals(SERVICE_TYPE, inventory.getServiceType());
  }

  @Test
  void testUpdateHostsCreatesNewInstanceRecords() {
    Measurement measurement =
        new Measurement().withUom(MetricIdUtils.getCores().toString()).withValue(42.0);
    Event event =
        createEvent()
            .withEventId(UUID.randomUUID())
            .withTimestamp(OffsetDateTime.parse("2021-02-26T00:00:00Z"))
            .withOrgId(ORG_ID)
            .withServiceType(SERVICE_TYPE)
            .withMeasurements(Collections.singletonList(measurement))
            .withBillingProvider(Event.BillingProvider.RED_HAT)
            .withBillingAccountId(Optional.of("sellerAcct"));
    AccountServiceInventory accountServiceInventory = createTestAccountServiceInventory();
    when(accountRepo.existsById(accountServiceInventory.getId())).thenReturn(true);

    metricUsageCollector.updateHosts(ORG_ID, SERVICE_TYPE, List.of(event));
    verify(hostRepository, times(1)).save(any());
  }

  @Test
  void updateHostsOnlyUpdatesLastSeenAndMeasurementsWhenEventTimestampMostRecent() {
    Measurement coresMeasurement =
        new Measurement().withUom(MetricIdUtils.getCores().toString()).withValue(42.0);
    Event event1 =
        createEvent()
            .withEventId(UUID.randomUUID())
            .withProductIds(List.of(RHEL_FOR_X86))
            .withTimestamp(OffsetDateTime.parse("2021-02-26T00:00:00Z"))
            .withServiceType("RHEL System")
            .withMeasurements(List.of(coresMeasurement))
            .withSla(Event.Sla.PREMIUM)
            .withBillingProvider(Event.BillingProvider.RED_HAT)
            .withBillingAccountId(Optional.of("sellerAcctId"));

    Measurement oldCoresMeasurement =
        new Measurement().withUom(MetricIdUtils.getCores().toString()).withValue(100.0);
    Event event2 =
        createEvent(event1.getInstanceId())
            .withEventId(UUID.randomUUID())
            .withProductIds(List.of(RHEL_FOR_X86))
            .withTimestamp(event1.getTimestamp().minusMonths(1))
            .withServiceType("RHEL System")
            .withMeasurements(List.of(oldCoresMeasurement))
            .withSla(Event.Sla.PREMIUM)
            .withBillingProvider(Event.BillingProvider.RED_HAT)
            .withBillingAccountId(Optional.of("sellerAcctId"));

    Measurement instanceHoursMeasurement =
        new Measurement().withUom(MetricIdUtils.getInstanceHours().toString()).withValue(5.0);
    Event event3 =
        createEvent(event1.getInstanceId())
            .withEventId(UUID.randomUUID())
            .withProductIds(List.of(RHEL_FOR_X86))
            .withTimestamp(event1.getTimestamp())
            .withServiceType("RHEL System")
            .withMeasurements(List.of(instanceHoursMeasurement))
            .withSla(Event.Sla.PREMIUM)
            .withBillingProvider(Event.BillingProvider.RED_HAT)
            .withBillingAccountId(Optional.of("sellerAcctId"));

    OffsetDateTime instanceDate = event1.getTimestamp().minusDays(1);
    Host activeInstance = new Host();
    activeInstance.setInstanceId(event1.getInstanceId());
    activeInstance.setInstanceType(SERVICE_TYPE);
    activeInstance.setLastSeen(instanceDate);

    doAnswer(invocation -> Stream.of(activeInstance))
        .when(hostRepository)
        .findAllByOrgIdAndInstanceIdIn(ORG_ID, Set.of(event1.getInstanceId()));

    // First update should change the date.
    metricUsageCollector.updateHosts(ORG_ID, SERVICE_TYPE, List.of(event1));
    assertEquals(event1.getTimestamp(), activeInstance.getLastSeen());
    assertTrue(activeInstance.getMeasurements().containsKey("CORES"));
    assertEquals(coresMeasurement.getValue(), activeInstance.getMeasurement("CORES"));

    // Second update should have the Event applied, but the lastSeen date should
    // not change since this event represents older usage.
    metricUsageCollector.updateHosts(ORG_ID, SERVICE_TYPE, List.of(event2));
    assertEquals(event1.getTimestamp(), activeInstance.getLastSeen());
    assertTrue(activeInstance.getMeasurements().containsKey("CORES"));
    // Should remain the same as the first event.
    assertEquals(coresMeasurement.getValue(), activeInstance.getMeasurement("CORES"));

    // Third update should have the third event applied because it's the same timestamp, but
    // includes
    // a different measurement.
    metricUsageCollector.updateHosts(ORG_ID, SERVICE_TYPE, List.of(event3));
    assertEquals(event1.getTimestamp(), activeInstance.getLastSeen());
    assertEquals(coresMeasurement.getValue(), activeInstance.getMeasurement("CORES"));
    assertEquals(
        instanceHoursMeasurement.getValue(), activeInstance.getMeasurement("INSTANCE_HOURS"));
  }

  @Test
  void testPopulatesUsageCalculationsWithProductTag() {
    Event event = createEvent().withProductTag(Set.of(OSD_PRODUCT_TAG));
    assertUsageCalculationForEvent(event);
  }

  @Test
  void testPopulatesUsageCalculationsWithoutProductTag() {
    Event event = createEvent().withProductTag(null);
    assertUsageCalculationForEvent(event);
  }

  static Stream<Arguments> hardwareTypeParams() {
    return Stream.of(
        Arguments.of(HardwareType.PHYSICAL, HardwareMeasurementType.PHYSICAL),
        Arguments.of(HardwareType.VIRTUAL, HardwareMeasurementType.VIRTUAL),
        Arguments.of(HardwareType.CLOUD, HardwareMeasurementType.AWS));
  }

  @ParameterizedTest
  @MethodSource("hardwareTypeParams")
  void testCollectHandlesAllHardwareTypes(
      Event.HardwareType hardwareType, HardwareMeasurementType expectedType) {
    Measurement measurement =
        new Measurement().withUom(MetricIdUtils.getCores().toString()).withValue(42.0);
    Event event =
        createEvent()
            .withEventId(UUID.randomUUID())
            .withTimestamp(OffsetDateTime.parse("2021-02-26T00:00:00Z"))
            .withServiceType(SERVICE_TYPE)
            .withRole(Event.Role.OSD)
            .withHardwareType(hardwareType)
            .withCloudProvider(Event.CloudProvider.__EMPTY__)
            .withBillingProvider(Event.BillingProvider.RED_HAT)
            .withBillingAccountId(Optional.of("sellerAcct"))
            .withMeasurements(List.of(measurement));

    if (hardwareType.equals(HardwareType.CLOUD)) {
      event.withCloudProvider(CloudProvider.AWS);
    }

    AccountUsageCalculationCache cache = new AccountUsageCalculationCache();
    metricUsageCollector.calculateUsage(List.of(event), cache);

    assertEquals(1, cache.getCalculations().size());
    assertTrue(cache.contains(event));

    AccountUsageCalculation accountUsageCalculation = cache.get(event);

    UsageCalculation.Key usageCalculationKey =
        new UsageCalculation.Key(
            OSD_PRODUCT_TAG,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider.RED_HAT,
            "sellerAcct");

    assertEquals(
        Double.valueOf(42.0),
        accountUsageCalculation
            .getCalculation(usageCalculationKey)
            .getTotals(expectedType)
            .getMeasurement(MetricIdUtils.getCores()));
  }

  @NotNull
  private AccountServiceInventory createTestAccountServiceInventory() {
    AccountServiceInventory accountServiceInventory = new AccountServiceInventory();
    accountServiceInventory.setAccountNumber("account123");
    accountServiceInventory.setOrgId("orgId");
    accountServiceInventory.setServiceType(SERVICE_TYPE);
    return accountServiceInventory;
  }

  static Stream<Arguments> cloudProviderParams() {
    List<Arguments> args =
        List.of(
            Arguments.of(CloudProvider.__EMPTY__, HardwareMeasurementType.PHYSICAL),
            Arguments.of(CloudProvider.AWS, HardwareMeasurementType.AWS),
            Arguments.of(CloudProvider.AZURE, HardwareMeasurementType.AZURE),
            Arguments.of(CloudProvider.ALIBABA, HardwareMeasurementType.ALIBABA),
            Arguments.of(CloudProvider.GOOGLE, HardwareMeasurementType.GOOGLE));
    List<CloudProvider> underTest = args.stream().map(arg -> (CloudProvider) arg.get()[0]).toList();
    assertTrue(underTest.containsAll(List.of(CloudProvider.values())));
    return args.stream();
  }

  @ParameterizedTest
  @MethodSource("cloudProviderParams")
  void testCalculateUsageHandlesAllCloudProviders(
      Event.CloudProvider cloudProvider, HardwareMeasurementType expectedMeasurementType) {
    Measurement measurement =
        new Measurement().withUom(MetricIdUtils.getCores().toString()).withValue(42.0);

    // If CloudProvider is __EMPTY__ the hardware type can not be CLOUD.
    HardwareType hardwareType =
        CloudProvider.__EMPTY__.equals(cloudProvider) ? HardwareType.PHYSICAL : HardwareType.CLOUD;

    Event event =
        createEvent()
            .withEventId(UUID.randomUUID())
            .withTimestamp(OffsetDateTime.parse("2021-02-26T00:00:00Z"))
            .withServiceType(SERVICE_TYPE)
            .withRole(Event.Role.OSD)
            .withHardwareType(hardwareType)
            .withCloudProvider(cloudProvider)
            .withBillingProvider(Event.BillingProvider.RED_HAT)
            .withBillingAccountId(Optional.of("sellerAcct"))
            .withMeasurements(List.of(measurement));

    AccountUsageCalculationCache cache = new AccountUsageCalculationCache();
    metricUsageCollector.calculateUsage(List.of(event), cache);

    assertEquals(1, cache.getCalculations().size());
    assertTrue(cache.contains(event));

    AccountUsageCalculation accountUsageCalculation = cache.get(event);

    UsageCalculation.Key usageCalculationKey =
        new UsageCalculation.Key(
            OSD_PRODUCT_TAG,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider.RED_HAT,
            "sellerAcct");

    assertEquals(
        Double.valueOf(42.0),
        accountUsageCalculation
            .getCalculation(usageCalculationKey)
            .getTotals(expectedMeasurementType)
            .getMeasurement(MetricIdUtils.getCores()));
  }

  @Test
  void testUpdateHostsAddsBucketsForApplicableUsageKeys() {
    Measurement measurement =
        new Measurement().withUom(MetricIdUtils.getCores().toString()).withValue(42.0);
    Event event =
        createEvent()
            .withEventId(UUID.randomUUID())
            .withProductIds(List.of(RHEL_ENG_ID, RHEL_ELS_PAYG_ENG_ID))
            .withProductTag(Set.of(RHEL_FOR_X86_ELS_PAYG))
            .withTimestamp(OffsetDateTime.parse("2021-02-26T00:00:00Z"))
            .withServiceType("RHEL System")
            .withMeasurements(Collections.singletonList(measurement))
            .withSla(Event.Sla.PREMIUM)
            .withBillingProvider(Event.BillingProvider.RED_HAT)
            .withBillingAccountId(Optional.of("sellerAcctId"));

    metricUsageCollector.updateHosts(ORG_ID, SERVICE_TYPE, List.of(event));

    ArgumentCaptor<Host> saveHostCaptor = ArgumentCaptor.forClass(Host.class);
    verify(hostRepository).save(saveHostCaptor.capture());

    Host instance = saveHostCaptor.getValue();

    Set<HostTallyBucket> expected = new HashSet<>();
    var usages = Set.of(Usage._ANY, Usage.PRODUCTION);
    var slas = Set.of(ServiceLevel._ANY, ServiceLevel.PREMIUM);
    var billingProviders = Set.of(BillingProvider._ANY, BillingProvider.RED_HAT);
    var billingAccountIds = Set.of("sellerAcctId", "_ANY");
    var tuples = Sets.cartesianProduct(usages, slas, billingProviders, billingAccountIds);

    tuples.forEach(
        tuple -> {
          Usage usage = (Usage) tuple.get(0);
          ServiceLevel sla = (ServiceLevel) tuple.get(1);
          BillingProvider billingProvider = (BillingProvider) tuple.get(2);
          String billingAccountId = (String) tuple.get(3);

          HostBucketKey key = new HostBucketKey();
          key.setProductId(RHEL_FOR_X86_ELS_PAYG);
          key.setSla(sla);
          key.setBillingProvider(billingProvider);
          key.setBillingAccountId(billingAccountId);
          key.setUsage(usage);
          key.setAsHypervisor(false);
          HostTallyBucket bucket = new HostTallyBucket();
          bucket.setKey(key);
          bucket.setCores(measurement.getValue().intValue());
          bucket.setHost(instance);
          expected.add(bucket);
        });
    assertEquals(expected, new HashSet<>(instance.getBuckets()));
  }

  @Test
  void testCalculateUsageAddsAnySlaToBuckets() {
    Measurement measurement =
        new Measurement().withUom(MetricIdUtils.getCores().toString()).withValue(42.0);
    Event event =
        createEvent()
            .withEventId(UUID.randomUUID())
            .withRole(Event.Role.OSD)
            .withProductTag(Set.of(OSD_PRODUCT_TAG))
            .withTimestamp(OffsetDateTime.parse("2021-02-26T00:00:00Z"))
            .withServiceType(SERVICE_TYPE)
            .withMeasurements(Collections.singletonList(measurement))
            .withSla(Event.Sla.PREMIUM)
            .withBillingProvider(Event.BillingProvider.RED_HAT)
            .withBillingAccountId(Optional.of("sellerAcctId"));

    AccountUsageCalculationCache cache = new AccountUsageCalculationCache();
    metricUsageCollector.calculateUsage(List.of(event), cache);

    assertEquals(1, cache.getCalculations().size());
    assertTrue(cache.contains(event));

    AccountUsageCalculation accountUsageCalculation = cache.get(event);

    assertNotNull(accountUsageCalculation);
    UsageCalculation.Key usageCalculationKey =
        new UsageCalculation.Key(
            OSD_PRODUCT_TAG,
            ServiceLevel._ANY,
            Usage.PRODUCTION,
            BillingProvider.RED_HAT,
            "sellerAcctId");
    assertTrue(accountUsageCalculation.containsCalculation(usageCalculationKey));
    assertEquals(
        Double.valueOf(42.0),
        accountUsageCalculation
            .getCalculation(usageCalculationKey)
            .getTotals(HardwareMeasurementType.PHYSICAL)
            .getMeasurement(MetricIdUtils.getCores()));
  }

  @Test
  void testCalculateUsageAddsAnyUsageToBuckets() {
    Measurement measurement =
        new Measurement().withUom(MetricIdUtils.getCores().toString()).withValue(42.0);
    Event event =
        createEvent()
            .withEventId(UUID.randomUUID())
            .withRole(Event.Role.OSD)
            .withProductTag(Set.of(OSD_PRODUCT_TAG))
            .withTimestamp(OffsetDateTime.parse("2021-02-26T00:00:00Z"))
            .withServiceType(SERVICE_TYPE)
            .withMeasurements(Collections.singletonList(measurement))
            .withUsage(Event.Usage.PRODUCTION)
            .withBillingProvider(Event.BillingProvider.RED_HAT)
            .withBillingAccountId(Optional.of("sellerAcctId"));

    AccountUsageCalculationCache cache = new AccountUsageCalculationCache();
    metricUsageCollector.calculateUsage(List.of(event), cache);

    assertEquals(1, cache.getCalculations().size());
    assertTrue(cache.contains(event));

    AccountUsageCalculation accountUsageCalculation = cache.get(event);

    assertNotNull(accountUsageCalculation);
    UsageCalculation.Key usageCalculationKey =
        new UsageCalculation.Key(
            OSD_PRODUCT_TAG,
            ServiceLevel.PREMIUM,
            Usage._ANY,
            BillingProvider.RED_HAT,
            "sellerAcctId");
    assertTrue(accountUsageCalculation.containsCalculation(usageCalculationKey));
    assertEquals(
        Double.valueOf(42.0),
        accountUsageCalculation
            .getCalculation(usageCalculationKey)
            .getTotals(HardwareMeasurementType.PHYSICAL)
            .getMeasurement(MetricIdUtils.getCores()));
  }

  @Test
  void productsDefinedInRolesAreIncludedInBucketsWhenSetOnEventWhileCalculating() {
    Measurement measurement =
        new Measurement().withUom(MetricIdUtils.getCores().toString()).withValue(42.0);
    Event event =
        createEvent()
            .withEventId(UUID.randomUUID())
            .withTimestamp(OffsetDateTime.parse("2021-02-26T00:00:00Z"))
            .withServiceType(SERVICE_TYPE)
            .withMeasurements(Collections.singletonList(measurement))
            .withUsage(Event.Usage.PRODUCTION)
            .withBillingProvider(Event.BillingProvider.RED_HAT)
            .withRole(Role.OSD)
            .withEventType("snapshot_" + OSD_METRIC_ID);

    AccountUsageCalculationCache cache = new AccountUsageCalculationCache();
    metricUsageCollector.calculateUsage(List.of(event), cache);

    assertEquals(1, cache.getCalculations().size());
    assertTrue(cache.contains(event));

    AccountUsageCalculation accountUsageCalculation = cache.get(event);
    assertNotNull(accountUsageCalculation);

    UsageCalculation.Key serverKey =
        new UsageCalculation.Key(
            OSD_PRODUCT_TAG, ServiceLevel.PREMIUM, Usage._ANY, BillingProvider.RED_HAT, "_ANY");
    assertTrue(accountUsageCalculation.containsCalculation(serverKey));
    assertEquals(
        Double.valueOf(42.0),
        accountUsageCalculation
            .getCalculation(serverKey)
            .getTotals(HardwareMeasurementType.PHYSICAL)
            .getMeasurement(MetricIdUtils.getCores()));

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
  void productsAreIncludedInBucketsWhenEngIdIsSetOnEventWhileCalculating() {
    Measurement measurement =
        new Measurement().withUom(MetricIdUtils.getCores().toString()).withValue(42.0);
    Event event =
        createEvent()
            .withEventId(UUID.randomUUID())
            .withServiceType(SERVICE_TYPE)
            .withTimestamp(OffsetDateTime.parse("2021-02-26T00:00:00Z"))
            .withMeasurements(Collections.singletonList(measurement))
            .withUsage(Event.Usage.PRODUCTION)
            .withBillingProvider(Event.BillingProvider.RED_HAT)
            .withProductIds(List.of(RHEL_ENG_ID, RHEL_ELS_PAYG_ENG_ID))
            .withProductTag(Set.of(RHEL_FOR_X86_ELS_PAYG));

    AccountUsageCalculationCache cache = new AccountUsageCalculationCache();
    metricUsageCollector.calculateUsage(List.of(event), cache);

    assertEquals(1, cache.getCalculations().size());
    assertTrue(cache.contains(event));

    AccountUsageCalculation accountUsageCalculation = cache.get(event);
    assertNotNull(accountUsageCalculation);

    UsageCalculation.Key engIdKey =
        new UsageCalculation.Key(
            RHEL_FOR_X86_ELS_PAYG,
            ServiceLevel.PREMIUM,
            Usage._ANY,
            BillingProvider.RED_HAT,
            "_ANY");
    assertTrue(accountUsageCalculation.containsCalculation(engIdKey));
    UsageCalculation.Key engIdKey1 =
        new UsageCalculation.Key(
            RHEL_FOR_X86, ServiceLevel.PREMIUM, Usage._ANY, BillingProvider.RED_HAT, "_ANY");
    assertFalse(accountUsageCalculation.containsCalculation(engIdKey1));
    assertEquals(
        Double.valueOf(42.0),
        accountUsageCalculation
            .getCalculation(engIdKey)
            .getTotals(HardwareMeasurementType.PHYSICAL)
            .getMeasurement(MetricIdUtils.getCores()));

    // Not defined on the event, should not exist.
    List.of(RHEL_WORKSTATION_SWATCH_PRODUCT_ID, RHEL_COMPUTE_NODE_SWATCH_PRODUCT_ID)
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

  @ParameterizedTest
  @MethodSource("generateDuplicateEventTestData")
  void testHandlesDuplicateEvents(MetricId metricId) {
    Measurement measurement = new Measurement().withUom(metricId.toString()).withValue(42.0);
    Event event =
        createEvent()
            .withEventId(UUID.randomUUID())
            .withRole(Event.Role.OSD)
            .withProductTag(Set.of(OSD_PRODUCT_TAG))
            .withTimestamp(OffsetDateTime.parse("2021-02-26T00:00:00Z"))
            .withServiceType(SERVICE_TYPE)
            .withMeasurements(Collections.singletonList(measurement))
            .withUsage(Event.Usage.PRODUCTION)
            .withBillingProvider(Event.BillingProvider.RED_HAT)
            .withBillingAccountId(Optional.of("sellerAcctId"));

    AccountUsageCalculationCache cache = new AccountUsageCalculationCache();

    // Records with the same record date should only be processed once.
    metricUsageCollector.calculateUsage(List.of(event, event), cache);

    assertEquals(1, cache.getCalculations().size());
    assertTrue(cache.contains(event));

    AccountUsageCalculation accountUsageCalculation = cache.get(event);
    assertNotNull(accountUsageCalculation);
    UsageCalculation.Key usageCalculationKey =
        new UsageCalculation.Key(
            OSD_PRODUCT_TAG,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider.RED_HAT,
            "sellerAcctId");
    assertTrue(accountUsageCalculation.containsCalculation(usageCalculationKey));
    assertEquals(
        Double.valueOf(42.0),
        accountUsageCalculation
            .getCalculation(usageCalculationKey)
            .getTotals(HardwareMeasurementType.PHYSICAL)
            .getMeasurement(metricId));
  }

  private static Stream<Arguments> generateDuplicateEventTestData() {
    return Stream.of(
        Arguments.of(MetricIdUtils.getCores()),
        Arguments.of(MetricIdUtils.getInstanceHours()),
        Arguments.of(MetricIdUtils.getSockets()));
  }

  @Test
  void testUpdatesMonthlyTotal() {
    String instanceId = UUID.randomUUID().toString();
    OffsetDateTime usageTimestamp = OffsetDateTime.parse("2021-02-26T00:00:00Z");

    Measurement coresMeasurement =
        new Measurement().withUom(MetricIdUtils.getCores().toString()).withValue(42.0);
    Measurement instanceHoursMeasurement =
        new Measurement().withUom(MetricIdUtils.getInstanceHours().toString()).withValue(43.0);

    // Events can have the same timestamp, and will be applied if the
    // record date is different. Order of creation matters for the following
    // Events since the record date is set via createEvent.
    Event coresEvent1 =
        createEvent(instanceId)
            .withEventId(UUID.randomUUID())
            .withTimestamp(usageTimestamp)
            .withServiceType(SERVICE_TYPE)
            .withMeasurements(Collections.singletonList(coresMeasurement))
            .withUsage(Event.Usage.PRODUCTION);

    Event instanceHoursEvent1 =
        createEvent(instanceId)
            .withEventId(UUID.randomUUID())
            .withTimestamp(usageTimestamp)
            .withServiceType(SERVICE_TYPE)
            .withMeasurements(Collections.singletonList(instanceHoursMeasurement))
            .withUsage(Event.Usage.PRODUCTION);

    Event coresEvent2 =
        createEvent(instanceId)
            .withEventId(UUID.randomUUID())
            .withTimestamp(usageTimestamp)
            .withServiceType(SERVICE_TYPE)
            .withMeasurements(Collections.singletonList(coresMeasurement))
            .withUsage(Event.Usage.PRODUCTION);

    Event instanceHoursEvent2 =
        createEvent(instanceId)
            .withEventId(UUID.randomUUID())
            .withTimestamp(usageTimestamp)
            .withServiceType(SERVICE_TYPE)
            .withMeasurements(Collections.singletonList(instanceHoursMeasurement))
            .withUsage(Event.Usage.PRODUCTION);

    metricUsageCollector.updateHosts(
        ORG_ID,
        SERVICE_TYPE,
        List.of(coresEvent1, instanceHoursEvent1, coresEvent2, instanceHoursEvent2));

    ArgumentCaptor<Host> saveHostCaptor = ArgumentCaptor.forClass(Host.class);
    verify(hostRepository, times(4)).save(saveHostCaptor.capture());

    Set<Host> savedHosts = new HashSet<>(saveHostCaptor.getAllValues());
    assertEquals(1, savedHosts.size());
    Host instance = savedHosts.iterator().next();
    assertEquals(
        Double.valueOf(84.0), instance.getMonthlyTotal("2021-02", MetricIdUtils.getCores()));
    assertEquals(
        Double.valueOf(86.0),
        instance.getMonthlyTotal("2021-02", MetricIdUtils.getInstanceHours()));
  }

  @Test
  void testUpdatesMonthlyTotalWhenEventsAreOldButRecordDateIsValid() {
    Measurement measurement =
        new Measurement().withUom(MetricIdUtils.getCores().toString()).withValue(42.0);
    String instanceId = UUID.randomUUID().toString();
    OffsetDateTime eventDate = clock.startOfCurrentHour();

    Event event1 =
        createEvent(instanceId)
            .withEventId(UUID.randomUUID())
            .withTimestamp(eventDate)
            .withServiceType(SERVICE_TYPE)
            .withMeasurements(Collections.singletonList(measurement))
            .withUsage(Event.Usage.PRODUCTION);

    Event olderEvent =
        createEvent(instanceId)
            .withEventId(UUID.randomUUID())
            .withTimestamp(eventDate.minusMonths(2))
            .withServiceType(SERVICE_TYPE)
            .withMeasurements(Collections.singletonList(measurement))
            .withUsage(Event.Usage.PRODUCTION);

    OffsetDateTime instanceDate = eventDate.minusDays(1);
    Host activeInstance = new Host();
    activeInstance.setInstanceId(instanceId);
    activeInstance.setInstanceType(SERVICE_TYPE);
    activeInstance.setLastSeen(instanceDate);

    String monthId = InstanceMonthlyTotalKey.formatMonthId(instanceDate);
    activeInstance.addToMonthlyTotal(monthId, MetricIdUtils.getCores(), 200.0);

    List<Host> activeInstances = List.of(activeInstance);
    when(hostRepository.findAllByOrgIdAndInstanceIdIn(
            ORG_ID, Set.of(activeInstance.getInstanceId())))
        .thenReturn(activeInstances.stream());

    metricUsageCollector.updateHosts(ORG_ID, SERVICE_TYPE, List.of(event1, olderEvent));
    assertEquals(
        Double.valueOf(242.0), activeInstance.getMonthlyTotal(monthId, MetricIdUtils.getCores()));
    // Past event should have the instance monthly totals added to the host.
    String pastEventMonthId = InstanceMonthlyTotalKey.formatMonthId(olderEvent.getTimestamp());
    assertEquals(
        Double.valueOf(42.0),
        activeInstance.getMonthlyTotal(pastEventMonthId, MetricIdUtils.getCores()));
  }

  @Test
  void testHandleMonthlyTotalWhenDuplicateInstanceIDFromHBIAndCost() {
    Measurement measurement =
        new Measurement().withUom(MetricIdUtils.getCores().toString()).withValue(42.0);
    String instanceId = UUID.randomUUID().toString();
    OffsetDateTime eventDate = clock.startOfCurrentHour();

    Event event1 =
        createEvent(instanceId)
            .withEventId(UUID.randomUUID())
            .withTimestamp(eventDate)
            .withServiceType(SERVICE_TYPE)
            .withMeasurements(Collections.singletonList(measurement))
            .withUsage(Event.Usage.PRODUCTION);

    Event olderEvent =
        createEvent(instanceId)
            .withEventId(UUID.randomUUID())
            .withTimestamp(eventDate.minusMonths(2))
            .withServiceType(SERVICE_TYPE)
            .withMeasurements(Collections.singletonList(measurement))
            .withUsage(Event.Usage.PRODUCTION);

    OffsetDateTime instanceDate = eventDate.minusDays(1);
    // Cost hourly tally created host from events
    Host activeInstance1 = new Host();
    activeInstance1.setInstanceId(instanceId);
    activeInstance1.setInstanceType(SERVICE_TYPE);
    activeInstance1.setLastSeen(instanceDate);
    activeInstance1.setInstanceType("RHEL System");

    String monthId = InstanceMonthlyTotalKey.formatMonthId(instanceDate);
    activeInstance1.addToMonthlyTotal(monthId, MetricIdUtils.getCores(), 200.0);

    // HBI host created from nightly tally
    Host activeInstance2 = new Host();
    activeInstance2.setInstanceId(instanceId);
    activeInstance2.setInstanceType(SERVICE_TYPE);
    activeInstance2.setLastSeen(instanceDate);
    activeInstance2.setInstanceType("HBI_HOST");

    List<Host> activeInstances = List.of(activeInstance1, activeInstance2);
    when(hostRepository.findAllByOrgIdAndInstanceIdIn(
            ORG_ID, Set.of(activeInstance1.getInstanceId())))
        .thenReturn(activeInstances.stream());

    metricUsageCollector.updateHosts(ORG_ID, SERVICE_TYPE, List.of(event1, olderEvent));
    assertEquals(
        Double.valueOf(242.0), activeInstance1.getMonthlyTotal(monthId, MetricIdUtils.getCores()));
    assertNull(activeInstance2.getMonthlyTotal(monthId, MetricIdUtils.getCores()));
    // Past event should have the instance monthly totals added to the host.
    String pastEventMonthId = InstanceMonthlyTotalKey.formatMonthId(olderEvent.getTimestamp());
    assertEquals(
        Double.valueOf(42.0),
        activeInstance1.getMonthlyTotal(pastEventMonthId, MetricIdUtils.getCores()));
  }

  @Test
  void testEventWithNullFieldsProcessedDuringUpdateHosts() {
    // NOTE: null in the JSON gets represented as Optional.empty()
    Measurement measurement =
        new Measurement().withUom(MetricIdUtils.getCores().toString()).withValue(42.0);
    Event event =
        createEvent()
            .withEventId(UUID.randomUUID())
            .withTimestamp(OffsetDateTime.parse("2021-02-26T00:00:00Z"))
            .withServiceType(SERVICE_TYPE)
            .withBillingAccountId(Optional.empty())
            .withDisplayName(Optional.empty())
            .withHypervisorUuid(Optional.empty())
            .withInsightsId(Optional.empty())
            .withInventoryId(Optional.empty())
            .withSubscriptionManagerId(Optional.empty());

    metricUsageCollector.updateHosts(ORG_ID, SERVICE_TYPE, List.of(event));
    ArgumentCaptor<Host> captor = ArgumentCaptor.forClass(Host.class);
    verify(hostRepository, times(1)).save(captor.capture());

    Host instance = captor.getValue();
    assertNotNull(instance);
    assertEquals(event.getInstanceId(), instance.getInstanceId());
  }

  @Test
  void testCreateInstanceDefaultBillingProvider() {
    Measurement measurement =
        new Measurement().withUom(MetricIdUtils.getCores().toString()).withValue(42.0);
    Event event =
        createEvent()
            .withEventId(UUID.randomUUID())
            .withTimestamp(OffsetDateTime.parse("2021-02-26T00:00:00Z"))
            .withServiceType(SERVICE_TYPE)
            .withMeasurements(Collections.singletonList(measurement))
            .withBillingAccountId(Optional.of("sellerAcct"));

    metricUsageCollector.updateHosts(ORG_ID, SERVICE_TYPE, List.of(event));
    ArgumentCaptor<Host> captor = ArgumentCaptor.forClass(Host.class);
    verify(hostRepository, times(1)).save(captor.capture());

    Host instance = captor.getValue();
    assertNotNull(instance);
    assertEquals(BillingProvider.RED_HAT, instance.getBillingProvider());
  }

  @Test
  void testInstanceHasOrgIdSet() {
    Measurement measurement =
        new Measurement().withUom(MetricIdUtils.getCores().toString()).withValue(42.0);
    Event event =
        createEvent()
            .withTimestamp(OffsetDateTime.parse("2021-02-26T00:00:00Z"))
            .withServiceType(SERVICE_TYPE)
            .withMeasurements(Collections.singletonList(measurement));

    metricUsageCollector.updateHosts(ORG_ID, SERVICE_TYPE, List.of(event));
    ArgumentCaptor<Host> captor = ArgumentCaptor.forClass(Host.class);
    verify(hostRepository, times(1)).save(captor.capture());

    Host instance = captor.getValue();
    assertNotNull(instance);
    assertEquals(event.getInstanceId(), instance.getInstanceId());
    assertEquals("test-org", instance.getOrgId());
  }

  @Test
  void testCalculateUsageLoadsUsageCalculationFromCacheWhenItExists() {
    OffsetDateTime eventTimestamp = OffsetDateTime.parse("2021-02-26T00:00:00Z");

    UsageCalculation.Key usageCalculationKey =
        new UsageCalculation.Key(
            OSD_PRODUCT_TAG,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider.RED_HAT,
            "sellerAcct");

    AccountUsageCalculation existingCalc = new AccountUsageCalculation(ORG_ID);
    existingCalc.addUsage(
        usageCalculationKey, HardwareMeasurementType.PHYSICAL, MetricIdUtils.getCores(), 200.0);

    AccountUsageCalculationCache cache = new AccountUsageCalculationCache();
    cache.getCalculations().put(eventTimestamp, existingCalc);

    Measurement measurement =
        new Measurement().withUom(MetricIdUtils.getCores().toString()).withValue(42.0);
    Event event1 =
        createEvent()
            .withEventId(UUID.randomUUID())
            .withRole(Event.Role.OSD)
            .withTimestamp(eventTimestamp)
            .withServiceType(SERVICE_TYPE)
            .withMeasurements(Collections.singletonList(measurement))
            .withBillingProvider(Event.BillingProvider.RED_HAT)
            .withBillingAccountId(Optional.of("sellerAcct"));

    metricUsageCollector.calculateUsage(List.of(event1), cache);

    assertEquals(1, cache.getCalculations().size());
    assertTrue(cache.contains(event1));

    AccountUsageCalculation accountUsageCalculation = cache.get(event1);
    assertTrue(accountUsageCalculation.containsCalculation(usageCalculationKey));
    assertEquals(
        Double.valueOf(242.0),
        accountUsageCalculation
            .getCalculation(usageCalculationKey)
            .getTotals(HardwareMeasurementType.PHYSICAL)
            .getMeasurement(MetricIdUtils.getCores()));

    verifyNoInteractions(tallySnapshotRepository);
  }

  @Test
  void testCalculateUsageLoadsUsageFromSnapshotRepositoryWhenNotInCache() {
    OffsetDateTime eventDate = OffsetDateTime.parse("2021-02-26T00:00:00Z");
    TallySnapshot snapshot = createSnapshot(eventDate, 100.0);
    when(tallySnapshotRepository.findByOrgIdAndProductIdInAndGranularityAndSnapshotDateBetween(
            "test-org",
            Set.of(OCP_PRODUCT_TAG, OSD_PRODUCT_TAG),
            Granularity.HOURLY,
            eventDate,
            clock.endOfHour(eventDate)))
        .thenReturn(Stream.of(snapshot));

    Measurement measurement =
        new Measurement().withUom(MetricIdUtils.getCores().toString()).withValue(42.0);
    Event event =
        createEvent()
            .withEventId(UUID.randomUUID())
            .withRole(Event.Role.OSD)
            .withTimestamp(eventDate)
            .withServiceType(SERVICE_TYPE)
            .withMeasurements(Collections.singletonList(measurement))
            .withBillingProvider(Event.BillingProvider.RED_HAT)
            .withBillingAccountId(Optional.of("sellerAcct"));

    AccountUsageCalculationCache cache = new AccountUsageCalculationCache();
    metricUsageCollector.calculateUsage(List.of(event), cache);

    assertEquals(1, cache.getCalculations().size());
    assertTrue(cache.contains(event));

    AccountUsageCalculation accountUsageCalculation = cache.get(event);
    UsageCalculation.Key usageCalculationKey =
        new UsageCalculation.Key(
            OSD_PRODUCT_TAG,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider.RED_HAT,
            "sellerAcct");
    assertTrue(accountUsageCalculation.containsCalculation(usageCalculationKey));
    assertEquals(
        Double.valueOf(142.0),
        accountUsageCalculation
            .getCalculation(usageCalculationKey)
            .getTotals(HardwareMeasurementType.PHYSICAL)
            .getMeasurement(MetricIdUtils.getCores()));
  }

  @Test
  void testRemovesStaleBuckets() {
    var host = new Host();
    host.setInstanceId("instanceId");
    when(hostRepository.findAllByOrgIdAndInstanceIdIn(any(), any()))
        .thenAnswer(i -> Stream.of(host));
    for (var value : Set.of(1, 2)) {
      var billingAccountId = "billingAccount" + value;
      Measurement measurement =
          new Measurement().withUom(MetricIdUtils.getCores().toString()).withValue((double) value);
      Event event =
          createEvent()
              .withEventId(UUID.randomUUID())
              .withInstanceId("instanceId")
              .withRole(Event.Role.OSD)
              .withProductTag(Set.of(OSD_PRODUCT_TAG))
              .withTimestamp(OffsetDateTime.parse("2021-02-26T00:00:00Z").plusHours(value))
              .withServiceType(SERVICE_TYPE)
              .withMeasurements(Collections.singletonList(measurement))
              .withSla(Event.Sla.PREMIUM)
              .withBillingProvider(Event.BillingProvider.RED_HAT)
              .withBillingAccountId(Optional.of(billingAccountId));
      metricUsageCollector.updateHosts("org123", "serviceType", List.of(event));
      var expectedBillingAccountIds = Set.of(billingAccountId, "_ANY");
      // This shows that the instance has only a single billing account id in its buckets
      var hostBucketBillingAccountIds =
          host.getBuckets().stream()
              .map(HostTallyBucket::getKey)
              .map(HostBucketKey::getBillingAccountId)
              .collect(Collectors.toSet());
      assertEquals(expectedBillingAccountIds, hostBucketBillingAccountIds);
    }
  }

  private static Event createEvent() {
    return createEvent(UUID.randomUUID().toString());
  }

  private static Event createEvent(String instanceId) {
    return new Event()
        .withEventId(UUID.randomUUID())
        .withRole(Event.Role.OSD)
        .withOrgId("test-org")
        .withServiceType(SERVICE_TYPE)
        .withInstanceId(instanceId)
        .withTimestamp(OffsetDateTime.parse("2021-02-26T00:00:00Z"))
        .withProductTag(Set.of(OSD_PRODUCT_TAG))
        // MICROS precision to match the DB.
        .withRecordDate(OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS))
        .withBillingProvider(Event.BillingProvider.RED_HAT)
        .withBillingAccountId(Optional.of("sellerAcct"));
  }

  private TallySnapshot createSnapshot(OffsetDateTime snapshotDate, double value) {
    Map<TallyMeasurementKey, Double> measurements = new HashMap<>();
    measurements.put(
        new TallyMeasurementKey(
            HardwareMeasurementType.PHYSICAL, MetricIdUtils.getCores().toString()),
        value);
    measurements.put(
        new TallyMeasurementKey(HardwareMeasurementType.TOTAL, MetricIdUtils.getCores().toString()),
        value);
    return TallySnapshot.builder()
        .snapshotDate(snapshotDate)
        .productId(OSD_PRODUCT_TAG)
        .orgId("org123")
        .tallyMeasurements(measurements)
        .granularity(Granularity.HOURLY)
        .serviceLevel(ServiceLevel.PREMIUM)
        .usage(Usage.PRODUCTION)
        .billingProvider(BillingProvider.RED_HAT)
        .billingAccountId("sellerAcct")
        .build();
  }

  private void assertUsageCalculationForEvent(Event event) {
    double expectedValue = 42.0;
    Measurement measurement =
        new Measurement().withUom(MetricIdUtils.getCores().toString()).withValue(expectedValue);
    event.withMeasurements(List.of(measurement));
    AccountUsageCalculationCache cache = new AccountUsageCalculationCache();
    metricUsageCollector.calculateUsage(List.of(event), cache);

    assertEquals(1, cache.getCalculations().size());
    assertTrue(cache.contains(event));

    AccountUsageCalculation accountUsageCalculation = cache.get(event);
    UsageCalculation.Key usageCalculationKey =
        new UsageCalculation.Key(
            OSD_PRODUCT_TAG,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider.RED_HAT,
            "sellerAcct");
    assertTrue(accountUsageCalculation.containsCalculation(usageCalculationKey));
    assertEquals(
        Double.valueOf(expectedValue),
        accountUsageCalculation
            .getCalculation(usageCalculationKey)
            .getTotals(HardwareMeasurementType.PHYSICAL)
            .getMeasurement(MetricIdUtils.getCores()));
  }
}
