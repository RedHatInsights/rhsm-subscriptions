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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.any;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.*;
import org.candlepin.subscriptions.json.Measurement;
import org.candlepin.subscriptions.registry.TagProfile;
import org.candlepin.subscriptions.util.DateRange;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"worker", "test"})
class CombiningRollupSnapshotStrategyTest {

  private static final String OPEN_SHIFT_HOURLY = "OpenShift Hourly";

  @Autowired CombiningRollupSnapshotStrategy combiningRollupSnapshotStrategy;

  @Autowired TagProfile tagProfile;

  @MockBean TallySnapshotRepository repo;

  @MockBean
  @Qualifier("summaryProducer")
  SnapshotSummaryProducer producer;

  @Test
  void testConsecutiveHoursAddedTogether() {
    when(repo.findByAccountNumberAndProductIdInAndGranularityAndSnapshotDateBetween(
            any(), any(), any(), any(), any()))
        .then(invocation -> Stream.empty());
    UsageCalculation.Key usageKey =
        new UsageCalculation.Key(
            OPEN_SHIFT_HOURLY,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider._ANY,
            "_ANY");
    when(repo.save(any())).then(invocation -> invocation.getArgument(0));
    AccountUsageCalculation noonUsage = createAccountUsageCalculation(usageKey, 4.0);
    AccountUsageCalculation afternoonUsage = createAccountUsageCalculation(usageKey, 3.0);
    combiningRollupSnapshotStrategy.produceSnapshotsFromCalculations(
        "account123",
        new DateRange(
            OffsetDateTime.parse("2021-02-24T12:00:00Z"),
            OffsetDateTime.parse("2021-02-26T12:00:00Z")),
        tagProfile.getTagsWithPrometheusEnabledLookup(),
        Map.of(
            OffsetDateTime.parse("2021-02-25T12:00:00Z"),
            noonUsage,
            OffsetDateTime.parse("2021-02-25T13:00:00Z"),
            afternoonUsage),
        Granularity.HOURLY,
        Double::sum);

    TallySnapshot noonSnapshot =
        createTallySnapshot(Granularity.HOURLY, "2021-02-25T12:00:00Z", 4.0);
    TallySnapshot afternoonSnapshot =
        createTallySnapshot(Granularity.HOURLY, "2021-02-25T13:00:00Z", 3.0);
    TallySnapshot dailySnapshot =
        createTallySnapshot(Granularity.DAILY, "2021-02-25T00:00:00Z", 7.0);

    ArgumentCaptor<TallySnapshot> talliesSavedCaptor = ArgumentCaptor.forClass(TallySnapshot.class);
    verify(repo, times(3)).save(talliesSavedCaptor.capture());
    List<TallySnapshot> talliesSaved = talliesSavedCaptor.getAllValues();
    TallySnapshot actual =
        talliesSaved.stream()
            .filter(s -> s.getGranularity() == Granularity.DAILY)
            .findFirst()
            .orElseThrow();
    assertThat(talliesSaved, containsInAnyOrder(noonSnapshot, afternoonSnapshot, dailySnapshot));
    assertEquals(7.0, actual.getMeasurement(HardwareMeasurementType.TOTAL, Measurement.Uom.CORES));
    assertEquals(
        7.0, actual.getMeasurement(HardwareMeasurementType.PHYSICAL, Measurement.Uom.CORES));
  }

