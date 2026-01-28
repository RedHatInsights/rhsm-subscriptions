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
package tests;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.contract.test.model.CapacityReportByMetricId;
import com.redhat.swatch.contract.test.model.GranularityType;
import com.redhat.swatch.contract.test.model.PageLinks;
import domain.Product;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class CapacityReportPaginationComponentTest extends BaseContractComponentTest {

  private static final int DATA_POINTS_COUNT = 100;
  private static final int PAGE_SIZE = 10;
  private static final int DAYS_TO_QUERY = DATA_POINTS_COUNT - 1; // 99 days to get 100 data points

  @Disabled(value = "Test disabled due to pagination bug to be fixed in SWATCH-4520.")
  @TestPlanName("capacity-report-pagination-TC001")
  @Test
  void shouldGeneratePaginatedCapacityReport() {
    // Given: Generate capacity data with 100 data points (using daily granularity for 100 days)
    final String testSku = RandomUtils.generateRandom();
    final double coresCapacity = 8.0;
    final OffsetDateTime beginning = clock.startOfToday().minusDays(DAYS_TO_QUERY);
    final OffsetDateTime ending = clock.endOfToday();

    givenSubscriptionWithCoresCapacityAndDates(testSku, coresCapacity, beginning, ending);

    final int offset = 0;
    final int limit = PAGE_SIZE;

    // When: Get capacity with offset=0, limit=10
    CapacityReportByMetricId capacityReport =
        service.getCapacityReportByMetricId(
            Product.OPENSHIFT,
            orgId,
            CORES.toString(),
            beginning,
            ending,
            GranularityType.DAILY,
            offset,
            limit);

    // Then: Response contains 10 data points
    assertThat("Capacity report should not be null", capacityReport, notNullValue());
    assertThat("Data should contain exactly 10 items", capacityReport.getData(), hasSize(limit));

    // Then: Links object populated with first, last, next, previous
    PageLinks links = capacityReport.getLinks();
    assertThat("Links should not be null", links, notNullValue());
    assertThat("First link should be present", links.getFirst(), notNullValue());
    assertThat("Last link should be present", links.getLast(), notNullValue());
    assertThat("Next link should be present", links.getNext(), notNullValue());

    assertTrue(links.getFirst().contains("offset=0"), "First link should contain offset=0");
    assertTrue(links.getNext().contains("offset=10"), "Next link should contain offset=10");

    // Then: Meta.count = 100
    assertThat("Meta should not be null", capacityReport.getMeta(), notNullValue());
    assertThat(
        "Meta count should be 100",
        capacityReport.getMeta().getCount(),
        equalTo(DATA_POINTS_COUNT));
  }

  @Disabled(value = "Test disabled due to pagination bug to be fixed in SWATCH-4520.")
  @TestPlanName("capacity-report-pagination-TC002")
  @Test
  void shouldNavigatePaginationLinks() {
    // Given: Generate capacity data with 100 data points
    final String testSku = RandomUtils.generateRandom();
    final double coresCapacity = 4.0;
    final OffsetDateTime beginning = clock.startOfToday().minusDays(DAYS_TO_QUERY);
    final OffsetDateTime ending = clock.endOfToday();

    givenSubscriptionWithCoresCapacityAndDates(testSku, coresCapacity, beginning, ending);

    // When: Get first page (offset=0, limit=10)
    CapacityReportByMetricId firstPage =
        service.getCapacityReportByMetricId(
            Product.OPENSHIFT,
            orgId,
            CORES.toString(),
            beginning,
            ending,
            GranularityType.DAILY,
            0,
            PAGE_SIZE);

    // Then: Verify first page structure
    assertThat("First page should not be null", firstPage, notNullValue());
    assertThat("First page should have 10 data points", firstPage.getData(), hasSize(PAGE_SIZE));
    PageLinks firstPageLinks = firstPage.getLinks();
    assertThat("First page links should not be null", firstPageLinks, notNullValue());
    assertThat(
        "Next link should be present on first page", firstPageLinks.getNext(), notNullValue());

    // When: Follow the "next" link (offset=10, limit=10)
    CapacityReportByMetricId secondPage =
        service.getCapacityReportByMetricId(
            Product.OPENSHIFT,
            orgId,
            CORES.toString(),
            beginning,
            ending,
            GranularityType.DAILY,
            10,
            PAGE_SIZE);

    // Then: Second page has offset=10
    assertThat("Second page should not be null", secondPage, notNullValue());
    assertThat("Second page should have 10 data points", secondPage.getData(), hasSize(PAGE_SIZE));

    // Then: Different data points returned
    assertThat(
        "First page data should not equal second page data",
        firstPage.getData(),
        not(equalTo(secondPage.getData())));

    // Then: The previous link points to the first page
    PageLinks secondPageLinks = secondPage.getLinks();
    assertThat("Second page links should not be null", secondPageLinks, notNullValue());
    assertThat(
        "Previous link should be present on second page",
        secondPageLinks.getPrevious(),
        notNullValue());
    assertTrue(
        secondPageLinks.getPrevious().contains("offset=0"),
        "Previous link should point to first page with offset=0");
  }

  @TestPlanName("capacity-report-pagination-TC003")
  @Test
  void shouldHandleLastPagePagination() {
    // Given: Generate capacity data with 100 data points
    final String testSku = RandomUtils.generateRandom();
    final double coresCapacity = 6.0;
    final OffsetDateTime beginning = clock.startOfToday().minusDays(DAYS_TO_QUERY);
    final OffsetDateTime ending = clock.endOfToday();

    givenSubscriptionWithCoresCapacityAndDates(testSku, coresCapacity, beginning, ending);

    // When: Get the last page of results (offset=90, limit=10 for 100 total items)
    final int lastPageOffset = 90;
    CapacityReportByMetricId lastPage =
        service.getCapacityReportByMetricId(
            Product.OPENSHIFT,
            orgId,
            CORES.toString(),
            beginning,
            ending,
            GranularityType.DAILY,
            lastPageOffset,
            PAGE_SIZE);

    // Then: Verify last page structure
    assertThat("Last page should not be null", lastPage, notNullValue());
    assertThat("Last page should have 10 data points", lastPage.getData(), hasSize(PAGE_SIZE));

    // Then: Next link is null
    PageLinks lastPageLinks = lastPage.getLinks();
    assertThat("Last page links should not be null", lastPageLinks, notNullValue());
    assertThat("Next link should be null on last page", lastPageLinks.getNext(), nullValue());

    // Then: Previous link populated
    assertThat(
        "Previous link should be present on last page",
        lastPageLinks.getPrevious(),
        notNullValue());
    assertTrue(
        lastPageLinks.getPrevious().contains("offset=80"),
        "Previous link should point to previous page with offset=80");
  }

  // Helper method to create subscription with custom dates spanning the query period
  private void givenSubscriptionWithCoresCapacityAndDates(
      String sku, double coresCapacity, OffsetDateTime startDate, OffsetDateTime endDate) {
    // Create offering with cores capacity
    wiremock
        .forProductAPI()
        .stubOfferingData(domain.Offering.buildOpenShiftOffering(sku, coresCapacity, null));
    assertThat(
        "Sync offering should succeed",
        service.syncOffering(sku).statusCode(),
        equalTo(org.apache.http.HttpStatus.SC_OK));

    // Create subscription with cores capacity and custom dates
    domain.Subscription subscription =
        domain.Subscription.buildOpenShiftSubscriptionUsingSku(
                orgId, java.util.Map.of(CORES, coresCapacity), sku)
            .toBuilder()
            .startDate(startDate.minusDays(1)) // Start before the query range
            .endDate(endDate.plusDays(1)) // End after the query range
            .build();
    assertThat(
        "Creating subscription should succeed",
        service.saveSubscriptions(true, subscription).statusCode(),
        equalTo(org.apache.http.HttpStatus.SC_OK));
  }
}
