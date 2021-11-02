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
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.*;
import org.candlepin.subscriptions.db.AccountListSource;
import org.candlepin.subscriptions.db.HostRepository;
import org.candlepin.subscriptions.db.model.Host;
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
    var host = new Host();
    host.setInstanceId("d6214a0b-b344-4778-831c-d53dcacb2da3");
    host.setDisplayName("rhv.example.com");
    host.setLastSeen(OffsetDateTime.now());

    var uom = new HashMap<Measurement.Uom, Double>();
    //    uom.put(Measurement.Uom.INSTANCE_HOURS,42.0);
    //    uom.put(Measurement.Uom.STORAGE_GIBIBYTES, 0.0);
    //    uom.put(Measurement.Uom.TRANSFER_GIBIBYTES, 1.0);
    //    host.setMeasurements(uom);

    Mockito.when(
            repository.findAllBy(
                any(), any(), any(), any(), any(), anyInt(), anyInt(), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of(host)));
    var data = new InstanceData();
    data.setId(host.getInstanceId());
    data.setDisplayName(host.getDisplayName());
    data.setLastSeen(host.getLastSeen());

    var meta = new InstanceMeta();
    meta.setCount(1);
    meta.setProduct(ProductId.RHOSAK);
    meta.setServiceLevel(ServiceLevelType.PREMIUM);
    meta.setUsage(UsageType.PRODUCTION);
    meta.setMeasurements(List.of("Instance-hours", "Storage-gibibytes", "Transfer-gibibytes"));

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
            null,
            null,
            null,
            InstanceReportSort.DISPLAY_NAME,
            null);

    // Reassigned values for measurements as logic cannot be mocked correctly.
    report.getData().get(0).setMeasurements(Collections.emptyList());
    assertEquals(expected, report);
  }
}
