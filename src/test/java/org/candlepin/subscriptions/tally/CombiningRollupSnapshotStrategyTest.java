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

import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import com.redhat.swatch.configuration.registry.Variant;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.*;
import org.candlepin.subscriptions.util.DateRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"worker", "test"})
class CombiningRollupSnapshotStrategyTest {

  private static final String OPEN_SHIFT_HOURLY = "OpenShift Hourly";

  @Autowired CombiningRollupSnapshotStrategy combiningRollupSnapshotStrategy;

  @MockBean TallySnapshotRepository repo;

  private Set<String> tagsWithPrometheusEnabled;

  @BeforeEach
  void setUp() {
    tagsWithPrometheusEnabled =
        SubscriptionDefinition.getSubscriptionDefinitions().stream()
            .filter(SubscriptionDefinition::isPrometheusEnabled)
            .flatMap(subDef -> subDef.getVariants().stream())
            .map(Variant::getTag)
            .collect(Collectors.toSet());
  }

  @Test
  void testConsecutiveHoursAddedTogether() {
    when(repo.findByOrgIdAndProductIdInAndGranularityAndSnapshotDateBetween(
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
        "org123",
        new DateRange(
            OffsetDateTime.parse("2021-02-24T12:00:00Z"),
            OffsetDateTime.parse("2021-02-26T12:00:00Z")),
        tagsWithPrometheusEnabled,
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
    assertEquals(
        7.0, actual.getMeasurement(HardwareMeasurementType.TOTAL, MetricIdUtils.getCores()));
    assertEquals(
        7.0, actual.getMeasurement(HardwareMeasurementType.PHYSICAL, MetricIdUtils.getCores()));
  }

  @Test
  void testProducesMultipleDailyWhenNecessary() {
    OffsetDateTime hourlyTimestamp1 = OffsetDateTime.parse("2021-02-25T11:00:00Z");
    OffsetDateTime hourlyTimestamp2 = OffsetDateTime.parse("2021-02-26T11:00:00Z");
    OffsetDateTime dailyTimestamp1 = OffsetDateTime.parse("2021-02-25T00:00:00Z");
    OffsetDateTime dailyTimestamp2 = OffsetDateTime.parse("2021-02-26T00:00:00Z");
    when(repo.findByOrgIdAndProductIdInAndGranularityAndSnapshotDateBetween(
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
        "org123",
        new DateRange(
            OffsetDateTime.parse("2021-02-24T12:00:00Z"),
            OffsetDateTime.parse("2021-02-26T12:00:00Z")),
        tagsWithPrometheusEnabled,
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
    assertEquals(
        4.0, actual1.getMeasurement(HardwareMeasurementType.TOTAL, MetricIdUtils.getCores()));
    assertEquals(
        4.0, actual1.getMeasurement(HardwareMeasurementType.PHYSICAL, MetricIdUtils.getCores()));
    assertEquals(
        3.0, actual2.getMeasurement(HardwareMeasurementType.TOTAL, MetricIdUtils.getCores()));
    assertEquals(
        3.0, actual2.getMeasurement(HardwareMeasurementType.PHYSICAL, MetricIdUtils.getCores()));
  }

  @Test
  void testUpdatesExistingHourlySnapshots() {
    TallySnapshot noonSnapshot =
        createTallySnapshot(Granularity.HOURLY, "2021-02-25T12:00:00Z", 4.0);
    noonSnapshot.setId(UUID.randomUUID());
    when(repo.findByOrgIdAndProductIdInAndGranularityAndSnapshotDateBetween(
            any(), any(), eq(Granularity.HOURLY), any(), any()))
        .thenReturn(Stream.of(noonSnapshot));
    when(repo.findByOrgIdAndProductIdInAndGranularityAndSnapshotDateBetween(
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
        "org123",
        new DateRange(
            OffsetDateTime.parse("2021-02-24T12:00:00Z"),
            OffsetDateTime.parse("2021-02-26T12:00:00Z")),
        tagsWithPrometheusEnabled,
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
    when(repo.findByOrgIdAndProductIdInAndGranularityAndSnapshotDateBetween(
            any(), any(), eq(Granularity.HOURLY), any(), any()))
        .thenReturn(Stream.empty());
    when(repo.findByOrgIdAndProductIdInAndGranularityAndSnapshotDateBetween(
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
        "org123",
        new DateRange(
            OffsetDateTime.parse("2021-02-24T12:00:00Z"),
            OffsetDateTime.parse("2021-02-26T12:00:00Z")),
        tagsWithPrometheusEnabled,
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

    when(repo.findByOrgIdAndProductIdInAndGranularityAndSnapshotDateBetween(
            any(), any(), eq(Granularity.HOURLY), any(), any()))
        .thenReturn(Stream.of(noonSnapshot, afternoonSnapshot));

    when(repo.findByOrgIdAndProductIdInAndGranularityAndSnapshotDateBetween(
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
        "org123",
        new DateRange(
            OffsetDateTime.parse("2021-02-24T12:00:00Z"),
            OffsetDateTime.parse("2021-02-26T12:00:00Z")),
        tagsWithPrometheusEnabled,
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

  @Test
  void testNaturalKeyFilteringWhileUpdatingAndDeletingExistingSnapshots() {
    // Create a snapshot based on default usage key setup.
    TallySnapshot existingHourlySnapshot1 =
        createTallySnapshot(Granularity.HOURLY, "2021-02-25T12:00:00Z", 4.0);
    existingHourlySnapshot1.setId(UUID.randomUUID());

    TallySnapshot existingHourlySnapshot2 =
        createTallySnapshot(Granularity.HOURLY, "2021-02-25T12:00:00Z", 6.0);
    existingHourlySnapshot2.setId(UUID.randomUUID());
    existingHourlySnapshot2.setBillingProvider(BillingProvider.AWS);

    // This snapshot will no longer be reported.
    TallySnapshot existingHourlySnapshot3 =
        createTallySnapshot(Granularity.HOURLY, "2021-02-25T13:00:00Z", 10.0);
    existingHourlySnapshot3.setId(UUID.randomUUID());
    existingHourlySnapshot3.setBillingProvider(BillingProvider.AWS);

    when(repo.findByOrgIdAndProductIdInAndGranularityAndSnapshotDateBetween(
            any(), any(), eq(Granularity.HOURLY), any(), any()))
        .thenReturn(
            Stream.of(existingHourlySnapshot1, existingHourlySnapshot2, existingHourlySnapshot3));

    TallySnapshot existingDailySnapshot1 =
        createTallySnapshot(Granularity.DAILY, "2021-02-25T00:00:00Z", 4.0);
    existingDailySnapshot1.setId(UUID.randomUUID());

    TallySnapshot existingDailySnapshot2 =
        createTallySnapshot(Granularity.DAILY, "2021-02-25T00:00:00Z", 16.0);
    existingDailySnapshot2.setId(UUID.randomUUID());
    existingDailySnapshot2.setBillingProvider(BillingProvider.AWS);
    when(repo.findByOrgIdAndProductIdInAndGranularityAndSnapshotDateBetween(
            any(), any(), eq(Granularity.DAILY), any(), any()))
        .thenReturn(Stream.of(existingDailySnapshot1, existingDailySnapshot2));

    UsageCalculation.Key usageKey =
        new UsageCalculation.Key(
            OPEN_SHIFT_HOURLY,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider.AWS,
            "awsAccount1");
    UsageCalculation.Key snapUsageKey1 =
        UsageCalculation.Key.fromTallySnapshot(existingHourlySnapshot1);
    UsageCalculation.Key snapUsageKey2 =
        UsageCalculation.Key.fromTallySnapshot(existingHourlySnapshot2);

    AccountUsageCalculation accountCalc = createAccountUsageCalculation(snapUsageKey1, 1.0);
    accountCalc.addUsage(
        snapUsageKey2, HardwareMeasurementType.PHYSICAL, MetricIdUtils.getCores(), 2.0);

    when(repo.save(any())).then(invocation -> invocation.getArgument(0));

    combiningRollupSnapshotStrategy.produceSnapshotsFromCalculations(
        "org123",
        new DateRange(
            OffsetDateTime.parse("2021-02-24T12:00:00Z"),
            OffsetDateTime.parse("2021-02-26T12:00:00Z")),
        tagsWithPrometheusEnabled,
        Map.of(existingHourlySnapshot1.getSnapshotDate(), accountCalc),
        Granularity.HOURLY,
        Double::sum);

    // Daily rolled snaphot
    TallySnapshot expectedHourly1 =
        createTallySnapshot(Granularity.HOURLY, "2021-02-25T12:00:00Z", 1.0);
    expectedHourly1.setId(existingHourlySnapshot1.getId());
    TallySnapshot expectedDaily1 =
        createTallySnapshot(Granularity.DAILY, "2021-02-25T00:00:00Z", 1.0);
    expectedDaily1.setId(existingDailySnapshot1.getId());

    TallySnapshot expectedHourly2 =
        createTallySnapshot(Granularity.HOURLY, "2021-02-25T12:00:00Z", 2.0);
    expectedHourly2.setId(existingHourlySnapshot2.getId());
    expectedHourly2.setBillingProvider(existingHourlySnapshot2.getBillingProvider());

    TallySnapshot expectedHourly3 =
        createTallySnapshot(Granularity.HOURLY, "2021-02-25T13:00:00Z", 0.0);
    // Was not included in update, so tally measurements should be cleared.
    expectedHourly3.getTallyMeasurements().clear();
    expectedHourly3.setId(existingHourlySnapshot3.getId());
    expectedHourly3.setBillingProvider(existingHourlySnapshot3.getBillingProvider());

    TallySnapshot expectedDaily2 =
        createTallySnapshot(Granularity.DAILY, "2021-02-25T00:00:00Z", 2.0);
    expectedDaily2.setBillingProvider(existingDailySnapshot2.getBillingProvider());
    expectedDaily2.setId(existingDailySnapshot2.getId());

    ArgumentCaptor<TallySnapshot> talliesSavedCaptor = ArgumentCaptor.forClass(TallySnapshot.class);
    verify(repo, times(5)).save(talliesSavedCaptor.capture());
    List<TallySnapshot> talliesSaved = talliesSavedCaptor.getAllValues();
    assertThat(
        talliesSaved,
        containsInAnyOrder(
            expectedHourly1, expectedHourly2, expectedHourly3, expectedDaily1, expectedDaily2));
  }

  @Test
  void testFinestGranularitySnapshotFilteredByDateRange() {
    when(repo.findByOrgIdAndProductIdInAndGranularityAndSnapshotDateBetween(
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
    Map<String, List<TallySnapshot>> talliesToSendByAccount =
        combiningRollupSnapshotStrategy.produceSnapshotsFromCalculations(
            "org123",
            new DateRange(
                OffsetDateTime.parse("2021-02-25T13:00:00Z"),
                OffsetDateTime.parse("2021-02-25T14:00:00Z")),
            tagsWithPrometheusEnabled,
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

    assertEquals(1, talliesToSendByAccount.keySet().size());
    assertTrue(talliesToSendByAccount.containsKey("org123"));

    List<TallySnapshot> talliesToSend = talliesToSendByAccount.get("org123");
    assertEquals(2, talliesToSend.size());
    assertThat(talliesToSend, containsInAnyOrder(afternoonSnapshot, dailySnapshot));
  }

  @Test
  void testExistingOlderFinestGranularitySnapshotMeasurementsPreserved() {
    TallySnapshot existingSnapshot =
        createTallySnapshot(Granularity.HOURLY, "2022-10-24T12:00:00Z", 4.0);
    when(repo.findByOrgIdAndProductIdInAndGranularityAndSnapshotDateBetween(
            any(), any(), any(), any(), any()))
        .then(invocation -> Stream.of(existingSnapshot));
    combiningRollupSnapshotStrategy.produceSnapshotsFromCalculations(
        "org123",
        new DateRange(
            OffsetDateTime.parse("2022-10-24T13:00:00Z"),
            OffsetDateTime.parse("2022-10-24T14:00:00Z")),
        tagsWithPrometheusEnabled,
        Map.of(),
        Granularity.HOURLY,
        Double::sum);
    assertEquals(
        4.0,
        existingSnapshot.getMeasurement(
            HardwareMeasurementType.PHYSICAL, MetricIdUtils.getCores()));
  }

  @Test
  void testExistingNewerFinestGranularitySnapshotMeasurementsPreserved() {
    TallySnapshot existingSnapshot =
        createTallySnapshot(Granularity.HOURLY, "2022-10-24T14:00:00Z", 4.0);
    when(repo.findByOrgIdAndProductIdInAndGranularityAndSnapshotDateBetween(
            any(), any(), any(), any(), any()))
        .then(invocation -> Stream.of(existingSnapshot));
    combiningRollupSnapshotStrategy.produceSnapshotsFromCalculations(
        "org123",
        new DateRange(
            OffsetDateTime.parse("2022-10-24T13:00:00Z"),
            OffsetDateTime.parse("2022-10-24T14:00:00Z")),
        tagsWithPrometheusEnabled,
        Map.of(),
        Granularity.HOURLY,
        Double::sum);
    assertEquals(
        4.0,
        existingSnapshot.getMeasurement(
            HardwareMeasurementType.PHYSICAL, MetricIdUtils.getCores()));
  }

  @Test
  void testFinestGranularitySnapshotClearedWhenUsageNotPresent() {
    TallySnapshot existingSnapshot =
        createTallySnapshot(Granularity.HOURLY, "2022-10-24T13:00:00Z", 4.0);
    when(repo.findByOrgIdAndProductIdInAndGranularityAndSnapshotDateBetween(
            any(), any(), any(), any(), any()))
        .then(invocation -> Stream.of(existingSnapshot));
    when(repo.save(any())).then(invocation -> invocation.getArgument(0));
    combiningRollupSnapshotStrategy.produceSnapshotsFromCalculations(
        "org123",
        new DateRange(
            OffsetDateTime.parse("2022-10-24T13:00:00Z"),
            OffsetDateTime.parse("2022-10-24T14:00:00Z")),
        tagsWithPrometheusEnabled,
        Map.of(),
        Granularity.HOURLY,
        Double::sum);
    assertTrue(existingSnapshot.getTallyMeasurements().isEmpty());
  }

  private AccountUsageCalculation createAccountUsageCalculation(
      UsageCalculation.Key usageKey, double v) {
    AccountUsageCalculation usage = new AccountUsageCalculation("org123");
    usage.addUsage(usageKey, HardwareMeasurementType.PHYSICAL, MetricIdUtils.getCores(), v);
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
        new TallyMeasurementKey(
            HardwareMeasurementType.PHYSICAL, MetricIdUtils.getCores().toString()),
        value);
    measurements.put(
        new TallyMeasurementKey(HardwareMeasurementType.TOTAL, MetricIdUtils.getCores().toString()),
        value);
    return TallySnapshot.builder()
        .snapshotDate(snapshotDate)
        .productId(OPEN_SHIFT_HOURLY)
        .orgId("org123")
        .tallyMeasurements(measurements)
        .granularity(granularity)
        .serviceLevel(ServiceLevel.PREMIUM)
        .usage(Usage.PRODUCTION)
        .billingProvider(BillingProvider._ANY)
        .billingAccountId("_ANY")
        .build();
  }
}
