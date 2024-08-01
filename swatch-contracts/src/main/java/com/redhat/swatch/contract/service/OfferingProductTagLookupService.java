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
package com.redhat.swatch.contract.service;

import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import com.redhat.swatch.configuration.util.ProductTagLookupParams;
import com.redhat.swatch.contract.exception.ErrorCode;
import com.redhat.swatch.contract.exception.ServiceException;
import com.redhat.swatch.contract.openapi.model.OfferingProductTags;
import com.redhat.swatch.contract.repository.OfferingEntity;
import com.redhat.swatch.contract.repository.OfferingRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Common methods to identify product tags between subscription and offering products */
@ApplicationScoped
@Slf4j
@AllArgsConstructor
public class OfferingProductTagLookupService {
  private final OfferingRepository offeringRepository;

  /**
   * This will allow any service to look up the swatch product(s) associated with a given SKU. (This
   * lookup will use the offering information already stored in the database) and map the
   * `product_name` to a swatch `product_tag` via info from `swatch-product-configuration` library.
   * If the offering does not exist then return 404. If it does exist, then return an empty list if
   * there are no tags found for that particular offering.
   *
   * @return OfferingProductTags
   */
  public OfferingProductTags discoverProductTagsBySku(Optional<OfferingEntity> newState) {
    OfferingProductTags productTags = new OfferingProductTags();
    newState.ifPresent(upstreamOffering -> processProductTagsBySku(upstreamOffering, productTags));
    return productTags;
  }

  private static void processProductTagsBySku(
      OfferingEntity offering, OfferingProductTags productTags) {
    var lookupParams =
        ProductTagLookupParams.builder()
            .role(offering.getRole())
            .engIds(offering.getProductIds())
            .productName(offering.getProductName())
            .isPaygEligibleProduct(offering.isMetered())
            .is3rdPartyMigration(offering.isMigrationOffering())
            .level1(offering.getLevel1())
            .level2(offering.getLevel2())
            .build();

    SubscriptionDefinition.getAllProductTags(lookupParams).forEach(productTags::addDataItem);
  }

  public OfferingProductTags findPersistedProductTagsBySku(String sku) {
    /* In https://issues.redhat.com/browse/SWATCH-1957 below duplicate code should be removed and replaced with only table lookup
    Can be done by return productTags by adding product tag addDataItem
    This is because when we run this code without sku sync the product tag won't exist
    for the offerings that are present. Hence contract lookup with fail. */

    OfferingProductTags productTags = new OfferingProductTags(); // NOSONAR
    productTags.setData(new ArrayList<>());
    var offering = offeringRepository.findById(sku); // NOSONAR
    if (offering == null) {
      throw new ServiceException(
          ErrorCode.OFFERING_MISSING_ERROR,
          Response.Status.NOT_FOUND,
          String.format("Sku %s not found in Offering", sku),
          null);
    }

    processProductTagsBySku(offering, productTags);
    return productTags;
  }
}
