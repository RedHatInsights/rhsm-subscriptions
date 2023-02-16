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
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@QuarkusTransactionalTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContractRepositoryTest {

  @Inject ContractRepository contractRepository;

  Contract actualContract1;

  Contract actualContract2;

  @BeforeAll
  public void setupTestData() {
    // Contract1
    actualContract1 = new Contract();
    var uuid = UUID.randomUUID();
    actualContract1.setUuid(uuid);
    actualContract1.setBillingAccountId("test");
    actualContract1.setStartDate(OffsetDateTime.now());
    actualContract1.setEndDate(OffsetDateTime.now());
    actualContract1.setBillingProvider("test");
    actualContract1.setSku("test");
    actualContract1.setProductId("test");
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

    actualContract1.setMetrics(List.of(contractMetric1, contractMetric2));

    // Contract2
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

    actualContract2.setMetrics(List.of(contractMetric3, contractMetric4));
  }

  @Test
  void
      whenValidContractsWithSameUUIDButDifferentMetricsPresent_thenCanPersistAndRetrieveAllContracts() {

    contractRepository.persist(actualContract1);

    contractRepository.persist(actualContract2);

    var queryResults = contractRepository.findAll();
    assertEquals(2, queryResults.stream().count());
    // System.out.println(queryResults.stream().collect(Collectors.toList()));
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
        actualContract1.getMetrics().get(1),
        queryResults.stream().findFirst().get().getMetrics().get(1));
  }

  @Test
  void whenValidContractPresent_thenCanPersistContractAndRetrieveAndDelete() {
    contractRepository.persist(actualContract1);

    Contract expectedContract = contractRepository.findById(actualContract1.getUuid());
    assertEquals(actualContract1, expectedContract);
    contractRepository.deleteById(actualContract1.getUuid());
    assertNull(contractRepository.findById(actualContract1.getUuid()));
  }

  @AfterAll
  public void cleanupTestData() {
    // contractRepository.findAll().stream().forEach(c ->
    // contractRepository.deleteById(c.getUuid()));
    contractRepository.deleteAll();
  }
}
