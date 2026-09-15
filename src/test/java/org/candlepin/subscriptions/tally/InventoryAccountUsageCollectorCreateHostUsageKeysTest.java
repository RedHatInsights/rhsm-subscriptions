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
package org.candlepin.subscriptions.tally;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.tally.UsageCalculation.Key;
import org.candlepin.subscriptions.tally.facts.NormalizedFacts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"worker", "test", "test-inventory"})
class InventoryAccountUsageCollectorCreateHostUsageKeysTest {
  private static final String RHEL_PRODUCT = "RHEL for x86";
  private static final String OPENSHIFT_PRODUCT = "OpenShift Container Platform";
  private static final String INVALID_PRODUCT = "InvalidProduct";

  @Autowired InventoryAccountUsageCollector collector;

  private NormalizedFacts normalizedFacts;

  @BeforeEach
  void setUp() {
    normalizedFacts = new NormalizedFacts();
  }

  @Test
  void createHostUsageKeysWithSingleProductAndHostSla() {
    Set<String> products = Set.of(RHEL_PRODUCT);
    normalizedFacts.setProducts(Set.of(RHEL_PRODUCT));
    normalizedFacts.setSla(ServiceLevel.PREMIUM);
    normalizedFacts.setUsage(Usage.PRODUCTION);

    Set<Key> keys = collector.createHostUsageKeys(products, normalizedFacts);

    assertNotNull(keys);
    assertFalse(keys.isEmpty());

    assertTrue(keys.stream().allMatch(k -> RHEL_PRODUCT.equals(k.getProductId())));
    assertTrue(
        keys.stream()
            .anyMatch(k -> k.getSla() == ServiceLevel.PREMIUM && k.getUsage() == Usage.PRODUCTION));
    assertTrue(
        keys.stream().anyMatch(k -> k.getSla() == ServiceLevel._ANY && k.getUsage() == Usage._ANY));
  }

  @Test
  void createHostUsageKeysWithEmptyHostSlaUsesProductDefault() {
    Set<String> products = Set.of(RHEL_PRODUCT);
    normalizedFacts.setProducts(Set.of(RHEL_PRODUCT));
    normalizedFacts.setSla(ServiceLevel.EMPTY);
    normalizedFacts.setUsage(Usage.PRODUCTION);

    Set<Key> keys = collector.createHostUsageKeys(products, normalizedFacts);

    assertNotNull(keys);
    assertFalse(keys.isEmpty());
    assertTrue(keys.stream().allMatch(k -> RHEL_PRODUCT.equals(k.getProductId())));
    assertTrue(keys.stream().anyMatch(k -> k.getSla() == ServiceLevel._ANY));
  }

  @Test
  void createHostUsageKeysWithEmptyHostUsageUsesProductDefault() {
    Set<String> products = Set.of(RHEL_PRODUCT);
    normalizedFacts.setProducts(Set.of(RHEL_PRODUCT));
    normalizedFacts.setSla(ServiceLevel.PREMIUM);
    normalizedFacts.setUsage(Usage.EMPTY);

    Set<Key> keys = collector.createHostUsageKeys(products, normalizedFacts);

    assertNotNull(keys);
    assertFalse(keys.isEmpty());
    assertTrue(keys.stream().allMatch(k -> RHEL_PRODUCT.equals(k.getProductId())));
    assertTrue(keys.stream().anyMatch(k -> k.getUsage() == Usage._ANY));
  }

  @Test
  void createHostUsageKeysWithBothEmptySlaAndUsageUsesDefaults() {
    Set<String> products = Set.of(RHEL_PRODUCT);
    normalizedFacts.setProducts(Set.of(RHEL_PRODUCT));
    normalizedFacts.setSla(ServiceLevel.EMPTY);
    normalizedFacts.setUsage(Usage.EMPTY);

    Set<Key> keys = collector.createHostUsageKeys(products, normalizedFacts);

    assertNotNull(keys);
    assertFalse(keys.isEmpty());
    assertTrue(
        keys.stream().anyMatch(k -> k.getSla() == ServiceLevel._ANY && k.getUsage() == Usage._ANY));
  }

