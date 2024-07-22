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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redhat.swatch.configuration.registry.ProductId;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.UriInfo;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.candlepin.subscriptions.db.HostRepository;
import org.candlepin.subscriptions.db.OrgConfigRepository;
import org.candlepin.subscriptions.db.TallyInstanceViewRepository;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.Host;
import org.candlepin.subscriptions.db.model.HostHardwareType;
import org.candlepin.subscriptions.db.model.TallyInstanceNonPaygView;
import org.candlepin.subscriptions.db.model.TallyInstancePaygView;
import org.candlepin.subscriptions.resteasy.PageLinkCreator;
import org.candlepin.subscriptions.security.WithMockRedHatPrincipal;
import org.candlepin.subscriptions.utilization.api.v1.model.BillingProviderType;
import org.candlepin.subscriptions.utilization.api.v1.model.CloudProvider;
import org.candlepin.subscriptions.utilization.api.v1.model.InstanceData;
import org.candlepin.subscriptions.utilization.api.v1.model.InstanceMeta;
import org.candlepin.subscriptions.utilization.api.v1.model.InstanceResponse;
import org.candlepin.subscriptions.utilization.api.v1.model.ReportCategory;
import org.candlepin.subscriptions.utilization.api.v1.model.ServiceLevelType;
import org.candlepin.subscriptions.utilization.api.v1.model.UsageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"api", "test"})
class InstancesResourceTest {

  private static final ProductId ROSA = ProductId.fromString("rosa");
  private static final ProductId RHEL_FOR_X86 = ProductId.fromString("RHEL for x86");
  private static final String SORT_BY_DISPLAY_NAME = "display_name";

  @MockBean TallyInstanceViewRepository repository;
  @MockBean HostRepository hostRepository;
  @MockBean PageLinkCreator pageLinkCreator;
  @MockBean OrgConfigRepository orgConfigRepository;
  @MockBean UriInfo uriInfo;
  @Autowired InstancesResource resource;

  @BeforeEach
  public void setup() {
    when(orgConfigRepository.existsByOrgId("owner123456")).thenReturn(true);
  }

  @WithMockRedHatPrincipal("123456")
  @Test
  void testShouldPopulateInstanceResponse() {
    BillingProvider expectedBillingProvider = BillingProvider.AWS;
    double expectedCoresValue = 45.0;
    double expectedInstanceHoursValue = 0.0;

    var tallyInstanceView = new TallyInstanceNonPaygView();
    tallyInstanceView.setId("testHostId");
    tallyInstanceView.setDisplayName("rhv.example.com");
    tallyInstanceView.setNumOfGuests(3);
    tallyInstanceView.setLastSeen(OffsetDateTime.now());
    tallyInstanceView.setLastAppliedEventRecordDate(OffsetDateTime.now());
    tallyInstanceView.setInventoryId(UUID.randomUUID().toString());
    tallyInstanceView.getKey().setInstanceId("d6214a0b-b344-4778-831c-d53dcacb2da3");
    tallyInstanceView.setHostBillingProvider(expectedBillingProvider);
    tallyInstanceView.getKey().setMeasurementType(HardwareMeasurementType.VIRTUAL);
    tallyInstanceView.setMetrics(Map.of(MetricIdUtils.getSockets(), 10.0));
    tallyInstanceView.setCores((int) expectedCoresValue);

    Mockito.when(
            repository.findAllBy(
                eq(true),
                eq("owner123456"),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()))
        .thenReturn(new PageImpl<>(List.of(tallyInstanceView)));

    var expectedMetrics = List.of("Cores", "Instance-hours");
    List<Double> expectedMeasurement = List.of(expectedCoresValue, expectedInstanceHoursValue);
    var data = new InstanceData();
    data.setId("testHostId");
    data.setInstanceId(tallyInstanceView.getKey().getInstanceId());
    data.setInventoryId(tallyInstanceView.getInventoryId());
    data.setDisplayName(tallyInstanceView.getDisplayName());
    data.setBillingProvider(BillingProviderType.AWS);
    data.setLastSeen(tallyInstanceView.getLastSeen());
    data.setLastAppliedEventRecordDate(tallyInstanceView.getLastAppliedEventRecordDate());
    data.setMeasurements(expectedMeasurement);
    data.setNumberOfGuests(tallyInstanceView.getNumOfGuests());
    data.setCategory(ReportCategory.VIRTUAL);

    var meta = new InstanceMeta();
    meta.setCount(1);
    meta.setProduct(ROSA.toString());
    meta.setServiceLevel(ServiceLevelType.PREMIUM);
    meta.setUsage(UsageType.PRODUCTION);
    meta.setMeasurements(expectedMetrics);
    meta.setBillingProvider(BillingProviderType.AWS);

    var expected = new InstanceResponse();
    expected.setData(List.of(data));
    expected.setMeta(meta);

    InstanceResponse report =
        resource.getInstancesByProduct(
            ROSA,
            null,
            null,
            ServiceLevelType.PREMIUM,
            UsageType.PRODUCTION,
            null,
            null,
            BillingProviderType.AWS,
            null,
            null,
            null,
            null,
            null,
            SORT_BY_DISPLAY_NAME,
            null);

    assertEquals(expected, report);
  }

