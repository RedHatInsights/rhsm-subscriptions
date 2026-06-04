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
package org.candlepin.subscriptions.tally.facts.product;

import com.redhat.swatch.configuration.util.MetricIdUtils;
import java.util.Set;
import org.candlepin.subscriptions.inventory.db.model.InventoryHostFacts;

/** Rules to apply when normalizing product tags. */
public interface ProductRule {

  Set<String> APPLICABLE_METRIC_IDS =
      Set.of(MetricIdUtils.getCores().toString(), MetricIdUtils.getSockets().toString());

  /**
   * @return if the product rule must be applied for the host.
   */
  boolean appliesTo(ProductRuleContext context);

  /**
   * @return the matched product tags from configuration.
   */
  Set<String> getFilteredProductTags(ProductRuleContext context);

  /**
   * @return all the product tags that are configured for the current rule.
   */
  Set<String> getAllProductTagsFromConfiguration(ProductRuleContext context);

  record ProductRuleContext(
      InventoryHostFacts hostFacts, boolean is3rdPartyMigrated, boolean skipRhsmFacts) {}
}
