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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static utils.TallyTestProducts.RHEL_FOR_X86;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.api.hbi.HbiDbConnector;
import com.redhat.swatch.component.tests.api.hbi.HostStateManager;
import com.redhat.swatch.component.tests.api.hbi.HostTemplates;
import com.redhat.swatch.component.tests.api.hbi.RhsmFacts;
import com.redhat.swatch.tally.test.model.InstanceResponse;
import com.redhat.swatch.tally.test.model.SortDirection;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TallyInstancesReportPaginationAndSortNonPaygTest extends BaseTallyComponentTest {

  private static final String PRODUCT_TAG = RHEL_FOR_X86.productTag();
  private static final String PHYSICAL_CATEGORY = "physical";

  private HostStateManager hostManager;
  private OffsetDateTime start;
  private OffsetDateTime end;

  @BeforeEach
  void setupHostManager() {
    hostManager = new HostStateManager(new HbiDbConnector(hbiDatabase));
    start = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS);
    end = start.plusDays(1).minusNanos(1);
  }

  @AfterEach
  void cleanupHosts() {
    if (hostManager != null) {
      hostManager.cleanupAll();
    }
  }

  @Test
  @TestPlanName("tally-instances-sorting-TC007")
  public void shouldPaginateWithLimitAndOffset() {
    givenPhysicalHosts(2, 4, 6, 12, 4, 8);

    InstanceResponse unpaged = service.getInstancesByProduct(orgId, PRODUCT_TAG, start, end);
    assertNotNull(unpaged.getData());
    assertEquals(3, unpaged.getData().size());

    Map<String, Object> firstPageParams = paginationParams(1, 0);
    firstPageParams.put("sort", "display_name");
    InstanceResponse firstPage =
        service.getInstancesByProduct(orgId, PRODUCT_TAG, start, end, firstPageParams);

    Map<String, Object> secondPageParams = paginationParams(1, 1);
    secondPageParams.put("sort", "display_name");
    InstanceResponse secondPage =
        service.getInstancesByProduct(orgId, PRODUCT_TAG, start, end, secondPageParams);

    assertNotNull(firstPage.getData());
    assertNotNull(secondPage.getData());
    assertTrue(firstPage.getData().size() <= 1);
    assertTrue(secondPage.getData().size() <= 1);
    assertEquals(3, firstPage.getMeta().getCount());
    assertEquals(3, secondPage.getMeta().getCount());
    assertEquals(
        "physical-sort-host-2-sockets-4-cores", firstPage.getData().getFirst().getDisplayName());
    assertEquals(
        "physical-sort-host-4-sockets-8-cores", secondPage.getData().getFirst().getDisplayName());
  }

  @Test
  @TestPlanName("tally-instances-sorting-TC008")
  public void shouldIncludePaginationLinksInResponse() {
    givenPhysicalHosts(2, 4, 6, 12, 4, 8);

    Map<String, Object> params = paginationParams(2, 0);
    InstanceResponse paged = service.getInstancesByProduct(orgId, PRODUCT_TAG, start, end, params);

    assertEquals(2, paged.getData().size());
    assertNotNull(paged.getLinks());

    // On the first page, first, last, and next links should be available, with no previous link.
    var links = paged.getLinks();
    assertNotNull(links.getFirst());
    assertNotNull(links.getLast());
    assertNotNull(links.getNext());
    assertNull(links.getPrevious());

    // The next link should target this report and preserve its pagination and report parameters.
    String expectedPath = "/api/rhsm-subscriptions/v1/instances/products/" + PRODUCT_TAG;
    URI nextLink = URI.create(links.getNext());
    assertEquals(expectedPath, nextLink.getPath());

    Map<String, String> nextLinkParams = queryParams(nextLink);
    assertEquals("2", nextLinkParams.get("offset"));
    assertEquals("2", nextLinkParams.get("limit"));
    assertEquals(PHYSICAL_CATEGORY, nextLinkParams.get("category"));
    assertEquals(start.toString(), nextLinkParams.get("beginning"));
    assertEquals(end.toString(), nextLinkParams.get("ending"));
  }

  @Test
  @TestPlanName("tally-instances-sorting-TC009")
  public void shouldSortNonPaygInstancesBySockets() {
    givenPhysicalHosts(4, 24, 6, 16, 2, 32);

    InstanceResponse ascending = querySorted("sockets", SortDirection.ASC);
    InstanceResponse descending = querySorted("sockets", SortDirection.DESC);

    List<Double> ascendingSockets = measurementValues(ascending, "Sockets");
    List<Double> descendingSockets = measurementValues(descending, "Sockets");
    assertEquals(List.of(2.0, 4.0, 6.0), ascendingSockets);
    assertEquals(List.of(6.0, 4.0, 2.0), descendingSockets);
  }

  @Test
  @TestPlanName("tally-instances-sorting-TC010")
  public void shouldSortNonPaygInstancesByCores() {
    givenPhysicalHosts(4, 24, 6, 16, 2, 32);

    InstanceResponse ascending = querySorted("cores", SortDirection.ASC);
    InstanceResponse descending = querySorted("cores", SortDirection.DESC);

    assertNotNull(ascending.getData());
    assertNotNull(descending.getData());
    List<String> ascendingHostNames =
        ascending.getData().stream().map(instance -> instance.getDisplayName()).toList();
    List<String> descendingHostNames =
        descending.getData().stream().map(instance -> instance.getDisplayName()).toList();
    assertEquals(
        List.of(
            "physical-sort-host-6-sockets-16-cores",
            "physical-sort-host-4-sockets-24-cores",
            "physical-sort-host-2-sockets-32-cores"),
        ascendingHostNames);
    assertEquals(
        List.of(
            "physical-sort-host-2-sockets-32-cores",
            "physical-sort-host-4-sockets-24-cores",
            "physical-sort-host-6-sockets-16-cores"),
        descendingHostNames);
  }

  private void givenPhysicalHosts(int... socketCorePairs) {
    if (socketCorePairs.length % 2 != 0) {
      throw new IllegalArgumentException("Socket and core values must be provided in pairs");
    }

    service.createOptInConfig(orgId);

    // Name hosts based on their sockets and cores values
    for (int i = 0; i < socketCorePairs.length; i += 2) {
      int sockets = socketCorePairs[i];
      int cores = socketCorePairs[i + 1];
      String displayName = "physical-sort-host-%d-sockets-%d-cores".formatted(sockets, cores);
      hostManager
          .createHost(orgId)
          .displayName(displayName)
          .apply(HostTemplates.conduitReportedPhysicalRhel(sockets, cores))
          .rhsmFacts(
              RhsmFacts.builder()
                  .products(List.of("69"))
                  .isVirtual(false)
                  .sla("Premium")
                  .usage("Production")
                  .build())
          .insert();
    }
    service.tallyOrg(orgId);
  }

  private Map<String, Object> paginationParams(int limit, int offset) {
    Map<String, Object> params = new HashMap<>();
    params.put("category", PHYSICAL_CATEGORY);
    params.put("limit", limit);
    params.put("offset", offset);
    return params;
  }

  private InstanceResponse querySorted(String sort, SortDirection direction) {
    return service.getInstancesByProduct(
        orgId,
        PRODUCT_TAG,
        start,
        end,
        Map.of("category", PHYSICAL_CATEGORY, "sort", sort, "dir", direction));
  }

  private List<Double> measurementValues(InstanceResponse response, String metricId) {
    assertNotNull(response.getData());
    int metricIndex = response.getMeta().getMeasurements().indexOf(metricId);
    assertTrue(metricIndex >= 0, "Response should include the " + metricId + " measurement");
    return response.getData().stream()
        .map(instance -> instance.getMeasurements().get(metricIndex))
        .toList();
  }

  private Map<String, String> queryParams(URI uri) {
    assertNotNull(uri.getRawQuery());
    return Arrays.stream(uri.getRawQuery().split("&"))
        .map(param -> param.split("=", 2))
        .collect(
            Collectors.toMap(
                param -> URLDecoder.decode(param[0], StandardCharsets.UTF_8),
                param ->
                    URLDecoder.decode(param.length > 1 ? param[1] : "", StandardCharsets.UTF_8)));
  }
}
