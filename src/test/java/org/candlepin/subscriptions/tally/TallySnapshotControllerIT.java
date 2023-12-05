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

import static org.candlepin.subscriptions.metering.MeteringEventFactory.getEventType;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.db.EventRecordRepository;
import org.candlepin.subscriptions.db.HostTallyBucketRepository;
import org.candlepin.subscriptions.db.model.EventRecord;
import org.candlepin.subscriptions.db.model.HostTallyBucket;
import org.candlepin.subscriptions.inventory.db.InventoryRepository;
import org.candlepin.subscriptions.inventory.db.model.InventoryHostFacts;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Measurement;
import org.candlepin.subscriptions.resource.OptInResource;
import org.candlepin.subscriptions.resource.TallyResource;
import org.candlepin.subscriptions.security.WithMockRedHatPrincipal;
import org.candlepin.subscriptions.test.ExtendWithEmbeddedKafka;
import org.candlepin.subscriptions.test.ExtendWithSwatchDatabase;
import org.candlepin.subscriptions.util.DateRange;
import org.candlepin.subscriptions.utilization.api.model.GranularityType;
import org.candlepin.subscriptions.utilization.api.model.TallyReportData;
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
@ActiveProfiles(value = {"worker", "kafka-queue", "api", "test-inventory"})
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

  @MockBean(answer = Answers.CALLS_REAL_METHODS)
  InventoryRepository inventoryRepository;

  OffsetDateTime start;
  OffsetDateTime end;
  List<Event> events = new ArrayList<>();

  @BeforeEach
  public void setup() {
    events.clear();
  }

  @WithMockRedHatPrincipal(value = USER_ID)
  @Test
  void testProduceHourlySnapshotsForOrgFromEvents() {
    givenOrgAndAccountInConfig();
    givenFiveDaysOfRangeForReport();

    givenEventAtDay(1, 78.4390);
    givenEventAtDay(3, 89.716);

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
  }

  private void givenEventAtDay(int dayAt, double value) {
    Event event = new Event();
    event.setEventId(UUID.randomUUID());
    event.setTimestamp(start.plusDays(dayAt));
    event.setSla(Event.Sla.PREMIUM);
    event.setRole(Event.Role.MOA_HOSTEDCONTROLPLANE);
    event.setProductTag(Set.of(PRODUCT_TAG));
    event.setOrgId(ORG_ID);
    event.setEventType(getEventType(METRIC, PRODUCT_TAG));
    event.setInstanceId(PROMETHEUS);
    event.setEventSource("any");
    event.setExpiration(Optional.of(event.getTimestamp().plusHours(5)));
    event.setMeasurements(List.of(measurement(value)));
    event.setServiceType("rosa Instance");
    event.setBillingProvider(Event.BillingProvider.AWS);
    event.setBillingAccountId(Optional.of("mktp-123"));

    eventRecordRepository.save(new EventRecord(event));

    events.add(event);
  }

  private void whenProduceSnapshotsForOrg() {
    controller.produceSnapshotsForOrg(ORG_ID);
  }

  private void whenProduceHourlySnapshotsForOrg() {
    controller.produceHourlySnapshotsForOrg(
        ORG_ID, new DateRange(clock.startOfCurrentHour(), clock.startOfCurrentHour().plusHours(1)));
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
    // assert events
    double accumulatedValue = 0;
    for (var point : report.getData()) {
      for (Event event : events) {
        if (point.getDate().toLocalDate().equals(event.getTimestamp().toLocalDate())) {
          for (var measurement : event.getMeasurements()) {
            accumulatedValue += measurement.getValue();
          }
        }
      }

      int expectedValue = (int) Math.ceil(accumulatedValue);
      assertEquals(
          expectedValue,
          point.getValue(),
          "Unexpected value in data point at '"
              + point.getDate()
              + "'. Expected was "
              + expectedValue
              + ". Report was: "
              + report);
    }

    // assert days, end day is inclusive, so we need to add one day more.
    assertEquals((int) Duration.between(start, end).toDays() + 1, report.getMeta().getCount());
  }

  private Measurement measurement(Double value) {
    Measurement measurement = new Measurement();
    measurement.setUom(METRIC);
    measurement.setValue(value);
    return measurement;
  }
}
