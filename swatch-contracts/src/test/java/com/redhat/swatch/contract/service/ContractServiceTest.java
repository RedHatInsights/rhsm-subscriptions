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
package com.redhat.swatch.contract.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redhat.swatch.contract.BaseUnitTest;
import com.redhat.swatch.contract.openapi.model.Contract;
import com.redhat.swatch.contract.openapi.model.Metric;
import com.redhat.swatch.contract.repository.ContractEntity;
import com.redhat.swatch.contract.repository.ContractMetricEntity;
import com.redhat.swatch.contract.repository.ContractRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.inject.Inject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContractServiceTest extends BaseUnitTest {
  @Inject ContractService contractService;
  @InjectMock ContractRepository contractRepository;

  ContractEntity actualContract1;

  Contract contractDto;

  @Captor ArgumentCaptor<ContractEntity> contractArgumentCaptor;

  @BeforeAll
  public void setupTestData() {
    actualContract1 = new ContractEntity();
    var uuid = UUID.randomUUID();
    actualContract1.setUuid(uuid);
    actualContract1.setBillingAccountId("billAcct123");
    actualContract1.setStartDate(OffsetDateTime.now());
    actualContract1.setEndDate(OffsetDateTime.now());
    actualContract1.setBillingProvider("test123");
    actualContract1.setSku("BAS123");
    actualContract1.setProductId("BASILISK123");
    actualContract1.setOrgId("org123");
    actualContract1.setLastUpdated(OffsetDateTime.now());
    actualContract1.setSubscriptionNumber("test");

    ContractMetricEntity contractMetric1 = new ContractMetricEntity();
    contractMetric1.setContractUuid(uuid);
    contractMetric1.setMetricId("cpu-hours");
    contractMetric1.setValue(2);

    ContractMetricEntity contractMetric2 = new ContractMetricEntity();
    contractMetric2.setContractUuid(uuid);
    contractMetric2.setMetricId("instance-hours");
    contractMetric2.setValue(4);

    actualContract1.setMetrics(Set.of(contractMetric1, contractMetric2));

    contractDto = new Contract();
    contractDto.setUuid(uuid.toString());
    contractDto.setBillingAccountId("billAcct123");
    contractDto.setStartDate(OffsetDateTime.now());
    contractDto.setEndDate(OffsetDateTime.now());
    contractDto.setBillingProvider("test123");
    contractDto.setSku("BAS123");
    contractDto.setProductId("BASILISK123");
    contractDto.setOrgId("org123");
    contractDto.setSubscriptionNumber("test");

    Metric metric1 = new Metric();
    metric1.setMetricId("instance-hours");
    metric1.setValue(2);

    Metric metric2 = new Metric();
    metric2.setMetricId("cpu-hours");
    metric2.setValue(4);

    contractDto.setMetrics(List.of(metric1, metric2));
  }

  @Test
  void testSaveContracts() {
    doNothing().when(contractRepository).persist(any(ContractEntity.class));
    Contract contractResponse = contractService.createContract(contractDto);
    verify(contractRepository, times(1)).persist(contractArgumentCaptor.capture());
    ContractEntity contract = contractArgumentCaptor.getValue();
    assertEquals(contractDto.getSku(), contract.getSku());
    assertEquals(contractResponse.getUuid(), contract.getUuid().toString());
  }

  @Test
  void testGetContracts() {
    when(contractRepository.getContracts(any())).thenReturn((List.of(actualContract1)));
    Map<String, Object> parameters = new HashMap<>();
    parameters.put("productId", "BASILISK123");
    List<Contract> contractList = contractService.getContracts(parameters);
    verify(contractRepository).getContracts(parameters);
    assertEquals(1, contractList.size());
    assertEquals(2, contractList.get(0).getMetrics().size());
  }

  /*  @Test
  void testUpdateContract() {

    contractService.updateContract(contractDto);
    verify(contractRepository, times(1)).findContract(actualContract1.getUuid());

    //assertNotEquals(contractDto.getUuid(), null);
  }*/

  @Test
  void testDeleteContract() {
    var uuid = UUID.randomUUID();
    when(contractRepository.deleteById(uuid)).thenReturn(true);
    contractService.deleteContract(uuid.toString());
    verify(contractRepository).deleteById(uuid);
  }

  @Test
  void testCreateContractForLogicalUpdate() {
    var dto = new Contract();

    Metric actualMetric1 = new Metric();
    actualMetric1.setMetricId("cpu-hours");
    actualMetric1.setValue(5);

    dto.setMetrics(List.of(actualMetric1));

    ContractMetricEntity expectedMetric2 = new ContractMetricEntity();
    expectedMetric2.setMetricId("cpu-hours");
    expectedMetric2.setValue(5);
    var expected = ContractEntity.builder().metrics(Set.of(expectedMetric2)).build();
    var actual = contractService.createContractForLogicalUpdate(dto);

    // new.uuid != old.uuid
    // new.uuid == new.metrics[].uuid
    // new.endDate == null
    // new.startDate == old.endDate
    assertEquals(
        expected.getMetrics().stream().toList().get(0).getMetricId(),
        actual.getMetrics().stream().toList().get(0).getMetricId());
  }
}
