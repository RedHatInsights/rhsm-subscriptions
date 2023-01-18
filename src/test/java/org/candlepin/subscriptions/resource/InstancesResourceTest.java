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

import static org.candlepin.subscriptions.utilization.api.model.ProductId.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.*;
import org.candlepin.subscriptions.db.AccountListSource;
import org.candlepin.subscriptions.db.TallyInstanceViewRepository;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.InstanceMonthlyTotalKey;
import org.candlepin.subscriptions.db.model.TallyInstanceView;
import org.candlepin.subscriptions.db.model.TallyInstanceViewKey;
import org.candlepin.subscriptions.json.Measurement;
import org.candlepin.subscriptions.resteasy.PageLinkCreator;
import org.candlepin.subscriptions.security.WithMockRedHatPrincipal;
import org.candlepin.subscriptions.tally.AccountListSourceException;
import org.candlepin.subscriptions.utilization.api.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"api", "test"})
@WithMockRedHatPrincipal("123456")
class InstancesResourceTest {

  @MockBean TallyInstanceViewRepository repository;
  @MockBean PageLinkCreator pageLinkCreator;
  @MockBean AccountListSource accountListSource;
  @Autowired InstancesResource resource;

  @BeforeEach
  public void setup() throws AccountListSourceException {
    when(accountListSource.containsReportingAccount("account123456")).thenReturn(true);
  }

  @Test
  void testShouldPopulateInstanceResponse() {
    BillingProvider expectedBillingProvider = BillingProvider.AWS;

    var tallyInstanceView = new TallyInstanceView();
    tallyInstanceView.setKey(new TallyInstanceViewKey());
    tallyInstanceView.setDisplayName("rhv.example.com");
    tallyInstanceView.setNumOfGuests(3);
    tallyInstanceView.setLastSeen(OffsetDateTime.now());
    tallyInstanceView
        .getKey()
        .setInstanceId(UUID.fromString("d6214a0b-b344-4778-831c-d53dcacb2da3"));
    tallyInstanceView.setHostBillingProvider(expectedBillingProvider);
    tallyInstanceView.getKey().setMeasurementType(HardwareMeasurementType.VIRTUAL);
    tallyInstanceView.setUom(Measurement.Uom.SOCKETS);

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

    var expectUom =
        List.of(
            "Instance-hours", "Storage-gibibyte-months", "Storage-gibibytes", "Transfer-gibibytes");
    List<Double> expectedMeasurement = new ArrayList<>();
    String month = InstanceMonthlyTotalKey.formatMonthId(tallyInstanceView.getLastSeen());
    for (String uom : expectUom) {
      expectedMeasurement.add(
          Optional.ofNullable(tallyInstanceView.getMonthlyTotals().get(0)).orElse(0.0));
    }
    var data = new InstanceData();
    data.setId(tallyInstanceView.getKey().getInstanceId().toString());
    data.setDisplayName(tallyInstanceView.getDisplayName());
    data.setBillingProvider(expectedBillingProvider.asOpenApiEnum());
    data.setLastSeen(tallyInstanceView.getLastSeen());
    data.setMeasurements(expectedMeasurement);
    data.setNumberOfGuests(tallyInstanceView.getNumOfGuests());
    data.setCategory(HardwareMeasurementType.VIRTUAL.toString());

    var meta = new InstanceMeta();
    meta.setCount(1);
    meta.setProduct(ProductId.RHOSAK);
    meta.setServiceLevel(ServiceLevelType.PREMIUM);
    meta.setUsage(UsageType.PRODUCTION);
    meta.setMeasurements(expectUom);
    meta.setBillingProvider(expectedBillingProvider.asOpenApiEnum());

    var expected = new InstanceResponse();
    expected.setData(List.of(data));
    expected.setMeta(meta);

    InstanceResponse report =
        resource.getInstancesByProduct(
            RHOSAK,
            null,
            null,
            ServiceLevelType.PREMIUM,
            UsageType.PRODUCTION,
            expectedBillingProvider.asOpenApiEnum(),
            null,
            null,
            null,
            null,
            null,
            InstanceReportSort.DISPLAY_NAME,
            null);

    assertEquals(expected, report);
  }

