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
package com.redhat.swatch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.redhat.swatch.openapi.model.Metric;
import io.quarkus.test.junit.QuarkusTest;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

@QuarkusTest
public class ContractMapperTest {

  private final ContractMapper mapper = Mappers.getMapper(ContractMapper.class);

  @Test
  public void testEntityToDto() {

    var uuid = UUID.randomUUID();
    var startDate = OffsetDateTime.now();

    var entity = new Contract();

    entity.setUuid(uuid);
    entity.setSku("BAS123");
    entity.setEndDate(null);
    entity.setStartDate(startDate);
    entity.setOrgId("org123");
    entity.setBillingAccountId("billAcct123");
    entity.setBillingProvider("aws");
    entity.setProductId("BASILISK");
    entity.setSubscriptionNumber("subs123");

    var contractMetric = new ContractMetric();
    contractMetric.setContractUuid(uuid);
    contractMetric.setMetricId("Instance-hours");
    contractMetric.setValue(1);
    entity.addMetric(contractMetric);

    var dto = mapper.contractToDto(entity);

    assertEquals(entity.getUuid(), UUID.fromString(dto.getUuid()));
    assertEquals(entity.getOrgId(), dto.getOrgId());
    assertEquals(entity.getStartDate(), dto.getStartDate());
    assertEquals(entity.getEndDate(), dto.getEndDate());
    assertEquals(entity.getSku(), dto.getSku());
    assertEquals(entity.getProductId(), dto.getProductId());
    assertEquals(entity.getSubscriptionNumber(), dto.getSubscriptionNumber());
    assertEquals(entity.getBillingProvider(), dto.getBillingProvider());
    assertEquals(entity.getBillingAccountId(), dto.getBillingAccountId());

    // verify size, metric ids, and values
    assertEquals(entity.getMetrics().size(), dto.getMetrics().size());
    assertEquals(
        entity.getMetrics().stream().map(x -> x.getMetricId()).collect(Collectors.toList()),
        dto.getMetrics().stream().map(x -> x.getMetricId()).collect(Collectors.toList()));
    assertEquals(
        entity.getMetrics().stream().map(x -> x.getValue()).collect(Collectors.toList()),
        dto.getMetrics().stream().map(x -> x.getValue()).collect(Collectors.toList()));
  }

  @Test
  void testDtoToEntity() {

    var uuid = UUID.randomUUID();
    var startDate = OffsetDateTime.now();

    var dto = new com.redhat.swatch.openapi.model.Contract();

    dto.setUuid(uuid.toString());
    dto.setSku("BAS123");
    dto.setEndDate(null);
    dto.setStartDate(startDate);
    dto.setOrgId("org123");
    dto.setBillingAccountId("billAcct123");
    dto.setBillingProvider("aws");
    dto.setProductId("BASILISK");
    dto.setSubscriptionNumber("subs123");

    var metric = new Metric();
    metric.setMetricId("Instance-hours");
    metric.setValue(1);

    dto.addMetricsItem(metric);

    var entity = mapper.dtoToContract(dto);

    assertEquals(dto.getUuid(), entity.getUuid().toString());
    assertEquals(dto.getOrgId(), entity.getOrgId());
    assertEquals(dto.getStartDate(), entity.getStartDate());
    assertEquals(dto.getEndDate(), entity.getEndDate());
    assertEquals(dto.getSku(), entity.getSku());
    assertEquals(dto.getProductId(), entity.getProductId());
    assertEquals(dto.getSubscriptionNumber(), entity.getSubscriptionNumber());
    assertEquals(dto.getBillingProvider(), entity.getBillingProvider());
    assertEquals(dto.getBillingAccountId(), entity.getBillingAccountId());

    // verify size, metric ids, and values
    assertEquals(dto.getMetrics().size(), entity.getMetrics().size());
    assertEquals(
        dto.getMetrics().stream().map(x -> x.getMetricId()).collect(Collectors.toList()),
        dto.getMetrics().stream().map(x -> x.getMetricId()).collect(Collectors.toList()));
    assertEquals(
        dto.getMetrics().stream().map(x -> x.getValue()).collect(Collectors.toList()),
        dto.getMetrics().stream().map(x -> x.getValue()).collect(Collectors.toList()));

    // verify UUID populates in metrics collection
    assertEquals(entity.getUuid(), entity.getMetrics().get(0).getContractUuid());
  }
}