  @WithMockRedHatPrincipal("123456")
  @Test
  void testShouldPopulateInstanceResponseWithHypervisorAndPhysical() {
    BillingProvider expectedBillingProvider = BillingProvider.RED_HAT;

    var tallyInstanceViewPhysical = new TallyInstanceNonPaygView();
    tallyInstanceViewPhysical.setDisplayName("rhv.example.com");
    tallyInstanceViewPhysical.setNumOfGuests(3);
    tallyInstanceViewPhysical.setLastSeen(OffsetDateTime.now());
    tallyInstanceViewPhysical.getKey().setInstanceId("d6214a0b-b344-4778-831c-d53dcacb2da3");
    tallyInstanceViewPhysical.setHostBillingProvider(expectedBillingProvider);
    tallyInstanceViewPhysical.getKey().setMeasurementType(HardwareMeasurementType.PHYSICAL);
    tallyInstanceViewPhysical.setMetrics(Map.of(MetricIdUtils.getSockets(), 4.0));
    // Measurement should come from sockets value
    tallyInstanceViewPhysical.setSockets(2);

    var tallyInstanceViewHypervisor = new TallyInstanceNonPaygView();
    tallyInstanceViewHypervisor.setDisplayName("rhv.example.com");
    tallyInstanceViewHypervisor.setNumOfGuests(3);
    tallyInstanceViewHypervisor.setLastSeen(OffsetDateTime.now());
    tallyInstanceViewHypervisor.getKey().setInstanceId("d6214a0bb3444778831cd53dcacb2da3");
    tallyInstanceViewHypervisor.setHostBillingProvider(expectedBillingProvider);
    tallyInstanceViewHypervisor.getKey().setMeasurementType(HardwareMeasurementType.HYPERVISOR);
    tallyInstanceViewHypervisor.setMetrics(Map.of(MetricIdUtils.getSockets(), 8.0));
    // Measurement should come from sockets value
    tallyInstanceViewHypervisor.setSockets(4);

    Mockito.when(
            repository.findAllBy(
                eq(false),
                eq("owner123456"),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()))
        .thenReturn(
            new PageImpl<>(List.of(tallyInstanceViewPhysical, tallyInstanceViewHypervisor)));

    var dataPhysical = new InstanceData();
    dataPhysical.setInstanceId(tallyInstanceViewPhysical.getKey().getInstanceId());
    dataPhysical.setDisplayName(tallyInstanceViewPhysical.getDisplayName());
    dataPhysical.setBillingProvider(BillingProviderType.RED_HAT);
    dataPhysical.setLastSeen(tallyInstanceViewPhysical.getLastSeen());
    dataPhysical.setMeasurements(List.of(2.0));
    dataPhysical.setNumberOfGuests(tallyInstanceViewPhysical.getNumOfGuests());
    dataPhysical.setCategory(ReportCategory.PHYSICAL);

    var dataHypervisor = new InstanceData();
    dataHypervisor.setInstanceId(tallyInstanceViewHypervisor.getKey().getInstanceId());
    dataHypervisor.setDisplayName(tallyInstanceViewHypervisor.getDisplayName());
    dataHypervisor.setBillingProvider(BillingProviderType.RED_HAT);
    dataHypervisor.setLastSeen(tallyInstanceViewHypervisor.getLastSeen());
    dataHypervisor.setMeasurements(List.of(4.0));
    dataHypervisor.setNumberOfGuests(tallyInstanceViewHypervisor.getNumOfGuests());
    dataHypervisor.setCategory(ReportCategory.HYPERVISOR);

    var meta = new InstanceMeta();
    meta.setCount(2);
    meta.setProduct(RHEL_FOR_X86.toString());
    meta.setServiceLevel(ServiceLevelType.PREMIUM);
    meta.setUsage(UsageType.PRODUCTION);
    meta.setBillingProvider(BillingProviderType.RED_HAT);
    meta.setMeasurements(List.of("Sockets"));

    var expected = new InstanceResponse();
    expected.setData(List.of(dataPhysical, dataHypervisor));
    expected.setMeta(meta);

    InstanceResponse report =
        resource.getInstancesByProduct(
            RHEL_FOR_X86,
            null,
            null,
            ServiceLevelType.PREMIUM,
            UsageType.PRODUCTION,
            null,
            null,
            BillingProviderType.RED_HAT,
            null,
            null,
            null,
            null,
            null,
            SORT_BY_DISPLAY_NAME,
            null);

    assertEquals(expected, report);
  }

