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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.ProductId;
import com.redhat.swatch.configuration.registry.Variant;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.db.AccountServiceInventoryRepository;
import org.candlepin.subscriptions.db.EventRecordRepository;
import org.candlepin.subscriptions.db.HostTallyBucketRepository;
import org.candlepin.subscriptions.db.TallyStateRepository;
import org.candlepin.subscriptions.db.model.AccountServiceInventory;
import org.candlepin.subscriptions.db.model.EventRecord;
import org.candlepin.subscriptions.db.model.Host;
import org.candlepin.subscriptions.db.model.HostTallyBucket;
import org.candlepin.subscriptions.db.model.TallyState;
import org.candlepin.subscriptions.inventory.db.InventoryRepository;
import org.candlepin.subscriptions.inventory.db.model.InventoryHostFacts;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Measurement;
import org.candlepin.subscriptions.resource.ResourceUtils;
import org.candlepin.subscriptions.resource.api.v1.OptInResource;
import org.candlepin.subscriptions.resource.api.v1.TallyResource;
import org.candlepin.subscriptions.security.WithMockRedHatPrincipal;
import org.candlepin.subscriptions.test.ExtendWithEmbeddedKafka;
import org.candlepin.subscriptions.test.ExtendWithSwatchDatabase;
import org.candlepin.subscriptions.utilization.api.v1.model.GranularityType;
import org.candlepin.subscriptions.utilization.api.v1.model.TallyReportData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {"CONTRACT_USE_STUB=true", "HOURLY_TALLY_EVENT_BATCH_SIZE=2"})
@ActiveProfiles({"worker", "kafka-queue", "api", "test-inventory", "capacity-ingress"})
@ExtendWith(OutputCaptureExtension.class)
class TallySnapshotControllerIT implements ExtendWithSwatchDatabase, ExtendWithEmbeddedKafka {
  static final String INSTANCE_ID = "i-123456";
  static final String USER_ID = "123";
  static final String ORG_ID = "owner" + USER_ID;
  static final String ROSA = "rosa";
  static final String ANSIBLE = "ansible-aap-managed";
  static final String PHYSICAL = "PHYSICAL";

  @Autowired TallySnapshotController controller;
  @Autowired TallyResource tallyResource;

  @Autowired ApplicationClock clock;

  @Autowired EventRecordRepository eventRecordRepository;
  @Autowired OptInResource optInResource;
  @Autowired HostTallyBucketRepository hostTallyBucketRepository;
  @Autowired AccountServiceInventoryRepository accountServiceInventoryRepository;
  @Autowired TallyStateRepository tallyStateRepository;

  @MockitoBean(answers = Answers.CALLS_REAL_METHODS)
  InventoryRepository inventoryRepository;

  OffsetDateTime start;
  OffsetDateTime end;
  ExpectedReport expectedReport = new ExpectedReport();
  ProductId product;
  String serviceType;
  String metric;

  @Transactional
  @BeforeEach
  public void setup() {
    expectedReport.clear();
  }

  /**
   * Test to reproduce the issue <a
   * href="https://issues.redhat.com/browse/SWATCH-2887">SWATCH-2887</a>
   */
  @WithMockRedHatPrincipal(value = USER_ID)
  @Test
  void testProduceHourlySnapshotsForOrgFromEventsUpdatingTheSameBucket(CapturedOutput output) {
    givenProduct(ROSA);
    givenOrgAndAccountInConfig();
    givenFiveDaysOfRangeForReport();
    // prevent retally
    givenExistingHostInformation();
    // first tx
    // host_tally_buckets created with STANDARD
    givenEventAtDay(1, 2, Event.Sla.STANDARD);
    // do nothing
    givenEventAtDay(2, 2, Event.Sla.STANDARD);

    // second tx
    // host_tally_buckets deleted with STANDARD
    // host_tally_buckets created with PREMIUM
    givenEventAtDay(3, 2, Event.Sla.PREMIUM);
    // host_tally_buckets deleted with PREMIUM
    // host_tally_buckets created with STANDARD
    givenEventAtDay(4, 2, Event.Sla.STANDARD);

    whenProduceHourlySnapshotsForOrg();
    assertFalse(output.getAll().contains("Could not collect"));
  }

