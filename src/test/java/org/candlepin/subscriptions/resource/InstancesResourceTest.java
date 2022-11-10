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
import org.candlepin.subscriptions.db.HostRepository;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.Host;
import org.candlepin.subscriptions.db.model.HostBucketKey;
import org.candlepin.subscriptions.db.model.HostTallyBucket;
import org.candlepin.subscriptions.db.model.InstanceMonthlyTotalKey;
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

  @MockBean HostRepository repository;
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

    var host = new Host();
    host.setInstanceId("d6214a0b-b344-4778-831c-d53dcacb2da3");
    host.setDisplayName("rhv.example.com");
    host.setBillingProvider(expectedBillingProvider);
    host.setNumOfGuests(3);
    host.setLastSeen(OffsetDateTime.now());

    var bucket = new HostTallyBucket();
    bucket.setMeasurementType(HardwareMeasurementType.VIRTUAL);
    bucket.setKey(new HostBucketKey());
    host.addBucket(bucket);

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
        .thenReturn(new PageImpl<>(List.of(host)));

    var expectUom =
        List.of(
            "Instance-hours", "Storage-gibibyte-months", "Storage-gibibytes", "Transfer-gibibytes");
    List<Double> expectedMeasurement = new ArrayList<>();
    String month = InstanceMonthlyTotalKey.formatMonthId(host.getLastSeen());
    for (String uom : expectUom) {
      expectedMeasurement.add(
          Optional.ofNullable(host.getMonthlyTotal(month, Measurement.Uom.fromValue(uom)))
              .orElse(0.0));
    }
    var data = new InstanceData();
    data.setId(host.getInstanceId());
    data.setDisplayName(host.getDisplayName());
    data.setBillingProvider(expectedBillingProvider.asOpenApiEnum());
    data.setLastSeen(host.getLastSeen());
    data.setMeasurements(expectedMeasurement);
    data.setNumberOfGuests(host.getNumOfGuests());
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
}