  @Test
  void testProducesMultipleDailyWhenNecessary() {
    OffsetDateTime hourlyTimestamp1 = OffsetDateTime.parse("2021-02-25T11:00:00Z");
    OffsetDateTime hourlyTimestamp2 = OffsetDateTime.parse("2021-02-26T11:00:00Z");
    OffsetDateTime dailyTimestamp1 = OffsetDateTime.parse("2021-02-25T00:00:00Z");
    OffsetDateTime dailyTimestamp2 = OffsetDateTime.parse("2021-02-26T00:00:00Z");
    when(repo.findByAccountNumberAndProductIdInAndGranularityAndSnapshotDateBetween(
            any(), any(), any(), any(), any()))
        .then(invocation -> Stream.empty());
    UsageCalculation.Key usageKey =
        new UsageCalculation.Key(
            OPEN_SHIFT_HOURLY,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider._ANY,
            "_ANY");
    when(repo.save(any())).then(invocation -> invocation.getArgument(0));
    AccountUsageCalculation day1Usage = createAccountUsageCalculation(usageKey, 4.0);
    AccountUsageCalculation day2Usage = createAccountUsageCalculation(usageKey, 3.0);
    combiningRollupSnapshotStrategy.produceSnapshotsFromCalculations(
        "account123",
        new DateRange(
            OffsetDateTime.parse("2021-02-24T12:00:00Z"),
            OffsetDateTime.parse("2021-02-26T12:00:00Z")),
        tagProfile.getTagsWithPrometheusEnabledLookup(),
        Map.of(hourlyTimestamp1, day1Usage, hourlyTimestamp2, day2Usage),
        Granularity.HOURLY,
        Double::sum);

    TallySnapshot day1HourlySnapshot =
        createTallySnapshot(Granularity.HOURLY, hourlyTimestamp1, 4.0);
    TallySnapshot day2HourlySnapshot =
        createTallySnapshot(Granularity.HOURLY, hourlyTimestamp2, 3.0);
    TallySnapshot dailySnapshot1 = createTallySnapshot(Granularity.DAILY, dailyTimestamp1, 4.0);
    TallySnapshot dailySnapshot2 = createTallySnapshot(Granularity.DAILY, dailyTimestamp2, 3.0);

    ArgumentCaptor<TallySnapshot> talliesSavedCaptor = ArgumentCaptor.forClass(TallySnapshot.class);
    verify(repo, times(4)).save(talliesSavedCaptor.capture());
    List<TallySnapshot> talliesSaved = talliesSavedCaptor.getAllValues();
    TallySnapshot actual1 =
        talliesSaved.stream()
            .filter(
                s ->
                    s.getGranularity() == Granularity.DAILY
                        && s.getSnapshotDate().isEqual(dailyTimestamp1))
            .findFirst()
            .orElseThrow();
    TallySnapshot actual2 =
        talliesSaved.stream()
            .filter(
                s ->
                    s.getGranularity() == Granularity.DAILY
                        && s.getSnapshotDate().isEqual(dailyTimestamp2))
            .findFirst()
            .orElseThrow();
    assertThat(
        talliesSaved,
        containsInAnyOrder(day1HourlySnapshot, day2HourlySnapshot, dailySnapshot1, dailySnapshot2));
    assertEquals(4.0, actual1.getMeasurement(HardwareMeasurementType.TOTAL, Measurement.Uom.CORES));
    assertEquals(
        4.0, actual1.getMeasurement(HardwareMeasurementType.PHYSICAL, Measurement.Uom.CORES));
    assertEquals(3.0, actual2.getMeasurement(HardwareMeasurementType.TOTAL, Measurement.Uom.CORES));
    assertEquals(
        3.0, actual2.getMeasurement(HardwareMeasurementType.PHYSICAL, Measurement.Uom.CORES));
  }

  @Test
  void testUpdatesExistingHourlySnapshots() {
    TallySnapshot noonSnapshot =
        createTallySnapshot(Granularity.HOURLY, "2021-02-25T12:00:00Z", 4.0);
    noonSnapshot.setId(UUID.randomUUID());
    when(repo.findByAccountNumberAndProductIdInAndGranularityAndSnapshotDateBetween(
            any(), any(), eq(Granularity.HOURLY), any(), any()))
        .thenReturn(Stream.of(noonSnapshot));
    when(repo.findByAccountNumberAndProductIdInAndGranularityAndSnapshotDateBetween(
            any(), any(), eq(Granularity.DAILY), any(), any()))
        .thenReturn(Stream.empty());
    UsageCalculation.Key usageKey =
        new UsageCalculation.Key(
            OPEN_SHIFT_HOURLY,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider._ANY,
            "_ANY");
    when(repo.save(any())).then(invocation -> invocation.getArgument(0));
    AccountUsageCalculation noonUsage = createAccountUsageCalculation(usageKey, 4.0);
    AccountUsageCalculation afternoonUsage = createAccountUsageCalculation(usageKey, 3.0);
    combiningRollupSnapshotStrategy.produceSnapshotsFromCalculations(
        "account123",
        new DateRange(
            OffsetDateTime.parse("2021-02-24T12:00:00Z"),
            OffsetDateTime.parse("2021-02-26T12:00:00Z")),
        tagProfile.getTagsWithPrometheusEnabledLookup(),
        Map.of(
            OffsetDateTime.parse("2021-02-25T12:00:00Z"),
            noonUsage,
            OffsetDateTime.parse("2021-02-25T13:00:00Z"),
            afternoonUsage),
        Granularity.HOURLY,
        Double::sum);

    TallySnapshot afternoonSnapshot =
        createTallySnapshot(Granularity.HOURLY, "2021-02-25T13:00:00Z", 3.0);
    TallySnapshot dailySnapshot =
        createTallySnapshot(Granularity.DAILY, "2021-02-25T00:00:00Z", 7.0);

    ArgumentCaptor<TallySnapshot> talliesSavedCaptor = ArgumentCaptor.forClass(TallySnapshot.class);
    verify(repo, times(3)).save(talliesSavedCaptor.capture());
    List<TallySnapshot> talliesSaved = talliesSavedCaptor.getAllValues();
    TallySnapshot actual =
        talliesSaved.stream()
            .filter(s -> Objects.equals(noonSnapshot.getId(), s.getId()))
            .findFirst()
            .orElse(null);
    assertThat(talliesSaved, containsInAnyOrder(noonSnapshot, afternoonSnapshot, dailySnapshot));
    assertNotNull(actual);
  }