  @WithMockRedHatPrincipal("123456")
  @Test
  void testShouldPopulateCategoryWithCloud() {
    BillingProvider expectedBillingProvider = BillingProvider.AWS;

    var tallyInstanceView = new TallyInstancePaygView();
    tallyInstanceView.setDisplayName("rhv.example.com");
    tallyInstanceView.setNumOfGuests(3);
    tallyInstanceView.setLastSeen(OffsetDateTime.now());
    tallyInstanceView.getKey().setInstanceId("d6214a0b-b344-4778-831c-d53dcacb2da3");
    tallyInstanceView.setHostBillingProvider(expectedBillingProvider);
    tallyInstanceView.getKey().setMeasurementType(HardwareMeasurementType.AWS);
    tallyInstanceView.setMetrics(
        Map.of(MetricIdUtils.getCores(), 8.0, MetricIdUtils.getInstanceHours(), 5.0));

    Mockito.when(
            repository.findAllBy(
                eq(true),
                eq("owner123456"),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()))
        .thenReturn(new PageImpl<>(List.of(tallyInstanceView)));

    var data = new InstanceData();
    data.setId(tallyInstanceView.getId());
    data.setInstanceId(tallyInstanceView.getKey().getInstanceId());
    data.setDisplayName(tallyInstanceView.getDisplayName());
    data.setBillingProvider(BillingProviderType.AWS);
    data.setLastSeen(tallyInstanceView.getLastSeen());
    data.setMeasurements(List.of(8.0, 5.0));
    data.setNumberOfGuests(tallyInstanceView.getNumOfGuests());
    data.setCloudProvider(CloudProvider.AWS);
    data.setCategory(ReportCategory.CLOUD);

    var meta = new InstanceMeta();
    meta.setCount(1);
    meta.setProduct(ROSA.toString());
    meta.setServiceLevel(ServiceLevelType.PREMIUM);
    meta.setUsage(UsageType.PRODUCTION);
    meta.setMeasurements(List.of("Cores", "Instance-hours"));
    meta.setBillingProvider(BillingProviderType.AWS);

    var expected = new InstanceResponse();
    expected.setData(List.of(data));
    expected.setMeta(meta);

    InstanceResponse report =
        resource.getInstancesByProduct(
            ROSA,
            null,
            null,
            ServiceLevelType.PREMIUM,
            UsageType.PRODUCTION,
            null,
            null,
            BillingProviderType.AWS,
            null,
            null,
            null,
            null,
            null,
            SORT_BY_DISPLAY_NAME,
            null);

    assertEquals(expected, report);
  }

