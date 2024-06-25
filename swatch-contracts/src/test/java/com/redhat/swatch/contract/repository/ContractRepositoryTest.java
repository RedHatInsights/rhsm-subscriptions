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
package com.redhat.swatch.contract.repository;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.redhat.swatch.contract.QuarkusTransactionalTest;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@QuarkusTest
@QuarkusTransactionalTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContractRepositoryTest {
  final OffsetDateTime BEGIN = OffsetDateTime.parse("2023-01-01T00:00Z");
  final OffsetDateTime END = OffsetDateTime.parse("2024-01-01T00:00Z");

  @Inject ContractRepository contractRepository;
  @Inject OfferingRepository offeringRepository;

  ContractEntity actualContract1;

  ContractEntity actualContract2;

  List<ContractEntity> contractList;

  @BeforeAll
  public void setupTestData() {
    // Contract1 with same UUID but different metrics
    actualContract1 = new ContractEntity();
    var uuid = UUID.randomUUID();
    actualContract1.setUuid(uuid);
    actualContract1.setBillingAccountId("billAcct123");
    actualContract1.setStartDate(BEGIN);
    actualContract1.setEndDate(END);
    actualContract1.setBillingProvider("test123");
    actualContract1.setOffering(offering("BAS123", "BASILISK123"));
    actualContract1.setOrgId("org123");
    actualContract1.setLastUpdated(OffsetDateTime.now());
    actualContract1.setSubscriptionNumber("test");
    actualContract1.setVendorProductCode("vendorTest");

    ContractMetricEntity contractMetric1 = new ContractMetricEntity();
    contractMetric1.setContractUuid(uuid);
    contractMetric1.setMetricId("cpu-hours");
    contractMetric1.setValue(2);

    ContractMetricEntity contractMetric2 = new ContractMetricEntity();
    contractMetric2.setContractUuid(uuid);
    contractMetric2.setMetricId("instance-hours");
    contractMetric2.setValue(4);

    actualContract1.setMetrics(Set.of(contractMetric1, contractMetric2));

    // Contract2 with same UUID but different metrics, and no end date
    actualContract2 = new ContractEntity();
    var uuid2 = UUID.randomUUID();
    actualContract2.setUuid(uuid2);
    actualContract2.setBillingAccountId("billAcct456");
    actualContract2.setStartDate(BEGIN);
    actualContract2.setEndDate(null);
    actualContract2.setBillingProvider("test456");
    actualContract2.setOffering(offering("BAS456", "BASILISK456"));
    actualContract2.setOrgId("org456");
    actualContract2.setLastUpdated(OffsetDateTime.now());
    actualContract2.setSubscriptionNumber("test");
    actualContract2.setVendorProductCode("vendorTest");

    ContractMetricEntity contractMetric3 = new ContractMetricEntity();
    contractMetric3.setContractUuid(uuid2);
    contractMetric3.setMetricId("cpu-hours");
    contractMetric3.setValue(5);

    ContractMetricEntity contractMetric4 = new ContractMetricEntity();
    contractMetric4.setContractUuid(uuid2);
    contractMetric4.setMetricId("instance-hours");
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
        actualContract1.getMetrics().stream().toList().get(1).getContractUuid(),
        queryResults.stream().findFirst().get().getMetrics().stream()
            .toList()
            .get(1)
            .getContractUuid());
    assertEquals(
        actualContract1.getMetrics().stream().toList().get(1).getContractUuid(),
        queryResults.stream().findFirst().get().getMetrics().stream()
            .toList()
            .get(1)
            .getContractUuid());
  }

  @Test
  void whenValidUUID_thenRetrieveContract() {
    ContractEntity contract1 = contractRepository.findContract(actualContract1.getUuid());
    assertEquals(
        actualContract1.getMetrics().stream().toList().get(1).getContractUuid(),
        contract1.getMetrics().stream().toList().get(1).getContractUuid());
    assertEquals(actualContract1.getSubscriptionNumber(), contract1.getSubscriptionNumber());
  }

  @Test
  void whenGetContractWithCorrectParam_thenReturnAllContracts() {
    var spec =
        ContractEntity.metricIdEquals("instance-hours")
            .and(ContractEntity.productTagEquals("BASILISK123"));
    List<ContractEntity> allContracts = contractRepository.getContracts(spec);
    assertEquals(allContracts.get(0).getUuid(), contractList.get(0).getUuid());
  }

  @Test
  void whenGetContractWithMissingMetricIdParam_thenReturnAllContracts() {
    var spec = ContractEntity.productTagEquals("BASILISK123");
    List<ContractEntity> allContracts = contractRepository.getContracts(spec);
    assertEquals(allContracts.get(0).getUuid(), contractList.get(0).getUuid());
  }

  @Test
  void whenValidContractPresent_thenCanRetrieveAndDelete() {
    ContractEntity expectedContract = contractRepository.findById(actualContract2.getUuid());
    assertEquals(
        actualContract2.getMetrics().stream().toList().get(1).getContractUuid(),
        expectedContract.getMetrics().stream().toList().get(1).getContractUuid());
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

  @Test
  void testActiveOnFiltersOutContractsStartingAfterTimestampWithNullEndDate() {
    var spec = ContractEntity.activeOn(BEGIN.minusSeconds(1));
    assertThat(contractRepository.find(ContractEntity.class, spec), empty());
  }

  @Test
  void testActiveOnMatchesContractsStartingOnTimestampWithNullEndDate() {
    var spec = ContractEntity.activeOn(BEGIN);
    assertThat(
        contractRepository.find(ContractEntity.class, spec),
        containsInAnyOrder(actualContract1, actualContract2));
  }

  @Test
  void testActiveOnMatchesContractsStartingBeforeTimestampWithNullEndDate() {
    var spec = ContractEntity.activeOn(BEGIN.plusSeconds(1));
    assertThat(
        contractRepository.find(ContractEntity.class, spec),
        containsInAnyOrder(actualContract1, actualContract2));
  }

  @Test
  void testActiveOnFiltersOutContractsEndingAfterTimestamp() {
    var spec = ContractEntity.activeOn(END.plusSeconds(1));
    assertThat(
        contractRepository.find(ContractEntity.class, spec), containsInAnyOrder(actualContract2));
  }

  @Test
  void testActiveOnFiltersOutContractsEndingOnTimestamp() {
    var spec = ContractEntity.activeOn(END);
    assertThat(
        contractRepository.find(ContractEntity.class, spec), containsInAnyOrder(actualContract2));
  }

  @AfterAll
  public void cleanupTestData() {
    contractRepository.deleteAll();
  }

  @Transactional
  OfferingEntity offering(String sku, String productTag) {
    OfferingEntity offeringEntity = new OfferingEntity();
    offeringEntity.setSku(sku);
    offeringEntity.setProductTags(Set.of(productTag));
    offeringRepository.persist(offeringEntity);
    return offeringEntity;
  }
}