  @WithMockRedHatPrincipal(value = USER_ID)
  @Test
  void testProduceHourlySnapshotsForOrgFromEvents() {
    givenProduct(ROSA);
    givenOrgAndAccountInConfig();
    givenFiveDaysOfRangeForReport();
    // prevent retally
    givenExistingHostInformation();
    givenEventAtDay(1, 78.4390);
    givenEventAtDay(3, 89.716);

    whenProduceHourlySnapshotsForOrg();

    assertDailyTallyReportData();

    givenEventAtDay(4, 10.0);
    whenProduceHourlySnapshotsForOrg();
    assertDailyTallyReportData();

    // Process an amendment.
    givenEventAtDay(1, -78.4390);
    givenEventAtDay(1, 100.0);
    whenProduceHourlySnapshotsForOrg();
    assertDailyTallyReportData();
  }

  @WithMockRedHatPrincipal(value = USER_ID)
  @ParameterizedTest
  @CsvSource(value = {"83,rhel-for-x86-ha", "90,rhel-for-x86-rs", "389,rhel-for-sap-x86"})
  void testProduceSnapshotsForOrgFromHostsPartOfHbi(String engId, String product) {
    givenProduct(ROSA);
    givenOrgAndAccountInConfig();
    UUID inventoryId = givenInventoryHostWithProductIds(engId);

    whenProduceSnapshotsForOrg();

    assertAllHostTallyBucketsHaveExpectedProduct(inventoryId, product);
  }

  @WithMockRedHatPrincipal(value = USER_ID)
  @Test
  void testAnsibleTallyReportWithGranularityHourly() {
    givenProduct(ANSIBLE);
    givenOrgAndAccountInConfig();
    givenFiveDaysOfRangeForReport();
    givenExistingHostInformation();
    givenEventAtDay(1, 2, Event.Sla.STANDARD);

    whenProduceHourlySnapshotsForOrg();

    assertHourlyTallyReportData();
  }

  private void givenProduct(String productTag) {
    Variant variant = Variant.findByTag(productTag).orElseThrow();
    this.product = ProductId.fromString(productTag);
    this.serviceType = variant.getSubscription().getServiceType();
    this.metric = variant.getSubscription().getMetrics().iterator().next().getId();
  }

  private UUID givenInventoryHostWithProductIds(String... productIds) {
    UUID inventoryId = UUID.randomUUID();

    InventoryHostFacts host = new InventoryHostFacts();
    host.setOrgId(ORG_ID);
    host.setInventoryId(inventoryId);
    host.setSystemProfileSockets(2);
    host.setSystemProfileCoresPerSocket(4);
    host.setSystemProfileProductIds(String.join(",", productIds));
    host.setSubscriptionManagerId(UUID.randomUUID().toString());
    host.setInsightsId(UUID.randomUUID().toString());
    host.setMarketplace(false);
    host.setSystemProfileInfrastructureType(PHYSICAL);
    host.setDisplayName(host.getInsightsId());
    when(inventoryRepository.streamFacts(eq(ORG_ID), any())).thenAnswer(i -> Stream.of(host));

    return inventoryId;
  }

  private void givenFiveDaysOfRangeForReport() {
    start = clock.startOfCurrentHour().minusMonths(1);
    end = start.plusDays(5L);
  }

  private void givenOrgAndAccountInConfig() {
    optInResource.putOptInConfig();
    tallyStateRepository.save(new TallyState(ResourceUtils.getOrgId(), serviceType, clock.now()));
  }

  private void givenExistingHostInformation() {
    var host = new Host();
    host.setOrgId(ORG_ID);
    host.setInstanceId("1c474d4e-c277-472c-94ab-8229a40417eb");
    host.setDisplayName("name");
    host.setInstanceType(serviceType);
    host.setLastSeen(clock.startOfCurrentMonth().minusMonths(1));
    AccountServiceInventory service = new AccountServiceInventory(ORG_ID, serviceType);
    service.getServiceInstances().put(host.getInstanceId(), host);
    accountServiceInventoryRepository.save(service);
  }

  private void givenEventAtDay(int dayAt, double value) {
    givenEventAtDay(dayAt, value, Event.Sla.PREMIUM);
  }