  @Test
  void testShouldRequirePAYGProductsHaveDateRangeWithinOneMonth() {
    var dayInJanuary = OffsetDateTime.of(2023, 1, 23, 10, 0, 0, 0, ZoneOffset.UTC);
    var laterDayInJanuary = OffsetDateTime.of(2023, 1, 29, 10, 0, 0, 0, ZoneOffset.UTC);
    var dayInFebruary = OffsetDateTime.of(2023, 2, 23, 10, 0, 0, 0, ZoneOffset.UTC);

    // ROSA is a PAYG product
    resource.validateBeginningAndEndingDates(ROSA, dayInJanuary, laterDayInJanuary);
    assertThrows(
        BadRequestException.class,
        () -> resource.validateBeginningAndEndingDates(ROSA, dayInJanuary, dayInFebruary));
  }

  @WithMockRedHatPrincipal("123456")
  @Test
  void testCallRepoWithNullMonthForNonPAYGProduct() {
    BillingProvider expectedBillingProvider = BillingProvider.RED_HAT;

    var tallyInstanceView = new TallyInstanceNonPaygView();
    tallyInstanceView.setDisplayName("rhv.example.com");
    tallyInstanceView.setNumOfGuests(3);
    tallyInstanceView.setLastSeen(OffsetDateTime.now());
    tallyInstanceView.getKey().setInstanceId("d6214a0b-b344-4778-831c-d53dcacb2da3");
    tallyInstanceView.setHostBillingProvider(expectedBillingProvider);
    tallyInstanceView.getKey().setMeasurementType(HardwareMeasurementType.VIRTUAL);
    tallyInstanceView.getKey().setProductId("RHEL");
    tallyInstanceView.setMetrics(Map.of(MetricIdUtils.getSockets(), 8.0));

    Mockito.when(
            repository.findAllBy(
                eq(false),
                eq("owner123456"),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()))
        .thenReturn(new PageImpl<>(List.of(tallyInstanceView)));

    resource.getInstancesByProduct(
        RHEL_FOR_X86,
        null,
        null,
        ServiceLevelType.PREMIUM,
        UsageType.PRODUCTION,
        null,
        null,
        BillingProviderType.RED_HAT,
        null,
        null,
        null,
        OffsetDateTime.now(),
        OffsetDateTime.now(),
        SORT_BY_DISPLAY_NAME,
        null);

    Mockito.when(
            repository.findAllBy(
                eq(false),
                eq("owner123456"),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()))
        .thenReturn(new PageImpl<>(new ArrayList<>()));

    verify(repository)
        .findAllBy(
            eq(false),
            eq("owner123456"),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            eq(null),
            any(),
            any(),
            any(),
            any(),
            any());
  }

  @WithMockRedHatPrincipal("123456")
  @Test
  void testGetInstanceGuestsReturnInstanceData() {
    var host = new Host();
    host.setOrgId("owner123456");
    host.setInstanceId("instance123");
    host.setHardwareType(HostHardwareType.PHYSICAL);

    Mockito.when(
            hostRepository.getGuestHostsByHypervisorInstanceId(
                eq(host.getOrgId()), eq(host.getInstanceId()), any()))
        .thenReturn(new PageImpl<>(List.of(host)));

    var response = resource.getInstanceGuests(host.getInstanceId(), null, null);

    assertEquals(1, response.getData().size());
  }

