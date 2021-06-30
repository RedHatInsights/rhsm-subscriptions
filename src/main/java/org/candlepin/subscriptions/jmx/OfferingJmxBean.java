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
package org.candlepin.subscriptions.jmx;

import java.util.Optional;
import org.candlepin.subscriptions.db.model.Offering;
import org.candlepin.subscriptions.product.OfferingSyncController;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedOperationParameter;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;

/** Allows syncing of offerings. */
@Component
@ManagedResource
public class OfferingJmxBean {
  private final OfferingSyncController offeringSync;

  public OfferingJmxBean(OfferingSyncController offeringSync) {
    this.offeringSync = offeringSync;
  }

  @ManagedOperation(description = "Sync an offering from the upstream source.")
  @ManagedOperationParameter(name = "sku", description = "A marketing SKU")
  public String syncOffering(String sku) {
    Optional<Offering> upstream = offeringSync.getUpstreamOffering(sku);
    upstream.ifPresent(offeringSync::syncOffering);
    return upstream
        .map(Offering::toString)
        .orElseGet(
            () -> "{\"message\": \"offeringSku=\"" + sku + "\" was not found/allowlisted.\"}");
  }
}
