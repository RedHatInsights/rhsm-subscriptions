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
package org.candlepin.subscriptions.resource;

import static org.mockito.Mockito.*;

import com.redhat.swatch.configuration.registry.ProductId;
import java.time.OffsetDateTime;
import java.util.Collections;
import org.candlepin.subscriptions.db.HostRepository;
import org.candlepin.subscriptions.db.OrgConfigRepository;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.TallyHostView;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.resteasy.PageLinkCreator;
import org.candlepin.subscriptions.security.WithMockRedHatPrincipal;
import org.candlepin.subscriptions.utilization.api.model.HostReportSort;
import org.candlepin.subscriptions.utilization.api.model.SortDirection;
import org.candlepin.subscriptions.utilization.api.model.Uom;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"api", "test"})
@WithMockRedHatPrincipal("123456")
class HostsResourceTest {
  static final Sort.Order IMPLICIT_ORDER = new Sort.Order(Sort.Direction.ASC, "id");
  private static final String SANITIZED_MISSING_DISPLAY_NAME = "";
  private static final OffsetDateTime NULL_BEGINNING_ENDING_PARAM = null;
  private static final ProductId RHEL_FOR_X86 = ProductId.fromString("RHEL for x86");

  @MockBean HostRepository repository;
  @MockBean PageLinkCreator pageLinkCreator;
  @MockBean OrgConfigRepository orgConfigRepo;
  @Autowired HostsResource resource;

  @BeforeEach
  public void setup() {
    PageImpl<TallyHostView> mockPage = new PageImpl<>(Collections.emptyList());
    when(repository.getTallyHostViews(
            any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt(), any()))
        .thenReturn(mockPage);
    when(orgConfigRepo.existsByOrgId("owner123456")).thenReturn(true);
  }

  @Test
  @SuppressWarnings("indentation")
  void testShouldMapDisplayNameAppropriately() {
    resource.getHosts(
        RHEL_FOR_X86,
        0,
        1,
        null,
        null,
        null,
        null,
        NULL_BEGINNING_ENDING_PARAM,
        NULL_BEGINNING_ENDING_PARAM,
        HostReportSort.DISPLAY_NAME,
        SortDirection.ASC);

    verify(repository, only())
        .getTallyHostViews(
            "owner123456",
            RHEL_FOR_X86.toString(),
            ServiceLevel._ANY,
            Usage._ANY,
            BillingProvider._ANY,
            "_ANY",
            SANITIZED_MISSING_DISPLAY_NAME,
            0,
            0,
            PageRequest.of(
                0,
                1,
                Sort.by(
                    Sort.Order.asc(
                        HostsResource.HOST_SORT_PARAM_MAPPING.get(HostReportSort.DISPLAY_NAME)),
                    IMPLICIT_ORDER)));
  }

  @Test
  void testShouldMapCoresAppropriately() {
    resource.getHosts(
        RHEL_FOR_X86,
        0,
        1,
        null,
        null,
        null,
        null,
        NULL_BEGINNING_ENDING_PARAM,
        NULL_BEGINNING_ENDING_PARAM,
        HostReportSort.CORES,
        SortDirection.ASC);

    verify(repository, only())
        .getTallyHostViews(
            "owner123456",
            RHEL_FOR_X86.toString(),
            ServiceLevel._ANY,
            Usage._ANY,
            BillingProvider._ANY,
            "_ANY",
            SANITIZED_MISSING_DISPLAY_NAME,
            0,
            0,
            PageRequest.of(
                0,
                1,
                Sort.by(
                    Sort.Order.asc(HostsResource.HOST_SORT_PARAM_MAPPING.get(HostReportSort.CORES)),
                    IMPLICIT_ORDER)));
  }

  @Test
  void testShouldMapSocketsAppropriately() {
    resource.getHosts(
        RHEL_FOR_X86,
        0,
        1,
        null,
        null,
        null,
        null,
        NULL_BEGINNING_ENDING_PARAM,
        NULL_BEGINNING_ENDING_PARAM,
        HostReportSort.SOCKETS,
        SortDirection.ASC);

    verify(repository, only())
        .getTallyHostViews(
            "owner123456",
            RHEL_FOR_X86.toString(),
            ServiceLevel._ANY,
            Usage._ANY,
            BillingProvider._ANY,
            "_ANY",
            SANITIZED_MISSING_DISPLAY_NAME,
            0,
            0,
            PageRequest.of(
                0,
                1,
                Sort.by(
                    Sort.Order.asc(
                        HostsResource.HOST_SORT_PARAM_MAPPING.get(HostReportSort.SOCKETS)),
                    IMPLICIT_ORDER)));
  }

  @Test
  void testShouldMapLastSeenAppropriately() {
    resource.getHosts(
        RHEL_FOR_X86,
        0,
        1,
        null,
        null,
        null,
        null,
        NULL_BEGINNING_ENDING_PARAM,
        NULL_BEGINNING_ENDING_PARAM,
        HostReportSort.LAST_SEEN,
        SortDirection.ASC);

    verify(repository, only())
        .getTallyHostViews(
            "owner123456",
            RHEL_FOR_X86.toString(),
            ServiceLevel._ANY,
            Usage._ANY,
            BillingProvider._ANY,
            "_ANY",
            SANITIZED_MISSING_DISPLAY_NAME,
            0,
            0,
            PageRequest.of(
                0,
                1,
                Sort.by(
                    Sort.Order.asc(
                        HostsResource.HOST_SORT_PARAM_MAPPING.get(HostReportSort.LAST_SEEN)),
                    IMPLICIT_ORDER)));
  }

