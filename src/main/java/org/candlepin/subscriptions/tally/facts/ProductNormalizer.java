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
package org.candlepin.subscriptions.tally.facts;

import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.inventory.db.model.InventoryHostFacts;
import org.candlepin.subscriptions.tally.facts.product.ProductRule;
import org.candlepin.subscriptions.tally.facts.product.ProductRule.ProductRuleContext;
import org.candlepin.subscriptions.tally.facts.product.QpcProductRule;
import org.candlepin.subscriptions.tally.facts.product.RhsmProductsProductRule;
import org.candlepin.subscriptions.tally.facts.product.SatelliteRoleProductRule;
import org.candlepin.subscriptions.tally.facts.product.SystemProfileProductIdsProductRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ProductNormalizer {

  private final List<ProductRule> productRules;

  @Autowired
  public ProductNormalizer(
      SystemProfileProductIdsProductRule systemProfileProductIdsProductRule,
      SatelliteRoleProductRule satelliteRoleProductRule,
      RhsmProductsProductRule rhsmProductsProductRule,
      QpcProductRule qpcProductRule) {
    this.productRules =
        List.of(
            systemProfileProductIdsProductRule,
            satelliteRoleProductRule,
            rhsmProductsProductRule,
            qpcProductRule);
  }

  public Set<String> normalizeProducts(
      InventoryHostFacts hostFacts, boolean is3rdPartyMigrated, boolean skipRhsmFacts) {

    ProductRuleContext context =
        new ProductRuleContext(hostFacts, is3rdPartyMigrated, skipRhsmFacts);

    // get products from rules / configuration
    Set<String> productTags = getProductsFromRules(context);

    // clean up the product tags
    reconcileProducts(productTags);

    // if no products were found, log a warning
    if (productTags.isEmpty()) {
      logTraceIfFoundConfiguredProductTags(context);
    }

    return productTags;
  }

  private void logTraceIfFoundConfiguredProductTags(ProductRuleContext context) {
    Set<String> candidateProductTags = getAllConfiguredProductTagsFromRules(context);
    if (!candidateProductTags.isEmpty()) {
      log.warn(
          "No products matched for host with name '{}' and subscription-manager ID '{}'. "
              + "The candidate products were '{}'",
          context.hostFacts().getSubscriptionManagerId(),
          context.hostFacts().getDisplayName(),
          candidateProductTags);
    }
  }

  private Set<String> getProductsFromRules(ProductRuleContext context) {
    return getAllProductTagsFromRules(context, ProductRule::getFilteredProductTags);
  }

  private Set<String> getAllConfiguredProductTagsFromRules(ProductRuleContext context) {
    return getAllProductTagsFromRules(context, ProductRule::getAllProductTagsFromConfiguration);
  }

  private Set<String> getAllProductTagsFromRules(
      ProductRuleContext context, BiFunction<ProductRule, ProductRuleContext, Set<String>> getter) {
    Set<String> productTags = new HashSet<>();

    for (ProductRule productRule : productRules) {
      if (productRule.appliesTo(context)) {
        productTags.addAll(getter.apply(productRule, context));
      }
    }

    return productTags;
  }

  private void reconcileProducts(Set<String> productTags) {
    normalizeRhelVariants(productTags);
    SubscriptionDefinition.pruneIncludedProducts(productTags);
  }

  private void normalizeRhelVariants(Set<String> products) {
    long variantCount = products.stream().filter(this::isRhelVariant).count();
    boolean hasRhel = products.contains("RHEL");
    if ((variantCount == 0 && hasRhel) || variantCount > 1) {
      products.add("RHEL Ungrouped");
    }
  }

  private boolean isRhelVariant(String product) {
    return product.startsWith("RHEL ") && !product.startsWith("RHEL for ");
  }
}
