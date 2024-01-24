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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.ProductId;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import jakarta.ws.rs.BadRequestException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.candlepin.subscriptions.db.HostRepository;
import org.candlepin.subscriptions.db.OrgConfigRepository;
import org.candlepin.subscriptions.db.TallyInstanceViewRepository;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.Host;
import org.candlepin.subscriptions.db.model.HostHardwareType;
import org.candlepin.subscriptions.db.model.InstanceMonthlyTotalKey;
import org.candlepin.subscriptions.db.model.TallyInstanceView;
import org.candlepin.subscriptions.db.model.TallyInstanceViewKey;
import org.candlepin.subscriptions.resteasy.PageLinkCreator;
import org.candlepin.subscriptions.security.WithMockRedHatPrincipal;
import org.candlepin.subscriptions.utilization.api.model.BillingProviderType;
import org.candlepin.subscriptions.utilization.api.model.CloudProvider;
import org.candlepin.subscriptions.utilization.api.model.InstanceData;
import org.candlepin.subscriptions.utilization.api.model.InstanceMeta;
import org.candlepin.subscriptions.utilization.api.model.InstanceResponse;
import org.candlepin.subscriptions.utilization.api.model.ReportCategory;
import org.candlepin.subscriptions.utilization.api.model.ServiceLevelType;
import org.candlepin.subscriptions.utilization.api.model.UsageType;
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
  @Autowired InstancesResource resource;

  @BeforeEach
  public void setup() {
    when(orgConfigRepository.existsByOrgId("owner123456")).thenReturn(true);
  }

  @WithMockRedHatPrincipal("123456")
  @Test
  void testShouldPopulateInstanceResponse() {
    BillingProvider expectedBillingProvider = BillingProvider.AWS;

    var tallyInstanceView = new TallyInstanceView();
    tallyInstanceView.setKey(new TallyInstanceViewKey());
    tallyInstanceView.setId("testHostId");
    tallyInstanceView.setDisplayName("rhv.example.com");
    tallyInstanceView.setNumOfGuests(3);
    tallyInstanceView.setLastSeen(OffsetDateTime.now());
    tallyInstanceView.getKey().setInstanceId("d6214a0b-b344-4778-831c-d53dcacb2da3");
    tallyInstanceView.setHostBillingProvider(expectedBillingProvider);
    tallyInstanceView.getKey().setMeasurementType(HardwareMeasurementType.VIRTUAL);
    tallyInstanceView.getKey().setMetricId(MetricIdUtils.getSockets().toString());

    Mockito.when(
            repository.findAllBy(
                eq("owner123456"),
                any(),
                any(),
                any(),
                any(),
                anyInt(),
                anyInt(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()))
        .thenReturn(new PageImpl<>(List.of(tallyInstanceView)));

    var expectUom = List.of("Cores", "Instance-hours");
    List<Double> expectedMeasurement = new ArrayList<>();
    String month = InstanceMonthlyTotalKey.formatMonthId(tallyInstanceView.getLastSeen());
    for (String uom : expectUom) {
      expectedMeasurement.add(
          Optional.ofNullable(tallyInstanceView.getMonthlyTotal(month, MetricId.fromString(uom)))
              .orElse(0.0));
    }
    var data = new InstanceData();
    data.setId("testHostId");
    data.setInstanceId(tallyInstanceView.getKey().getInstanceId());
    data.setDisplayName(tallyInstanceView.getDisplayName());
    data.setBillingProvider(expectedBillingProvider.asOpenApiEnum());
    data.setLastSeen(tallyInstanceView.getLastSeen());
    data.setMeasurements(expectedMeasurement);
    data.setNumberOfGuests(tallyInstanceView.getNumOfGuests());
    data.setCategory(ReportCategory.VIRTUAL);

    var meta = new InstanceMeta();
    meta.setCount(1);
    meta.setProduct(ROSA.toString());
    meta.setServiceLevel(ServiceLevelType.PREMIUM);
    meta.setUsage(UsageType.PRODUCTION);
    meta.setMeasurements(expectUom);
    meta.setBillingProvider(expectedBillingProvider.asOpenApiEnum());

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
            expectedBillingProvider.asOpenApiEnum(),
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

    var tallyInstanceViewPhysical = new TallyInstanceView();
    tallyInstanceViewPhysical.setKey(new TallyInstanceViewKey());
    tallyInstanceViewPhysical.setDisplayName("rhv.example.com");
    tallyInstanceViewPhysical.setNumOfGuests(3);
    tallyInstanceViewPhysical.setLastSeen(OffsetDateTime.now());
    tallyInstanceViewPhysical.getKey().setInstanceId("d6214a0b-b344-4778-831c-d53dcacb2da3");
    tallyInstanceViewPhysical.setHostBillingProvider(expectedBillingProvider);
    tallyInstanceViewPhysical.getKey().setMeasurementType(HardwareMeasurementType.PHYSICAL);
    tallyInstanceViewPhysical.getKey().setMetricId(MetricIdUtils.getSockets().toString());
    tallyInstanceViewPhysical.setValue(4.0);
    // Measurement should come from sockets value
    tallyInstanceViewPhysical.setSockets(2);

    var tallyInstanceViewHypervisor = new TallyInstanceView();
    tallyInstanceViewHypervisor.setKey(new TallyInstanceViewKey());
    tallyInstanceViewHypervisor.setDisplayName("rhv.example.com");
    tallyInstanceViewHypervisor.setNumOfGuests(3);
    tallyInstanceViewHypervisor.setLastSeen(OffsetDateTime.now());
    tallyInstanceViewHypervisor.getKey().setInstanceId("d6214a0bb3444778831cd53dcacb2da3");
    tallyInstanceViewHypervisor.setHostBillingProvider(expectedBillingProvider);
    tallyInstanceViewHypervisor.getKey().setMeasurementType(HardwareMeasurementType.HYPERVISOR);
    tallyInstanceViewHypervisor.getKey().setMetricId(MetricIdUtils.getSockets().toString());
    tallyInstanceViewHypervisor.setValue(8.0);
    // Measurement should come from sockets value
    tallyInstanceViewHypervisor.setSockets(4);

    Mockito.when(
            repository.findAllBy(
                eq("owner123456"),
                any(),
                any(),
                any(),
                any(),
                anyInt(),
                anyInt(),
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
    dataPhysical.setBillingProvider(expectedBillingProvider.asOpenApiEnum());
    dataPhysical.setLastSeen(tallyInstanceViewPhysical.getLastSeen());
    dataPhysical.setMeasurements(List.of(2.0));
    dataPhysical.setNumberOfGuests(tallyInstanceViewPhysical.getNumOfGuests());
    dataPhysical.setCategory(ReportCategory.PHYSICAL);

    var dataHypervisor = new InstanceData();
    dataHypervisor.setInstanceId(tallyInstanceViewHypervisor.getKey().getInstanceId());
    dataHypervisor.setDisplayName(tallyInstanceViewHypervisor.getDisplayName());
    dataHypervisor.setBillingProvider(expectedBillingProvider.asOpenApiEnum());
    dataHypervisor.setLastSeen(tallyInstanceViewHypervisor.getLastSeen());
    dataHypervisor.setMeasurements(List.of(4.0));
    dataHypervisor.setNumberOfGuests(tallyInstanceViewHypervisor.getNumOfGuests());
    dataHypervisor.setCategory(ReportCategory.HYPERVISOR);

    var meta = new InstanceMeta();
    meta.setCount(2);
    meta.setProduct(RHEL_FOR_X86.toString());
    meta.setServiceLevel(ServiceLevelType.PREMIUM);
    meta.setUsage(UsageType.PRODUCTION);
    meta.setBillingProvider(expectedBillingProvider.asOpenApiEnum());
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
            expectedBillingProvider.asOpenApiEnum(),
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

    var tallyInstanceView = new TallyInstanceView();
    tallyInstanceView.setKey(new TallyInstanceViewKey());
    tallyInstanceView.setDisplayName("rhv.example.com");
    tallyInstanceView.setNumOfGuests(3);
    tallyInstanceView.setLastSeen(OffsetDateTime.now());
    tallyInstanceView.getKey().setInstanceId("d6214a0b-b344-4778-831c-d53dcacb2da3");
    tallyInstanceView.setHostBillingProvider(expectedBillingProvider);
    tallyInstanceView.getKey().setMeasurementType(HardwareMeasurementType.AWS);
    tallyInstanceView.getKey().setMetricId(MetricIdUtils.getCores().getValue());

    String month = InstanceMonthlyTotalKey.formatMonthId(tallyInstanceView.getLastSeen());

    // Measurement should come from instance_monthly_totals
    var monthlyTotalMap = new HashMap<InstanceMonthlyTotalKey, Double>();
    monthlyTotalMap.put(
        new InstanceMonthlyTotalKey(month, MetricIdUtils.getInstanceHours().toString()), 5.0);
    tallyInstanceView.setMonthlyTotals(monthlyTotalMap);

    Mockito.when(
            repository.findAllBy(
                eq("owner123456"),
                any(),
                any(),
                any(),
                any(),
                anyInt(),
                anyInt(),
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
    data.setBillingProvider(expectedBillingProvider.asOpenApiEnum());
    data.setLastSeen(tallyInstanceView.getLastSeen());
    data.setMeasurements(List.of(0.0, 5.0));
    data.setNumberOfGuests(tallyInstanceView.getNumOfGuests());
    data.setCloudProvider(CloudProvider.AWS);
    data.setCategory(ReportCategory.CLOUD);

    var meta = new InstanceMeta();
    meta.setCount(1);
    meta.setProduct(ROSA.toString());
    meta.setServiceLevel(ServiceLevelType.PREMIUM);
    meta.setUsage(UsageType.PRODUCTION);
    meta.setMeasurements(List.of("Cores", "Instance-hours"));
    meta.setBillingProvider(expectedBillingProvider.asOpenApiEnum());

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
            expectedBillingProvider.asOpenApiEnum(),
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

    var tallyInstanceView = new TallyInstanceView();
    tallyInstanceView.setKey(new TallyInstanceViewKey());
    tallyInstanceView.setDisplayName("rhv.example.com");
    tallyInstanceView.setNumOfGuests(3);
    tallyInstanceView.setLastSeen(OffsetDateTime.now());
    tallyInstanceView.getKey().setInstanceId("d6214a0b-b344-4778-831c-d53dcacb2da3");
    tallyInstanceView.setHostBillingProvider(expectedBillingProvider);
    tallyInstanceView.getKey().setMeasurementType(HardwareMeasurementType.VIRTUAL);
    tallyInstanceView.getKey().setProductId("RHEL");
    tallyInstanceView.getKey().setMetricId(MetricIdUtils.getSockets().getValue());

    Mockito.when(
            repository.findAllBy(
                eq("owner123456"),
                any(),
                any(),
                any(),
                any(),
                anyInt(),
                anyInt(),
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
                eq("owner123456"),
                any(),
                any(),
                any(),
                any(),
                anyInt(),
                anyInt(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()))
        .thenReturn(new PageImpl<>(new ArrayList<>()));

    verify(repository)
        .findAllBy(
            eq("owner123456"),
            any(),
            any(),
            any(),
            any(),
            anyInt(),
            anyInt(),
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
    host.setHardwareType(HostHardwareType.CLOUD.PHYSICAL);

    Mockito.when(
            hostRepository.getGuestHostsByHypervisorInstanceId(
                eq(host.getOrgId()), eq(host.getInstanceId()), any()))
        .thenReturn(new PageImpl<>(List.of(host)));

    var response = resource.getInstanceGuests(host.getInstanceId(), null, null);

    assertEquals(1, response.getData().size());
  }

  @WithMockRedHatPrincipal("123456")
  @Test
  void testMinCoresOneWhenUomIsCores() {
    BillingProvider expectedBillingProvider = BillingProvider.RED_HAT;

    var tallyInstanceView = new TallyInstanceView();
    tallyInstanceView.setKey(new TallyInstanceViewKey());
    tallyInstanceView.setDisplayName("rhv.example.com");
    tallyInstanceView.setNumOfGuests(3);
    tallyInstanceView.setLastSeen(OffsetDateTime.now());
    tallyInstanceView.getKey().setInstanceId("d6214a0b-b344-4778-831c-d53dcacb2da3");
    tallyInstanceView.setHostBillingProvider(expectedBillingProvider);
    tallyInstanceView.getKey().setMeasurementType(HardwareMeasurementType.VIRTUAL);
    tallyInstanceView.getKey().setProductId("RHEL");
    tallyInstanceView.getKey().setMetricId(MetricIdUtils.getCores().getValue());

    Mockito.when(
            repository.findAllBy(
                eq("owner123456"),
                any(),
                any(),
                any(),
                any(),
                anyInt(),
                anyInt(),
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
            eq("owner123456"),
            any(),
            any(),
            any(),
            any(),
            eq(1),
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
  void testMinSocketsOneWhenUomIsSockets() {
    BillingProvider expectedBillingProvider = BillingProvider.RED_HAT;

    var tallyInstanceView = new TallyInstanceView();
    tallyInstanceView.setKey(new TallyInstanceViewKey());
    tallyInstanceView.setDisplayName("rhv.example.com");
    tallyInstanceView.setNumOfGuests(3);
    tallyInstanceView.setLastSeen(OffsetDateTime.now());
    tallyInstanceView.getKey().setInstanceId("d6214a0b-b344-4778-831c-d53dcacb2da3");
    tallyInstanceView.setHostBillingProvider(expectedBillingProvider);
    tallyInstanceView.getKey().setMeasurementType(HardwareMeasurementType.VIRTUAL);
    tallyInstanceView.getKey().setProductId("RHEL");
    tallyInstanceView.getKey().setMetricId(MetricIdUtils.getSockets().getValue());

    Mockito.when(
            repository.findAllBy(
                eq("owner123456"),
                any(),
                any(),
                any(),
                any(),
                anyInt(),
                anyInt(),
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
            eq("owner123456"),
            any(),
            any(),
            any(),
            any(),
            eq(0),
            eq(1),
            eq(null),
            any(),
            any(),
            any(),
            any(),
            any());
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
                "Sockets",
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