  @Test
  void createHostUsageKeysAlwaysIncludesAnyWildcards() {
    Set<String> products = Set.of(RHEL_PRODUCT);
    normalizedFacts.setProducts(Set.of(RHEL_PRODUCT));
    normalizedFacts.setSla(ServiceLevel.STANDARD);
    normalizedFacts.setUsage(Usage.DEVELOPMENT_TEST);

    Set<Key> keys = collector.createHostUsageKeys(products, normalizedFacts);

    assertTrue(
        keys.stream().anyMatch(k -> k.getSla() == ServiceLevel._ANY && k.getUsage() == Usage._ANY));
    assertTrue(
        keys.stream()
            .anyMatch(
                k -> k.getSla() == ServiceLevel._ANY && k.getUsage() == Usage.DEVELOPMENT_TEST));
    assertTrue(
        keys.stream()
            .anyMatch(k -> k.getSla() == ServiceLevel.STANDARD && k.getUsage() == Usage._ANY));
  }

  @Test
  void createHostUsageKeysWithMultipleApplicableProducts() {
    Set<String> products = Set.of(RHEL_PRODUCT, OPENSHIFT_PRODUCT);
    normalizedFacts.setProducts(Set.of(RHEL_PRODUCT, OPENSHIFT_PRODUCT));
    normalizedFacts.setSla(ServiceLevel.PREMIUM);
    normalizedFacts.setUsage(Usage.PRODUCTION);

    Set<Key> keys = collector.createHostUsageKeys(products, normalizedFacts);

    assertNotNull(keys);
    assertFalse(keys.isEmpty());

    Set<String> productIds = keys.stream().map(Key::getProductId).collect(Collectors.toSet());
    assertTrue(productIds.contains(RHEL_PRODUCT));
    assertTrue(productIds.contains(OPENSHIFT_PRODUCT));

    long rhelCount = keys.stream().filter(k -> RHEL_PRODUCT.equals(k.getProductId())).count();
    long openshiftCount =
        keys.stream().filter(k -> OPENSHIFT_PRODUCT.equals(k.getProductId())).count();
    assertEquals(rhelCount, openshiftCount);
  }

  @Test
  void createHostUsageKeysFiltersOutNonApplicableProducts() {
    Set<String> applicableProducts = Set.of(RHEL_PRODUCT);
    normalizedFacts.setProducts(Set.of(RHEL_PRODUCT, INVALID_PRODUCT));
    normalizedFacts.setSla(ServiceLevel.PREMIUM);
    normalizedFacts.setUsage(Usage.PRODUCTION);

    Set<Key> keys = collector.createHostUsageKeys(applicableProducts, normalizedFacts);

    assertTrue(keys.stream().allMatch(k -> RHEL_PRODUCT.equals(k.getProductId())));
    assertFalse(keys.stream().anyMatch(k -> INVALID_PRODUCT.equals(k.getProductId())));
  }

  @Test
  void createHostUsageKeysWhenHostHasNoApplicableProducts() {
    Set<String> applicableProducts = Set.of(RHEL_PRODUCT);
    normalizedFacts.setProducts(Set.of(INVALID_PRODUCT));
    normalizedFacts.setSla(ServiceLevel.PREMIUM);
    normalizedFacts.setUsage(Usage.PRODUCTION);

    Set<Key> keys = collector.createHostUsageKeys(applicableProducts, normalizedFacts);

    assertTrue(keys.isEmpty());
  }

  @Test
  void createHostUsageKeysWithEmptyApplicableProducts() {
    Set<String> applicableProducts = Set.of();
    normalizedFacts.setProducts(Set.of(RHEL_PRODUCT));
    normalizedFacts.setSla(ServiceLevel.PREMIUM);
    normalizedFacts.setUsage(Usage.PRODUCTION);

    Set<Key> keys = collector.createHostUsageKeys(applicableProducts, normalizedFacts);

    assertTrue(keys.isEmpty());
  }

  @Test
  void createHostUsageKeysWithEmptyHostProducts() {
    Set<String> applicableProducts = Set.of(RHEL_PRODUCT);
    normalizedFacts.setProducts(Set.of());
    normalizedFacts.setSla(ServiceLevel.PREMIUM);
    normalizedFacts.setUsage(Usage.PRODUCTION);

    Set<Key> keys = collector.createHostUsageKeys(applicableProducts, normalizedFacts);

    assertTrue(keys.isEmpty());
  }

  @Test
  void createHostUsageKeysAllBillingProvidersAreAny() {
    Set<String> products = Set.of(RHEL_PRODUCT);
    normalizedFacts.setProducts(Set.of(RHEL_PRODUCT));
    normalizedFacts.setSla(ServiceLevel.PREMIUM);
    normalizedFacts.setUsage(Usage.PRODUCTION);

    Set<Key> keys = collector.createHostUsageKeys(products, normalizedFacts);

    assertTrue(keys.stream().allMatch(k -> BillingProvider._ANY.equals(k.getBillingProvider())));
  }