  @Test
  void testShouldPopulateInstanceResponseWithHypervisorAndPhysical() {
    BillingProvider expectedBillingProvider = BillingProvider.RED_HAT;

    var tallyInstanceViewPhysical = new TallyInstanceView();
    tallyInstanceViewPhysical.setKey(new TallyInstanceViewKey());
    tallyInstanceViewPhysical.setDisplayName("rhv.example.com");
    tallyInstanceViewPhysical.setNumOfGuests(3);
    tallyInstanceViewPhysical.setLastSeen(OffsetDateTime.now());
    tallyInstanceViewPhysical
        .getKey()
        .setInstanceId(UUID.fromString("d6214a0b-b344-4778-831c-d53dcacb2da3"));
    tallyInstanceViewPhysical.setHostBillingProvider(expectedBillingProvider);
    tallyInstanceViewPhysical.getKey().setMeasurementType(HardwareMeasurementType.PHYSICAL);
    tallyInstanceViewPhysical.setUom(Measurement.Uom.CORES);

    var tallyInstanceViewHypervisor = new TallyInstanceView();
    tallyInstanceViewHypervisor.setKey(new TallyInstanceViewKey());
    tallyInstanceViewHypervisor.setDisplayName("rhv.example.com");
    tallyInstanceViewHypervisor.setNumOfGuests(3);
    tallyInstanceViewHypervisor.setLastSeen(OffsetDateTime.now());
    tallyInstanceViewHypervisor
        .getKey()
        .setInstanceId(UUID.fromString("d6214a0b-b344-4778-831c-d53dcacb2da3"));
    tallyInstanceViewHypervisor.setHostBillingProvider(expectedBillingProvider);
    tallyInstanceViewHypervisor.getKey().setMeasurementType(HardwareMeasurementType.HYPERVISOR);
    tallyInstanceViewHypervisor.setUom(Measurement.Uom.CORES);

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

    List<Double> expectedMeasurement = new ArrayList<>();
    var dataPhysical = new InstanceData();
    dataPhysical.setId(tallyInstanceViewPhysical.getKey().getInstanceId().toString());
    dataPhysical.setDisplayName(tallyInstanceViewPhysical.getDisplayName());
    dataPhysical.setBillingProvider(expectedBillingProvider.asOpenApiEnum());
    dataPhysical.setLastSeen(tallyInstanceViewPhysical.getLastSeen());
    dataPhysical.setMeasurements(expectedMeasurement);
    dataPhysical.setNumberOfGuests(tallyInstanceViewPhysical.getNumOfGuests());
    dataPhysical.setCategory(HardwareMeasurementType.PHYSICAL.toString());

    var dataHypervisor = new InstanceData();
    dataHypervisor.setId(tallyInstanceViewHypervisor.getKey().getInstanceId().toString());
    dataHypervisor.setDisplayName(tallyInstanceViewHypervisor.getDisplayName());
    dataHypervisor.setBillingProvider(expectedBillingProvider.asOpenApiEnum());
    dataHypervisor.setLastSeen(tallyInstanceViewHypervisor.getLastSeen());
    dataHypervisor.setMeasurements(expectedMeasurement);
    dataHypervisor.setNumberOfGuests(tallyInstanceViewHypervisor.getNumOfGuests());
    dataHypervisor.setCategory(HardwareMeasurementType.HYPERVISOR.toString());

    var meta = new InstanceMeta();
    meta.setCount(2);
    meta.setProduct(RHEL);
    meta.setServiceLevel(ServiceLevelType.PREMIUM);
    meta.setUsage(UsageType.PRODUCTION);
    meta.setBillingProvider(expectedBillingProvider.asOpenApiEnum());
    meta.setMeasurements(new ArrayList<>());

    var expected = new InstanceResponse();
    expected.setData(List.of(dataPhysical, dataHypervisor));
    expected.setMeta(meta);

    InstanceResponse report =
        resource.getInstancesByProduct(
            RHEL,
            null,
            null,
            ServiceLevelType.PREMIUM,
            UsageType.PRODUCTION,
            expectedBillingProvider.asOpenApiEnum(),
            null,
            null,
            null,
            null,
            null,
            InstanceReportSort.DISPLAY_NAME,
            null);

    assertEquals(expected, report);
  }
}
