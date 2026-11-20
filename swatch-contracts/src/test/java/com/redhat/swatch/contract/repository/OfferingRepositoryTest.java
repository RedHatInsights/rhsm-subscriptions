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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.common.model.ServiceLevel;
import com.redhat.swatch.common.model.Usage;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

@QuarkusTest
class OfferingRepositoryTest {
  @Inject OfferingRepository repository;

  @Test
  @TestTransaction
  void testApplySlaUsageDefaults() {
    var missing = offering("defaults-null", null, null);
    var unmapped = offering("defaults-unmapped", ServiceLevel.EMPTY, Usage.EMPTY);
    var standard = offering("defaults-standard", ServiceLevel.STANDARD, null);
    var development = offering("defaults-development", null, Usage.DEVELOPMENT_TEST);
    var explicit =
        offering("defaults-explicit", ServiceLevel.SELF_SUPPORT, Usage.DISASTER_RECOVERY);
    var wildcard = offering("defaults-wildcard", ServiceLevel._ANY, Usage._ANY);
    var excluded = offering("defaults-excluded", null, null);
    var candidates = List.of(missing, unmapped, standard, development, explicit, wildcard);
    candidates.forEach(repository::persist);
    repository.persist(excluded);
    repository.flush();
    repository.update("lastModified = ?1 where sku = ?2", Instant.EPOCH, missing.getSku());

    var eligibleSkus = candidates.stream().map(OfferingEntity::getSku).toList();
    assertEquals(4, repository.applySlaUsageDefaults(eligibleSkus));
    repository.getEntityManager().clear();

    assertOffering(missing.getSku(), ServiceLevel.PREMIUM, Usage.PRODUCTION);
    assertOffering(unmapped.getSku(), ServiceLevel.EMPTY, Usage.EMPTY);
    assertOffering(standard.getSku(), ServiceLevel.STANDARD, Usage.PRODUCTION);
    assertOffering(development.getSku(), ServiceLevel.PREMIUM, Usage.DEVELOPMENT_TEST);
    assertOffering(explicit.getSku(), ServiceLevel.SELF_SUPPORT, Usage.DISASTER_RECOVERY);
    assertOffering(wildcard.getSku(), ServiceLevel._ANY, Usage._ANY);
    assertOffering(excluded.getSku(), null, null);
    assertTrue(repository.findById(missing.getSku()).getLastModified().isAfter(Instant.EPOCH));
    assertEquals(0, repository.applySlaUsageDefaults(eligibleSkus));
  }

  private OfferingEntity offering(String sku, ServiceLevel serviceLevel, Usage usage) {
    return OfferingEntity.builder().sku(sku).serviceLevel(serviceLevel).usage(usage).build();
  }

  private void assertOffering(String sku, ServiceLevel serviceLevel, Usage usage) {
    var offering = repository.findById(sku);
    assertEquals(serviceLevel, offering.getServiceLevel());
    assertEquals(usage, offering.getUsage());
  }

  @Test
  @TestTransaction
  void testFindSkusForDerivedSkus() {
    var expectedOffering = OfferingEntity.builder().sku("foo").derivedSku("derived").build();
    var extra = OfferingEntity.builder().sku("foo2").build();
    repository.persist(expectedOffering);
    repository.persist(extra);
    var actual = repository.findSkusForDerivedSkus(Set.of("derived")).collect(Collectors.toSet());
    assertEquals(Set.of("foo"), actual);
  }

  @Test
  @TestTransaction
  void testFindSkusForChildSkus() {
    var expectedOffering = OfferingEntity.builder().sku("foo").childSkus(Set.of("child")).build();
    var extra = OfferingEntity.builder().sku("foo2").build();
    repository.persist(expectedOffering);
    repository.persist(extra);
    var actual = repository.findSkusForChildSku("child").collect(Collectors.toSet());
    assertEquals(Set.of("foo"), actual);
  }

  @Test
  @TestTransaction
  void testFindAllDistinctSkus() {
    var offering1 = OfferingEntity.builder().sku("foo").build();
    var offering2 = OfferingEntity.builder().sku("foo2").build();
    repository.persist(offering1);
    repository.persist(offering2);
    var actual = repository.findAllDistinctSkus();
    assertTrue(actual.contains(offering1.getSku()));
    assertTrue(actual.contains(offering2.getSku()));
  }
}
