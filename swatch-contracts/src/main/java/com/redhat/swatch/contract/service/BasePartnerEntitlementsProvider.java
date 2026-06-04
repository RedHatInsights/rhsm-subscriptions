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

import com.redhat.swatch.clients.rh.partner.gateway.api.model.PageRequest;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.PartnerEntitlementV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.QueryPartnerEntitlementV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.resources.ApiException;
import com.redhat.swatch.clients.rh.partner.gateway.api.resources.PartnerApi;
import com.redhat.swatch.contract.model.PartnerEntitlementsRequest;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@Slf4j
public abstract class BasePartnerEntitlementsProvider {

  @RestClient PartnerApi partnerApi;

  public abstract boolean isFor(PartnerEntitlementsRequest contract);

  protected abstract QueryPartnerEntitlementV1 buildQuery(PartnerEntitlementsRequest contract);

  public PartnerEntitlementV1 getPartnerEntitlement(PartnerEntitlementsRequest contract)
      throws ApiException {
    var query = buildQuery(contract).page(new PageRequest().size(20).number(0));
    log.trace("Call Partner Api using query {}", query);
    var response = partnerApi.getPartnerEntitlements(query);
    if (response != null && response.getContent() != null && !response.getContent().isEmpty()) {
      return response.getContent().get(0);
    }

    return null;
  }
}