  @WithMockRedHatPrincipal("123456")
  @Test
  void testMinCoresZeroWhenUomIsCores() {
    BillingProvider expectedBillingProvider = BillingProvider.RED_HAT;

    var tallyInstanceView = new TallyInstanceNonPaygView();
    tallyInstanceView.setDisplayName("rhv.example.com");
    tallyInstanceView.setNumOfGuests(3);
    tallyInstanceView.setLastSeen(OffsetDateTime.now());
    tallyInstanceView.getKey().setInstanceId("d6214a0b-b344-4778-831c-d53dcacb2da3");
    tallyInstanceView.setHostBillingProvider(expectedBillingProvider);
    tallyInstanceView.getKey().setMeasurementType(HardwareMeasurementType.VIRTUAL);
    tallyInstanceView.getKey().setProductId("RHEL");
    tallyInstanceView.setMetrics(Map.of(MetricIdUtils.getCores(), 8.0));

    Mockito.when(
            repository.findAllBy(
                eq(false),
                eq("owner123456"),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()))
        .thenReturn(new PageImpl<>(List.of(tallyInstanceView)));

    resource.getInstancesByProduct(
        RHEL_FOR_X86,
        null,
        null,
        ServiceLevelType.PREMIUM,
        UsageType.PRODUCTION,
        "Cores",
        null,
        BillingProviderType.RED_HAT,
        null,
        null,
        null,
        OffsetDateTime.now(),
        OffsetDateTime.now(),
        SORT_BY_DISPLAY_NAME,
        null);

    verify(repository)
        .findAllBy(
            eq(false),
            eq("owner123456"),
            any(),
            any(),
            any(),
            any(),
            eq(0),
            eq(null),
            eq(null),
            any(),
            any(),
            any(),
            any(),
            any());
  }

  @WithMockRedHatPrincipal("123456")
  @Test
  void testMinCoresZeroWhenMetricIdIsCores() {
    BillingProvider expectedBillingProvider = BillingProvider.RED_HAT;

    var tallyInstanceView = new TallyInstanceNonPaygView();
    tallyInstanceView.setDisplayName("rhv.example.com");
    tallyInstanceView.setNumOfGuests(3);
    tallyInstanceView.setLastSeen(OffsetDateTime.now());
    tallyInstanceView.getKey().setInstanceId("d6214a0b-b344-4778-831c-d53dcacb2da3");
    tallyInstanceView.setHostBillingProvider(expectedBillingProvider);
    tallyInstanceView.getKey().setMeasurementType(HardwareMeasurementType.VIRTUAL);
    tallyInstanceView.getKey().setProductId("RHEL");
    tallyInstanceView.setMetrics(Map.of(MetricIdUtils.getCores(), 8.0));

    Mockito.when(
            repository.findAllBy(
                eq(false),
                eq("owner123456"),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()))
        .thenReturn(new PageImpl<>(List.of(tallyInstanceView)));

    resource.getInstancesByProduct(
        RHEL_FOR_X86,
        null,
        null,
        ServiceLevelType.PREMIUM,
        UsageType.PRODUCTION,
        null,
        "Cores",
        BillingProviderType.RED_HAT,
        null,
        null,
        null,
        OffsetDateTime.now(),
        OffsetDateTime.now(),
        SORT_BY_DISPLAY_NAME,
        null);

    verify(repository)
        .findAllBy(
            eq(false),
            eq("owner123456"),
            any(),
            any(),
            any(),
            any(),
            eq(0),
            eq(null),
            eq(null),
            any(),
            any(),
            any(),
            any(),
            any());
  }

  @WithMockRedHatPrincipal("123456")
  @Test
  void testMinSocketsZeroWhenUomIsSockets() {
    BillingProvider expectedBillingProvider = BillingProvider.RED_HAT;

    var tallyInstanceView = new TallyInstanceNonPaygView();
    tallyInstanceView.setDisplayName("rhv.example.com");
    tallyInstanceView.setNumOfGuests(3);
    tallyInstanceView.setLastSeen(OffsetDateTime.now());
    tallyInstanceView.getKey().setInstanceId("d6214a0b-b344-4778-831c-d53dcacb2da3");
    tallyInstanceView.setHostBillingProvider(expectedBillingProvider);
    tallyInstanceView.getKey().setMeasurementType(HardwareMeasurementType.VIRTUAL);
    tallyInstanceView.getKey().setProductId("RHEL");
    tallyInstanceView.setMetrics(Map.of(MetricIdUtils.getSockets(), 8.0));

    Mockito.when(
            repository.findAllBy(
                eq(false),
                eq("owner123456"),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()))
        .thenReturn(new PageImpl<>(List.of(tallyInstanceView)));

    resource.getInstancesByProduct(
        RHEL_FOR_X86,
        null,
        null,
        ServiceLevelType.PREMIUM,
        UsageType.PRODUCTION,
        "Sockets",
        null,
        BillingProviderType.RED_HAT,
        null,
        null,
        null,
        OffsetDateTime.now(),
        OffsetDateTime.now(),
        SORT_BY_DISPLAY_NAME,
        null);

    verify(repository)
        .findAllBy(
            eq(false),
            eq("owner123456"),
            any(),
            any(),
            any(),
            any(),
            eq(null),
            eq(0),
            eq(null),
            any(),
            any(),
            any(),
            any(),
            any());
  }

