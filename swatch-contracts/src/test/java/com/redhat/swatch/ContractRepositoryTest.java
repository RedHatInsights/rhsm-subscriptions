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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.quarkus.test.junit.QuarkusTest;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

@QuarkusTest
@QuarkusTransactionalTest
// @QuarkusTestResource(value = PostgresResource.class, restrictToAnnotatedClass = true)
@DisabledIfEnvironmentVariable(named = "CI", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContractRepositoryTest {

  @Inject ContractRepository contractRepository;

  Contract actualContract1;

  Contract actualContract2;

  List<Contract> contractList;

  @BeforeAll
  public void setupTestData() {
    // Contract1 with same UUID but different metrics
    actualContract1 = new Contract();
    var uuid = UUID.randomUUID();
    actualContract1.setUuid(uuid);
    actualContract1.setBillingAccountId("test");
    actualContract1.setStartDate(OffsetDateTime.now());
    actualContract1.setEndDate(OffsetDateTime.now());
    actualContract1.setBillingProvider("test");
    actualContract1.setSku("test");
    actualContract1.setProductId("p1");
    actualContract1.setOrgId("org123");
    actualContract1.setLastUpdated(OffsetDateTime.now());
    actualContract1.setSubscriptionNumber("test");

    ContractMetric contractMetric1 = new ContractMetric();
    contractMetric1.setContractUuid(uuid);
    contractMetric1.setMetricId("instance-hours");
    contractMetric1.setValue(2);

    ContractMetric contractMetric2 = new ContractMetric();
    contractMetric2.setContractUuid(uuid);
    contractMetric2.setMetricId("cpu-hours");
    contractMetric2.setValue(4);

    actualContract1.setMetrics(Set.of(contractMetric1, contractMetric2));

    // Contract2 with same UUID but different metrics
    actualContract2 = new Contract();
    var uuid2 = UUID.randomUUID();
    actualContract2.setUuid(uuid2);
    actualContract2.setBillingAccountId("test");
    actualContract2.setStartDate(OffsetDateTime.now());
    actualContract2.setEndDate(OffsetDateTime.now());
    actualContract2.setBillingProvider("test");
    actualContract2.setSku("test");
    actualContract2.setProductId("test");
    actualContract2.setOrgId("org123");
    actualContract2.setLastUpdated(OffsetDateTime.now());
    actualContract2.setSubscriptionNumber("test");

    ContractMetric contractMetric3 = new ContractMetric();
    contractMetric3.setContractUuid(uuid2);
    contractMetric3.setMetricId("instance-hours");
    contractMetric3.setValue(5);

    ContractMetric contractMetric4 = new ContractMetric();
    contractMetric4.setContractUuid(uuid2);
    contractMetric4.setMetricId("cpu-hours");
    contractMetric4.setValue(10);

    actualContract2.setMetrics(Set.of(contractMetric3, contractMetric4));

    contractRepository.persist(actualContract1);

    contractRepository.persist(actualContract2);

    contractList = List.of(actualContract1, actualContract2);
  }

  @Test
  void
      whenValidContractsWithUUIDButDifferentMetricsPresent_thenCanPersistAndRetrieveAllContracts() {

    var queryResults = contractRepository.findAll();
    assertEquals(2, queryResults.stream().count());
    assertEquals(
        1,
        queryResults.stream()
            .filter(x -> Objects.equals(actualContract1.getUuid(), x.getUuid()))
            .count());
    assertEquals(
        actualContract1.getUuid(),
        queryResults.stream()
            .filter(x -> Objects.equals(actualContract1.getUuid(), x.getUuid()))
            .findAny()
            .get()
            .getUuid());
    assertEquals(2, queryResults.stream().findFirst().get().getMetrics().size());
    assertEquals(
        actualContract1.getMetrics().stream().toList().get(1).getMetricId(),
        queryResults.stream().findFirst().get().getMetrics().stream()
            .toList()
            .get(1)
            .getMetricId());
    assertEquals(
        actualContract1.getMetrics().stream().toList().get(1).getValue(),
        queryResults.stream().findFirst().get().getMetrics().stream().toList().get(1).getValue());
  }

  @Test
  void whenValidUUID_thenRetrieveContract() {
    Contract contract1 = contractRepository.findContract(actualContract1.getUuid());
    assertEquals(
        actualContract1.getMetrics().stream().toList().get(1).getValue(),
        contract1.getMetrics().stream().toList().get(1).getValue());
    assertEquals(actualContract1.getSubscriptionNumber(), contract1.getSubscriptionNumber());
  }

  @Test
  void whenGetContractWithEmptyParam_thenReturnAllContracts() {
    List<Contract> allContracts = contractRepository.getContracts(null);
    assertEquals(allContracts.get(0).getUuid(), contractList.get(0).getUuid());

    Map<String, Object> parameters = new HashMap<>();
    parameters.put("productId", null);
    allContracts = contractRepository.getContracts(parameters);
    assertEquals(allContracts.get(0).getUuid(), contractList.get(0).getUuid());
  }

  @Test
  void whenGetContractWithCorrectParam_thenReturnAllContracts() {
    Map<String, Object> parameters = new HashMap<>();
    parameters.put("metricId", "instance-hours");
    parameters.put("productId", "p1");
    List<Contract> allContracts = contractRepository.getContracts(parameters);
    assertEquals(allContracts.get(0).getUuid(), contractList.get(0).getUuid());
  }

  @Test
  void whenValidContractPresent_thenCanRetrieveAndDelete() {
    Contract expectedContract = contractRepository.findById(actualContract2.getUuid());
    assertEquals(
        actualContract2.getMetrics().stream().toList().get(1).getValue(),
        expectedContract.getMetrics().stream().toList().get(1).getValue());
    assertEquals(actualContract2.getSubscriptionNumber(), expectedContract.getSubscriptionNumber());
    contractRepository.deleteById(actualContract2.getUuid());
    assertNull(contractRepository.findById(actualContract2.getUuid()));
  }

  @Test
  void whenInValidContractPresent_thenCannotRetrieveAndDelete() {
    var uuid = UUID.randomUUID();
    assertNull(contractRepository.findById(uuid));
    assertFalse(contractRepository.deleteById(uuid));
  }

  @AfterAll
  public void cleanupTestData() {
    // contractRepository.deleteAll();
  }
}