  @Test
  void testUpdatesExistingRollupSnapshots() {
    TallySnapshot dailySnapshot =
        createTallySnapshot(Granularity.DAILY, "2021-02-25T00:00:00Z", 7.0);
    dailySnapshot.setId(UUID.randomUUID());
    when(repo.findByAccountNumberAndProductIdInAndGranularityAndSnapshotDateBetween(
            any(), any(), eq(Granularity.HOURLY), any(), any()))
        .thenReturn(Stream.empty());
    when(repo.findByAccountNumberAndProductIdInAndGranularityAndSnapshotDateBetween(
            any(), any(), eq(Granularity.DAILY), any(), any()))
        .thenReturn(Stream.of(dailySnapshot));
    UsageCalculation.Key usageKey =
        new UsageCalculation.Key(
            OPEN_SHIFT_HOURLY,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider._ANY,
            "_ANY");
    when(repo.save(any())).then(invocation -> invocation.getArgument(0));

    AccountUsageCalculation noonUsage = createAccountUsageCalculation(usageKey, 4.0);
    AccountUsageCalculation afternoonUsage = createAccountUsageCalculation(usageKey, 3.0);

    combiningRollupSnapshotStrategy.produceSnapshotsFromCalculations(
        "account123",
        new DateRange(
            OffsetDateTime.parse("2021-02-24T12:00:00Z"),
            OffsetDateTime.parse("2021-02-26T12:00:00Z")),
        tagProfile.getTagsWithPrometheusEnabledLookup(),
        Map.of(
            OffsetDateTime.parse("2021-02-25T12:00:00Z"),
            noonUsage,
            OffsetDateTime.parse("2021-02-25T13:00:00Z"),
            afternoonUsage),
        Granularity.HOURLY,
        Double::sum);

    TallySnapshot noonSnapshot =
        createTallySnapshot(Granularity.HOURLY, "2021-02-25T12:00:00Z", 4.0);
    TallySnapshot afternoonSnapshot =
        createTallySnapshot(Granularity.HOURLY, "2021-02-25T13:00:00Z", 3.0);

    ArgumentCaptor<TallySnapshot> talliesSavedCaptor = ArgumentCaptor.forClass(TallySnapshot.class);
    verify(repo, times(3)).save(talliesSavedCaptor.capture());
    List<TallySnapshot> talliesSaved = talliesSavedCaptor.getAllValues();
    TallySnapshot actual =
        talliesSaved.stream()
            .filter(s -> Objects.equals(dailySnapshot.getId(), s.getId()))
            .findFirst()
            .orElse(null);
    assertThat(talliesSaved, containsInAnyOrder(noonSnapshot, afternoonSnapshot, dailySnapshot));
    assertNotNull(actual);
  }

