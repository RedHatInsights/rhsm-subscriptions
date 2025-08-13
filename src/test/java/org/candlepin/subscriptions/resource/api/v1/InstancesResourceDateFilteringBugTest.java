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
package org.candlepin.subscriptions.resource.api.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redhat.swatch.configuration.registry.ProductId;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.candlepin.subscriptions.db.HostRepository;
import org.candlepin.subscriptions.db.OrgConfigRepository;
import org.candlepin.subscriptions.db.TallyInstanceViewRepository;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.TallyInstancePaygView;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.security.WithMockRedHatPrincipal;
import org.candlepin.subscriptions.utilization.api.v1.model.InstanceResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Test class to demonstrate the date filtering bug for OpenShift products.
 *
 * <p>The bug: Date filtering only works for products where isPayg() returns true.
 * OpenShift-dedicated-metrics should be PAYG (it has rhmMetricId) but the original isPaygEligible()
 * method was missing azureDimension check, potentially causing it to return false and bypass date
 * filtering.
 */
@SpringBootTest
@ActiveProfiles({"api", "test"})
class InstancesResourceDateFilteringBugTest {

  private static final ProductId OPENSHIFT_DEDICATED_METRICS =
      ProductId.fromString("OpenShift-dedicated-metrics");
  private static final ProductId OPENSHIFT_METRICS = ProductId.fromString("OpenShift-metrics");
  private static final String ORG_ID = "owner123456";

  @MockitoBean TallyInstanceViewRepository repository;
  @MockitoBean OrgConfigRepository orgConfigRepository;
  @MockitoBean HostRepository hostRepository;
  @Autowired InstancesResource resource;

  private OffsetDateTime currentMonth;

  @Transactional
  @BeforeEach
  void setup() {
    when(orgConfigRepository.existsByOrgId(ORG_ID)).thenReturn(true);

    // Set up dates for testing
    currentMonth = OffsetDateTime.of(2024, 3, 15, 10, 0, 0, 0, ZoneOffset.UTC);
  }

  // This test demonstrates the date filtering bug
  @WithMockRedHatPrincipal("123456")
  @ParameterizedTest
  @MethodSource("provideOpenShiftProductsAndDateRanges")
  void testOpenShiftProductDateFilteringBug(
      ProductId product,
      OffsetDateTime startDate,
      OffsetDateTime endDate,
      String expectedInstanceId) {

    // Create test data with different dates within the same month
    var earlyMonthInstance = createPaygInstanceView("early-month-instance");
    var midMonthInstance = createPaygInstanceView("mid-month-instance");
    var lateMonthInstance = createPaygInstanceView("late-month-instance");

    // Set different dates for the instances within the same month
    earlyMonthInstance.setLastSeen(currentMonth.withDayOfMonth(1));
    midMonthInstance.setLastSeen(currentMonth.withDayOfMonth(15));
    lateMonthInstance.setLastSeen(currentMonth.withDayOfMonth(28));

    // Mock repository to return filtered results based on date range
    // This simulates what the repository should do when date filtering is working
    when(repository.findAllBy(
            eq(ORG_ID),
            eq(product),
            any(ServiceLevel.class),
            any(Usage.class),
            any(),
            any(),
            any(),
            eq("2024-03"), // month parameter for March
            any(),
            any(BillingProvider.class),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()))
        .thenReturn(
            new PageImpl<>(List.of(earlyMonthInstance, midMonthInstance, lateMonthInstance)));

    InstanceResponse response =
        resource.getInstancesByProduct(
            product, null, null, null, null, null, null, null, null, null,
            startDate, // beginning - from parameter
            endDate, // ending - from parameter
            null, null);

    // EXPECTED BEHAVIOR:
    // The date range should return only instances within that specific range

    // Currently the bug causes all instances to be returned regardless of date range
    // When fixed, this should return only the expected instance
    assertThat(response.getData())
        .as(
            "Date range request ("
                + startDate
                + " to "
                + endDate
                + ") should only return instances from that date range")
        .hasSize(1);
    assertThat(response.getData().get(0).getInstanceId())
        .as("Date range request should return " + expectedInstanceId)
        .isEqualTo(expectedInstanceId);

    // Verify repository call with proper date filtering
    verify(repository, times(1))
        .findAllBy(
            eq(ORG_ID),
            eq(product),
            any(ServiceLevel.class),
            any(Usage.class),
            any(),
            any(),
            any(),
            eq("2024-03"), // month parameter should be consistent for same month
            any(),
            any(BillingProvider.class),
            any(),
            any(),
            any(),
            any(),
            any(),
            any());

    System.out.println("=== DATE FILTERING TEST FOR " + product + " ===");
    System.out.println("✓ Date range: " + startDate + " to " + endDate);
    System.out.println("✓ Expected instance: " + expectedInstanceId);
    System.out.println("✓ Actual instance: " + response.getData().get(0).getInstanceId());
  }

  private TallyInstancePaygView createPaygInstanceView(String instanceId) {
    var view = new TallyInstancePaygView();
    view.setId("host-" + instanceId);
    view.setDisplayName("test-host-" + instanceId);
    view.setInventoryId(UUID.randomUUID().toString());
    view.getKey().setInstanceId(instanceId);
    view.setHostBillingProvider(BillingProvider.AWS);
    view.getKey().setMeasurementType(HardwareMeasurementType.AWS);
    view.setMetrics(Map.of(MetricIdUtils.getCores(), 4.0));
    view.setCores(4);
    view.setLastSeen(currentMonth);
    return view;
  }

  private static Stream<Arguments> provideOpenShiftProductsAndDateRanges() {
    return Stream.of(
        // OpenShift-dedicated-metrics with different date ranges
        Arguments.of(
            OPENSHIFT_DEDICATED_METRICS,
            OffsetDateTime.of(2024, 3, 1, 10, 0, 0, 0, ZoneOffset.UTC),
            OffsetDateTime.of(2024, 3, 5, 10, 0, 0, 0, ZoneOffset.UTC),
            "early-month-instance"),
        Arguments.of(
            OPENSHIFT_DEDICATED_METRICS,
            OffsetDateTime.of(2024, 3, 10, 10, 0, 0, 0, ZoneOffset.UTC),
            OffsetDateTime.of(2024, 3, 20, 10, 0, 0, 0, ZoneOffset.UTC),
            "mid-month-instance"),
        Arguments.of(
            OPENSHIFT_DEDICATED_METRICS,
            OffsetDateTime.of(2024, 3, 25, 10, 0, 0, 0, ZoneOffset.UTC),
            OffsetDateTime.of(2024, 3, 31, 10, 0, 0, 0, ZoneOffset.UTC),
            "late-month-instance"),
        // OpenShift-metrics with different date ranges
        Arguments.of(
            OPENSHIFT_METRICS,
            OffsetDateTime.of(2024, 3, 1, 10, 0, 0, 0, ZoneOffset.UTC),
            OffsetDateTime.of(2024, 3, 15, 10, 0, 0, 0, ZoneOffset.UTC),
            "early-month-instance"),
        Arguments.of(
            OPENSHIFT_METRICS,
            OffsetDateTime.of(2024, 3, 16, 10, 0, 0, 0, ZoneOffset.UTC),
            OffsetDateTime.of(2024, 3, 31, 10, 0, 0, 0, ZoneOffset.UTC),
            "late-month-instance"));
  }
}
