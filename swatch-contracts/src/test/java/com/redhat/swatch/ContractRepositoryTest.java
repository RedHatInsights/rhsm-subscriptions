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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@QuarkusTransactionalTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContractRepositoryTest {

  @Inject ContractRepository contractRepository;

  @BeforeAll
  public void setupTestData() {
    contractRepository.deleteAll();
  }

  @Test
  void canPersistContract() {
    final Contract contract = new Contract();
    contract.setBillingAccountId("test");
    contract.setStartDate(OffsetDateTime.now());
    contract.setEndDate(OffsetDateTime.now());
    contract.setBillingProvider("test");
    contract.setSku("test");
    contract.setProductId("test");
    var uuid = UUID.randomUUID();
    contract.setUuid(uuid);
    contract.setOrgId("org123");
    contract.setLastUpdated(OffsetDateTime.now());

    ContractMetric contractMetric = new ContractMetric();
    contractMetric.setContractUuid(uuid);
    contractMetric.setMetricId("1");
    contractMetric.setValue(0);
    contract.setSubscriptionNumber("test");
    contract.setMetrics(List.of(contractMetric));
    contractRepository.persist(contract);
    var s = contractRepository.findAll();
    assertEquals(1, s.stream().count());
  }

  @AfterAll
  public void cleanupTestData() {
    // contractRepository.findAll().stream().forEach(c ->
    // contractRepository.deleteById(c.getUuid()));
    contractRepository.deleteAll();
  }
}