  @Test
  void testFinestGranularitySnapsZeroOutOldMeasurementsWhenDataNoLongerFound() {
    TallySnapshot noonSnapshot =
        createTallySnapshot(Granularity.HOURLY, "2021-02-25T12:00:00Z", 4.0);
    TallySnapshot afternoonSnapshot =
        createTallySnapshot(Granularity.HOURLY, "2021-02-25T13:00:00Z", 3.0);
    TallySnapshot dailySnapshot =
        createTallySnapshot(Granularity.DAILY, "2021-02-25T00:00:00Z", 7.0);

    when(repo.findByAccountNumberAndProductIdInAndGranularityAndSnapshotDateBetween(
            any(), any(), eq(Granularity.HOURLY), any(), any()))
        .thenReturn(Stream.of(noonSnapshot, afternoonSnapshot));

    when(repo.findByAccountNumberAndProductIdInAndGranularityAndSnapshotDateBetween(
            any(), any(), eq(Granularity.DAILY), any(), any()))
        .thenReturn(Stream.of(dailySnapshot));

    when(repo.save(any())).then(invocation -> invocation.getArgument(0));

    UsageCalculation.Key usageKey =
        new UsageCalculation.Key(
            OPEN_SHIFT_HOURLY,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider._ANY,
            "_ANY");

    AccountUsageCalculation afternoonUsage = createAccountUsageCalculation(usageKey, 3.0);

    combiningRollupSnapshotStrategy.produceSnapshotsFromCalculations(
        "account123",
        new DateRange(
            OffsetDateTime.parse("2021-02-24T12:00:00Z"),
            OffsetDateTime.parse("2021-02-26T12:00:00Z")),
        tagProfile.getTagsWithPrometheusEnabledLookup(),
        Map.of(OffsetDateTime.parse("2021-02-25T13:00:00Z"), afternoonUsage),
        Granularity.HOURLY,
        Double::sum);

    ArgumentCaptor<TallySnapshot> tallySaveCapture = ArgumentCaptor.forClass(TallySnapshot.class);
    // 1 - noon snapshot that was reset
    // 1 - afternoonSnapshot that was updated.
    // 1 - daily snapshot that was updated.
    verify(repo, times(3)).save(tallySaveCapture.capture());

    List<TallySnapshot> talliesSaved = tallySaveCapture.getAllValues();
    assertThat(talliesSaved, containsInAnyOrder(noonSnapshot, afternoonSnapshot, dailySnapshot));

    // Any hourly tallies that were not represented by a calculation should have been reset.
    noonSnapshot.getTallyMeasurements().values().forEach(v -> assertEquals(0.0, v));

    // Afternoon snapshot measurements should reflect the calculation.
    afternoonSnapshot.getTallyMeasurements().values().forEach(v -> assertEquals(3.0, v));

    // Daily snapshot should total only the after noon tally since we did not include
    // the afternoon tally in the calculation.
    dailySnapshot.getTallyMeasurements().values().forEach(v -> assertEquals(3.0, v));
  }

  private AccountUsageCalculation createAccountUsageCalculation(
      UsageCalculation.Key usageKey, double v) {
    AccountUsageCalculation usage = new AccountUsageCalculation("account123");
    usage.addUsage(usageKey, HardwareMeasurementType.PHYSICAL, Measurement.Uom.CORES, v);
    usage.getProducts().add(OPEN_SHIFT_HOURLY);

    return usage;
  }

  private TallySnapshot createTallySnapshot(
      Granularity granularity, String snapshotDate, double value) {
    return createTallySnapshot(granularity, OffsetDateTime.parse(snapshotDate), value);
  }

  private TallySnapshot createTallySnapshot(
      Granularity granularity, OffsetDateTime snapshotDate, double value) {
    Map<TallyMeasurementKey, Double> measurements = new HashMap<>();
    measurements.put(
        new TallyMeasurementKey(HardwareMeasurementType.PHYSICAL, Measurement.Uom.CORES), value);
    measurements.put(
        new TallyMeasurementKey(HardwareMeasurementType.TOTAL, Measurement.Uom.CORES), value);
    return TallySnapshot.builder()
        .snapshotDate(snapshotDate)
        .productId(OPEN_SHIFT_HOURLY)
        .accountNumber("account123")
        .tallyMeasurements(measurements)
        .granularity(granularity)
        .serviceLevel(ServiceLevel.PREMIUM)
        .usage(Usage.PRODUCTION)
        .billingProvider(BillingProvider._ANY)
        .billingAccountId("_ANY")
        .build();
  }
}
