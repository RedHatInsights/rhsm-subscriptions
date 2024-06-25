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
package org.candlepin.subscriptions.util;

import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import jakarta.ws.rs.core.Response;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.db.OfferingRepository;
import org.candlepin.subscriptions.db.model.Offering;
import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.exception.MissingOfferingException;
import org.candlepin.subscriptions.utilization.admin.api.model.OfferingProductTags;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Common methods to identify product tags between subscription and offering products */
@Component
@Slf4j
public class OfferingProductTagLookupService {
  private final OfferingRepository offeringRepository;

  @Autowired
  public OfferingProductTagLookupService(OfferingRepository offeringRepository) {
    this.offeringRepository = offeringRepository;
  }

  /**
   * This will allow any service to look up the swatch product(s) associated with a given SKU. (This
   * lookup will use the offering information already stored in the database) and map the
   * `product_name` to a swatch `product_tag` via info from `swatch-product-configuration` library.
   * If the offering does not exist then return 404. If it does exist, then return an empty list if
   * there are no tags found for that particular offering.
   *
   * @return OfferingProductTags
   */
  public OfferingProductTags discoverProductTagsBySku(Optional<Offering> newState) {
    OfferingProductTags productTags = new OfferingProductTags();
    newState.ifPresent(upstreamOffering -> processProductTagsBySku(upstreamOffering, productTags));
    return productTags;
  }

  private static void processProductTagsBySku(Offering offering, OfferingProductTags productTags) {
    // lookup product tags by either role or eng IDs
    SubscriptionDefinition.getAllProductTagsByRoleOrEngIds(
            offering.getRole(),
            offering.getProductIds(),
            offering.getProductName(),
            offering.isMetered(),
            offering.isMigrationOffering())
        .forEach(productTags::addDataItem);
  }

  public OfferingProductTags findPersistedProductTagsBySku(String sku) {
    /* In https://issues.redhat.com/browse/SWATCH-1957 below duplicate code should be removed and replaced with only table lookup
    Can be done by return productTags by adding product tag addDataItem
    This is because when we run this code without sku sync the product tag won't exist
    for the offerings that are present. Hence contract lookup with fail. */

    OfferingProductTags productTags = new OfferingProductTags(); // NOSONAR
    var offering = offeringRepository.findOfferingBySku(sku); // NOSONAR
    if (offering == null) {
      throw new MissingOfferingException(
          ErrorCode.OFFERING_MISSING_ERROR,
          Response.Status.NOT_FOUND,
          String.format("Sku %s not found in Offering", sku),
          null);
    }

    processProductTagsBySku(offering, productTags);
    return productTags;
  }
}