  private void givenEventAtDay(int dayAt, double value, Event.Sla sla) {
    Event event = new Event();
    event.setEventId(UUID.randomUUID());
    event.setTimestamp(start.plusDays(dayAt));
    event.setSla(sla);
    event.setRole(Event.Role.MOA_HOSTEDCONTROLPLANE);
    event.setProductTag(Set.of(product.getValue()));
    event.setOrgId(ORG_ID);
    event.setEventType("snapshot_" + metric.toLowerCase() + "_" + product.getValue().toLowerCase());
    event.setInstanceId(INSTANCE_ID);
    event.setEventSource("any");
    event.setExpiration(Optional.of(event.getTimestamp().plusHours(5)));
    event.setMeasurements(List.of(measurement(value)));
    event.setServiceType(serviceType);
    event.setBillingProvider(Event.BillingProvider.AWS);
    event.setBillingAccountId(Optional.of("mktp-123"));

    eventRecordRepository.save(new EventRecord(event));

    expectedReport.addEvent(event);
  }

  private void whenProduceSnapshotsForOrg() {
    controller.produceSnapshotsForOrg(ORG_ID);
  }

  private void whenProduceHourlySnapshotsForOrg() {
    controller.produceHourlySnapshotsForOrg(ORG_ID);
  }

  private void assertHourlyTallyReportData() {
    assertTallyReportData(GranularityType.HOURLY);
  }

  private void assertDailyTallyReportData() {
    assertTallyReportData(GranularityType.DAILY);
  }

  private void assertTallyReportData(GranularityType granularity) {
    TallyReportData report =
        tallyResource.getTallyReportData(
            product,
            MetricId.fromString(metric),
            granularity,
            start,
            end,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            true,
            null);

    expectedReport.assertReport(granularity, report);
  }

  private void assertAllHostTallyBucketsHaveExpectedProduct(
      UUID inventoryId, String expectedProduct) {
    List<HostTallyBucket> allHostTallyBuckets = new ArrayList<>();
    hostTallyBucketRepository.findAll().forEach(allHostTallyBuckets::add);
    assertFalse(allHostTallyBuckets.isEmpty());
    assertTrue(
        allHostTallyBuckets.stream()
            .filter(h -> inventoryId.equals(h.getKey().getHostId()))
            .allMatch(h -> expectedProduct.equals(h.getKey().getProductId())));
  }

  private Measurement measurement(Double value) {
    Measurement measurement = new Measurement();
    measurement.setMetricId(metric);
    measurement.setValue(value);
    return measurement;
  }

  private class ExpectedReport {
    private final List<Event> rawEvents = new ArrayList<>();

    public void addEvent(Event event) {
      rawEvents.add(event);
    }

    public void assertReport(GranularityType granularity, TallyReportData report) {
      Map<OffsetDateTime, Measurement> measurementsByDate = groupsEventsByGranularity(granularity);

      int nextValue = 0;
      for (var point : report.getData()) {
        if (measurementsByDate.containsKey(point.getDate())) {
          nextValue =
              nextValue + (int) Math.ceil(measurementsByDate.get(point.getDate()).getValue());
        }
        assertEquals(
            nextValue,
            point.getValue(),
            "Unexpected value in data point at '"
                + point.getDate()
                + "'. Expected was "
                + nextValue
                + ". Report was: "
                + report);
      }

      // assert days, end day is inclusive, so we need to add one day more.
      Duration duration = Duration.between(start, end);
      if (granularity == GranularityType.DAILY) {
        assertEquals((int) duration.toDays() + 1, report.getMeta().getCount());
      } else if (granularity == GranularityType.HOURLY) {
        assertEquals((int) duration.toHours() + 1, report.getMeta().getCount());
      }
    }

    public void clear() {
      rawEvents.clear();
    }

    private Map<OffsetDateTime, Measurement> groupsEventsByGranularity(
        GranularityType granularity) {
      Map<OffsetDateTime, Measurement> measurementsByDate = new HashMap<>();
      for (Event event : rawEvents) {
        OffsetDateTime timestamp = event.getTimestamp();
        if (granularity == GranularityType.DAILY) {
          timestamp = clock.startOfDay(event.getTimestamp());
        }

        Measurement current = measurementsByDate.getOrDefault(timestamp, measurement(0.0));
        measurementsByDate.put(timestamp, current);
        event.getMeasurements().stream()
            .filter(m -> m.getMetricId().equals(metric))
            .forEach(m -> current.setValue(current.getValue() + m.getValue()));
      }

      return measurementsByDate;
    }
  }
}