  @Test
  void testShouldMapHardwareTypeAppropriately() {
    resource.getHosts(
        RHEL_FOR_X86,
        0,
        1,
        null,
        null,
        null,
        null,
        NULL_BEGINNING_ENDING_PARAM,
        NULL_BEGINNING_ENDING_PARAM,
        HostReportSort.HARDWARE_TYPE,
        SortDirection.ASC);

    verify(repository, only())
        .getTallyHostViews(
            "owner123456",
            RHEL_FOR_X86.toString(),
            ServiceLevel._ANY,
            Usage._ANY,
            BillingProvider._ANY,
            "_ANY",
            SANITIZED_MISSING_DISPLAY_NAME,
            0,
            0,
            PageRequest.of(
                0,
                1,
                Sort.by(
                    Sort.Order.asc(
                        HostsResource.HOST_SORT_PARAM_MAPPING.get(HostReportSort.HARDWARE_TYPE)),
                    IMPLICIT_ORDER)));
  }

  @Test
  void testShouldDefaultToImplicitOrder() {
    resource.getHosts(
        RHEL_FOR_X86,
        0,
        1,
        null,
        null,
        null,
        null,
        NULL_BEGINNING_ENDING_PARAM,
        NULL_BEGINNING_ENDING_PARAM,
        null,
        SortDirection.ASC);

    verify(repository, only())
        .getTallyHostViews(
            "owner123456",
            RHEL_FOR_X86.toString(),
            ServiceLevel._ANY,
            Usage._ANY,
            BillingProvider._ANY,
            "_ANY",
            SANITIZED_MISSING_DISPLAY_NAME,
            0,
            0,
            PageRequest.of(0, 1, Sort.by(IMPLICIT_ORDER)));
  }

  @Test
  void testShouldDefaultToAscending() {
    resource.getHosts(
        RHEL_FOR_X86,
        0,
        1,
        null,
        null,
        null,
        null,
        NULL_BEGINNING_ENDING_PARAM,
        NULL_BEGINNING_ENDING_PARAM,
        HostReportSort.DISPLAY_NAME,
        null);

    verify(repository, only())
        .getTallyHostViews(
            "owner123456",
            RHEL_FOR_X86.toString(),
            ServiceLevel._ANY,
            Usage._ANY,
            BillingProvider._ANY,
            "_ANY",
            SANITIZED_MISSING_DISPLAY_NAME,
            0,
            0,
            PageRequest.of(
                0,
                1,
                Sort.by(
                    Sort.Order.asc(
                        HostsResource.HOST_SORT_PARAM_MAPPING.get(HostReportSort.DISPLAY_NAME)),
                    IMPLICIT_ORDER)));
  }

  @Test
  void testShouldUseMinCoresWhenUomIsCores() {
    resource.getHosts(
        RHEL_FOR_X86,
        0,
        1,
        null,
        null,
        Uom.CORES,
        null,
        NULL_BEGINNING_ENDING_PARAM,
        NULL_BEGINNING_ENDING_PARAM,
        null,
        null);
    verify(repository, only())
        .getTallyHostViews(
            "owner123456",
            RHEL_FOR_X86.toString(),
            ServiceLevel._ANY,
            Usage._ANY,
            BillingProvider._ANY,
            "_ANY",
            SANITIZED_MISSING_DISPLAY_NAME,
            1,
            0,
            PageRequest.of(0, 1, Sort.by(IMPLICIT_ORDER)));
  }

  @Test
  void testShouldUseMinSocketsWhenUomIsSockets() {
    resource.getHosts(
        RHEL_FOR_X86,
        0,
        1,
        null,
        null,
        Uom.SOCKETS,
        null,
        NULL_BEGINNING_ENDING_PARAM,
        NULL_BEGINNING_ENDING_PARAM,
        null,
        null);

    verify(repository, only())
        .getTallyHostViews(
            "owner123456",
            RHEL_FOR_X86.toString(),
            ServiceLevel._ANY,
            Usage._ANY,
            BillingProvider._ANY,
            "_ANY",
            SANITIZED_MISSING_DISPLAY_NAME,
            0,
            1,
            PageRequest.of(0, 1, Sort.by(IMPLICIT_ORDER)));
  }

  @ParameterizedTest(name = "testInvalidBeginningAndEndingDates[{index}] {arguments}")
  @CsvSource({
    "2000-01-01T00:00:00Z, 1999-01-01T00:00:00Z",
    "2021-02-01T00:00:00Z, 2021-03-01T00:00:00Z"
  })
  void testInvalidBeginningAndEndingDates(OffsetDateTime beginning, OffsetDateTime ending) {
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> resource.validateBeginningAndEndingDates(beginning, ending));
  }

  @ParameterizedTest(name = "testValidBeginningAndEndingDates[{index}] {arguments}")
  @CsvSource({
    "2000-01-01T00:00:00Z, 2000-01-01T00:00:00Z",
    "2000-01-01T00:00:00Z, 2000-01-31T00:00:00Z"
  })
  void testValidBeginningAndEndingDates(OffsetDateTime beginning, OffsetDateTime ending) {
    Assertions.assertDoesNotThrow(
        () -> resource.validateBeginningAndEndingDates(beginning, ending));
  }
}