  @Test
  void createHostUsageKeysAllBillingAccountIdsAreAny() {
    Set<String> products = Set.of(RHEL_PRODUCT);
    normalizedFacts.setProducts(Set.of(RHEL_PRODUCT));
    normalizedFacts.setSla(ServiceLevel.PREMIUM);
    normalizedFacts.setUsage(Usage.PRODUCTION);

    Set<Key> keys = collector.createHostUsageKeys(products, normalizedFacts);

    assertTrue(keys.stream().allMatch(k -> "_ANY".equals(k.getBillingAccountId())));
  }

  @Test
  void createHostUsageKeysGeneratesExpectedNumberOfCombinations() {
    Set<String> products = Set.of(RHEL_PRODUCT);
    normalizedFacts.setProducts(Set.of(RHEL_PRODUCT));
    normalizedFacts.setSla(ServiceLevel.PREMIUM);
    normalizedFacts.setUsage(Usage.PRODUCTION);

    Set<Key> keys = collector.createHostUsageKeys(products, normalizedFacts);

    assertEquals(4, keys.size());
  }

  @Test
  void createHostUsageKeysContainsExpectedKeyCompositions() {
    Set<String> products = Set.of(RHEL_PRODUCT);
    normalizedFacts.setProducts(Set.of(RHEL_PRODUCT));
    normalizedFacts.setSla(ServiceLevel.STANDARD);
    normalizedFacts.setUsage(Usage.DEVELOPMENT_TEST);

    Set<Key> keys = collector.createHostUsageKeys(products, normalizedFacts);

    assertTrue(
        keys.contains(
            new Key(
                RHEL_PRODUCT,
                ServiceLevel.STANDARD,
                Usage.DEVELOPMENT_TEST,
                BillingProvider._ANY,
                "_ANY")));
    assertTrue(
        keys.contains(
            new Key(
                RHEL_PRODUCT, ServiceLevel.STANDARD, Usage._ANY, BillingProvider._ANY, "_ANY")));
    assertTrue(
        keys.contains(
            new Key(
                RHEL_PRODUCT,
                ServiceLevel._ANY,
                Usage.DEVELOPMENT_TEST,
                BillingProvider._ANY,
                "_ANY")));
    assertTrue(
        keys.contains(
            new Key(RHEL_PRODUCT, ServiceLevel._ANY, Usage._ANY, BillingProvider._ANY, "_ANY")));
  }

  @Test
  void createHostUsageKeysWithDifferentServiceLevels() {
    Set<String> products = Set.of(RHEL_PRODUCT);
    normalizedFacts.setProducts(Set.of(RHEL_PRODUCT));
    normalizedFacts.setSla(ServiceLevel.STANDARD);
    normalizedFacts.setUsage(Usage.PRODUCTION);

    Set<Key> keys = collector.createHostUsageKeys(products, normalizedFacts);

    assertTrue(keys.stream().anyMatch(k -> k.getSla() == ServiceLevel.STANDARD));
    assertTrue(keys.stream().anyMatch(k -> k.getSla() == ServiceLevel._ANY));
  }

  @Test
  void createHostUsageKeysWithDifferentUsageLevels() {
    Set<String> products = Set.of(RHEL_PRODUCT);
    normalizedFacts.setProducts(Set.of(RHEL_PRODUCT));
    normalizedFacts.setSla(ServiceLevel.PREMIUM);
    normalizedFacts.setUsage(Usage.DEVELOPMENT_TEST);

    Set<Key> keys = collector.createHostUsageKeys(products, normalizedFacts);

    assertTrue(keys.stream().anyMatch(k -> k.getUsage() == Usage.DEVELOPMENT_TEST));
    assertTrue(keys.stream().anyMatch(k -> k.getUsage() == Usage._ANY));
  }

  @Test
  void createHostUsageKeysNoDuplicateKeys() {
    Set<String> products = Set.of(RHEL_PRODUCT);
    normalizedFacts.setProducts(Set.of(RHEL_PRODUCT));
    normalizedFacts.setSla(ServiceLevel.PREMIUM);
    normalizedFacts.setUsage(Usage.PRODUCTION);

    Set<Key> keys = collector.createHostUsageKeys(products, normalizedFacts);

    assertEquals(keys.size(), keys.stream().distinct().count());
  }
}
