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
package com.redhat.swatch.contract.model;

import com.redhat.swatch.clients.rh.partner.gateway.api.model.RhEntitlementV1;
import com.redhat.swatch.contract.repository.OfferingEntity;
import com.redhat.swatch.contract.repository.OfferingRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;
import java.util.List;
import java.util.Objects;
import lombok.AllArgsConstructor;
import org.mapstruct.Named;

@ApplicationScoped
@AllArgsConstructor
public class ContractOfferingMapper {
  private final OfferingRepository offeringRepository;

  @Named("offering")
  public OfferingEntity findOffering(List<RhEntitlementV1> rhEntitlements) {
    if (rhEntitlements == null) {
      return null;
    }

    String sku =
        rhEntitlements.stream()
            .map(RhEntitlementV1::getSku)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);

    if (sku == null) {
      return null;
    }

    var offering = offeringRepository.findById(sku);
    if (Objects.isNull(offering)) {
      throw new BadRequestException("Could not find sku " + sku);
    }

    return offering;
  }
}
