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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "CONTRACT_USE_STUB=true")
@ActiveProfiles(value = {"worker", "kafka-queue", "api", "test-inventory", "capacity-ingress"})
class TallySnapshotControllerIT implements ExtendWithSwatchDatabase, ExtendWithEmbeddedKafka {
  static final String PROMETHEUS = "prometheus";
  static final String METRIC = "Cores";
  static final String USER_ID = "123";
  static final String ORG_ID = "owner" + USER_ID;
  static final String PRODUCT_TAG = "rosa";
  static final String PHYSICAL = "PHYSICAL";

  @Autowired TallySnapshotController controller;
  @Autowired TallyResource tallyResource;

  @Autowired ApplicationClock clock;

  @Autowired EventRecordRepository eventRecordRepository;
  @Autowired OptInResource optInResource;
  @Autowired HostTallyBucketRepository hostTallyBucketRepository;
  @Autowired AccountServiceInventoryRepository accountServiceInventoryRepository;
  @Autowired TallyStateRepository tallyStateRepository;

  @MockBean(answer = Answers.CALLS_REAL_METHODS)
  InventoryRepository inventoryRepository;

  OffsetDateTime start;
  OffsetDateTime end;
  List<Event> events = new ArrayList<>();
  ExpectedReport expectedReport = new ExpectedReport();

  @BeforeEach
  public void setup() {
    expectedReport.clear();
    events.clear();
  }

  @WithMockRedHatPrincipal(value = USER_ID)
  @Test
  void testProduceHourlySnapshotsForOrgFromEvents() {
    givenOrgAndAccountInConfig();
    givenFiveDaysOfRangeForReport();
    // prevent retally
    givenExistingHostInformation();
    givenEventAtDay(1, 78.4390);
    givenEventAtDay(3, 89.716);

    whenProduceHourlySnapshotsForOrg();

    assertTallyReportData();

    givenEventAtDay(4, 10.0);
    whenProduceHourlySnapshotsForOrg();
    assertTallyReportData();

    // Process an amendment.
    givenEventAtDay(1, -78.4390);
    givenEventAtDay(1, 100.0);
    whenProduceHourlySnapshotsForOrg();
    assertTallyReportData();
  }

  @WithMockRedHatPrincipal(value = USER_ID)
  @ParameterizedTest
  @CsvSource(value = {"83,rhel-for-x86-ha", "90,rhel-for-x86-rs", "389,rhel-for-sap-x86"})
  void testProduceSnapshotsForOrgFromHostsPartOfHbi(String engId, String product) {
    givenOrgAndAccountInConfig();
    UUID inventoryId = givenInventoryHostWithProductIds(engId);

    whenProduceSnapshotsForOrg();

    assertAllHostTallyBucketsHaveExpectedProduct(inventoryId, product);
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
    tallyStateRepository.save(
        new TallyState(ResourceUtils.getOrgId(), "rosa Instance", clock.now()));
  }

  private void givenExistingHostInformation() {
    var host = new Host();
    host.setOrgId(ORG_ID);
    host.setInstanceId("1c474d4e-c277-472c-94ab-8229a40417eb");
    host.setDisplayName("name");
    host.setInstanceType("rosa Instance");
    host.setLastSeen(clock.startOfCurrentMonth().minusMonths(1));
    AccountServiceInventory service = new AccountServiceInventory(ORG_ID, "rosa Instance");
    service.getServiceInstances().put(host.getInstanceId(), host);
    accountServiceInventoryRepository.save(service);
  }

  private void givenEventAtDay(int dayAt, double value) {
    Event event = new Event();
    event.setEventId(UUID.randomUUID());
    event.setTimestamp(start.plusDays(dayAt));
    event.setSla(Event.Sla.PREMIUM);
    event.setRole(Event.Role.MOA_HOSTEDCONTROLPLANE);
    event.setProductTag(Set.of(PRODUCT_TAG));
    event.setOrgId(ORG_ID);
    event.setEventType("snapshot_" + METRIC.toLowerCase() + "_" + PRODUCT_TAG.toLowerCase());
    event.setInstanceId(PROMETHEUS);
    event.setEventSource("any");
    event.setExpiration(Optional.of(event.getTimestamp().plusHours(5)));
    event.setMeasurements(List.of(measurement(value)));
    event.setServiceType("rosa Instance");
    event.setBillingProvider(Event.BillingProvider.AWS);
    event.setBillingAccountId(Optional.of("mktp-123"));

    eventRecordRepository.save(new EventRecord(event));

    events.add(event);
    expectedReport.applyEvent(event);
  }

  private void whenProduceSnapshotsForOrg() {
    controller.produceSnapshotsForOrg(ORG_ID);
  }

  private void whenProduceHourlySnapshotsForOrg() {
    controller.produceHourlySnapshotsForOrg(ORG_ID);
  }

  private void assertTallyReportData() {
    TallyReportData report =
        tallyResource.getTallyReportData(
            ProductId.fromString(PRODUCT_TAG),
            MetricId.fromString(METRIC),
            GranularityType.DAILY,
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

    expectedReport.assertReport(report);
  }

  private Measurement measurement(Double value) {
    Measurement measurement = new Measurement();
    measurement.setUom(METRIC);
    measurement.setMetricId(METRIC);
    measurement.setValue(value);
    return measurement;
  }

  private class ExpectedReport {
    private final Map<OffsetDateTime, Measurement> measurementsByDate = new HashMap<>();

    public void applyEvent(Event event) {
      // For now tests are against Daily report, so track by start of day.
      OffsetDateTime timestamp = clock.startOfDay(event.getTimestamp());
      Measurement current = measurementsByDate.getOrDefault(timestamp, measurement(0.0));
      measurementsByDate.put(timestamp, current);
      event.getMeasurements().stream()
          .filter(m -> m.getUom().equals(METRIC))
          .forEach(m -> current.setValue(current.getValue() + m.getValue()));
    }

    public void assertReport(TallyReportData report) {
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
      assertEquals((int) Duration.between(start, end).toDays() + 1, report.getMeta().getCount());
    }

    public void clear() {
      measurementsByDate.clear();
    }
  }
}
