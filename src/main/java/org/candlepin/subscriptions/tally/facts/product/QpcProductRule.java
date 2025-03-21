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

import java.util.HashSet;
import java.util.Set;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

@Service
public class QpcProductRule implements ProductRule {

  public static final String RHEL = "RHEL";
  public static final String RHEL_FOR_X86 = "RHEL for x86";
  public static final String RHEL_FOR_ARM = "RHEL for ARM";
  public static final String RHEL_FOR_IBM_POWER = "RHEL for IBM Power";

  @Override
  public boolean appliesTo(ProductRuleContext context) {
    Set<String> qpcProducts = context.hostFacts().getQpcProducts();
    return qpcProducts != null && qpcProducts.contains("RHEL");
  }

  @Override
  public Set<String> getFilteredProductTags(ProductRuleContext context) {
    Set<String> products = new HashSet<>();
    if (context.hostFacts().getSystemProfileArch() != null
        && CollectionUtils.isEmpty(context.hostFacts().getSystemProfileProductIds())) {
      switch (context.hostFacts().getSystemProfileArch()) {
        case "x86_64", "i686", "i386":
          products.add(RHEL_FOR_X86);
          break;
        case "aarch64":
          products.add(RHEL_FOR_ARM);
          break;
        case "ppc64le":
          products.add(RHEL_FOR_IBM_POWER);
          break;
        default:
          break;
      }
    }
    products.add(RHEL);
    return products;
  }

  @Override
  public Set<String> getAllProductTagsFromConfiguration(ProductRuleContext context) {
    return Set.of(RHEL_FOR_X86, RHEL_FOR_ARM, RHEL_FOR_IBM_POWER, RHEL);
  }
}
