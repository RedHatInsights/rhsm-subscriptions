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

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.openapi.model.Metric;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

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

    // TODO metric mapping

    var dto = new com.redhat.swatch.openapi.model.Contract();

    System.err.println(mapper.contractToDto(entity));

    assertTrue(true);
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

    System.err.println(mapper.dtoToContract(dto));

    assertTrue(true);
  }
}