  @WithMockRedHatPrincipal("123456")
  @Test
  void testMinSocketsZeroWhenMetricIdIsSockets() {
    BillingProvider expectedBillingProvider = BillingProvider.RED_HAT;

    var tallyInstanceView = new TallyInstanceNonPaygView();
    tallyInstanceView.setDisplayName("rhv.example.com");
    tallyInstanceView.setNumOfGuests(3);
    tallyInstanceView.setLastSeen(OffsetDateTime.now());
    tallyInstanceView.getKey().setInstanceId("d6214a0b-b344-4778-831c-d53dcacb2da3");
    tallyInstanceView.setHostBillingProvider(expectedBillingProvider);
    tallyInstanceView.getKey().setMeasurementType(HardwareMeasurementType.VIRTUAL);
    tallyInstanceView.getKey().setProductId("RHEL");
    tallyInstanceView.setMetrics(Map.of(MetricIdUtils.getSockets(), 8.0));

    Mockito.when(
            repository.findAllBy(
                eq(false),
                eq("owner123456"),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()))
        .thenReturn(new PageImpl<>(List.of(tallyInstanceView)));

    resource.getInstancesByProduct(
        RHEL_FOR_X86,
        null,
        null,
        ServiceLevelType.PREMIUM,
        UsageType.PRODUCTION,
        null,
        "Sockets",
        BillingProviderType.RED_HAT,
        null,
        null,
        null,
        OffsetDateTime.now(),
        OffsetDateTime.now(),
        SORT_BY_DISPLAY_NAME,
        null);

    verify(repository)
        .findAllBy(
            eq(false),
            eq("owner123456"),
            any(),
            any(),
            any(),
            any(),
            eq(null),
            eq(0),
            eq(null),
            any(),
            any(),
            any(),
            any(),
            any());
  }

  @WithMockRedHatPrincipal("123456")
  @Test
  void testGetInstancesByProductThrowsExceptionForUnknownUom() {
    assertThrows(
        BadRequestException.class,
        () ->
            resource.getInstancesByProduct(
                ROSA,
                null,
                null,
                ServiceLevelType.PREMIUM,
                UsageType.PRODUCTION,
                "NotAMetricId",
                null,
                BillingProviderType.RED_HAT,
                null,
                null,
                null,
                null,
                null,
                SORT_BY_DISPLAY_NAME,
                null));
  }

  @WithMockRedHatPrincipal("123456")
  @Test
  void testGetInstancesByProductThrowsExceptionForUnknownMetricId() {
    assertThrows(
        BadRequestException.class,
        () ->
            resource.getInstancesByProduct(
                ROSA,
                null,
                null,
                ServiceLevelType.PREMIUM,
                UsageType.PRODUCTION,
                null,
                "NotAMetricId",
                BillingProviderType.RED_HAT,
                null,
                null,
                null,
                null,
                null,
                SORT_BY_DISPLAY_NAME,
                null));
  }

  @Test
  void testGetInstancesByProductThrowsAuthenticationCredentialsNotFoundExceptionWhenNoSecurity() {
    assertThrows(
        AuthenticationCredentialsNotFoundException.class,
        () ->
            resource.getInstancesByProduct(
                ROSA,
                null,
                null,
                ServiceLevelType.PREMIUM,
                UsageType.PRODUCTION,
                null,
                null,
                BillingProviderType.RED_HAT,
                null,
                null,
                null,
                null,
                null,
                SORT_BY_DISPLAY_NAME,
                null));
  }

  @Test
  void testGetInstanceGuestsThrowsAuthenticationCredentialsNotFoundExceptionWhenNoSecurity() {
    assertThrows(
        AuthenticationCredentialsNotFoundException.class,
        () -> resource.getInstanceGuests("instance123", null, null));
  }
}
