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
package com.redhat.swatch.subscriptions;

import com.redhat.swatch.clients.swatch.internal.subscription.api.resources.ApiException;
import com.redhat.swatch.clients.swatch.internal.subscription.api.resources.InternalSubscriptionsApi;
import com.redhat.swatch.openapi.model.OfferingProductTags;
import com.redhat.swatch.openapi.resource.OfferingsApi;
import com.redhat.swatch.subscriptions.mapper.ProductTagsMapper;
import javax.inject.Inject;
import javax.ws.rs.PathParam;
import javax.ws.rs.ProcessingException;
import org.eclipse.microprofile.rest.client.inject.RestClient;

public class SubscriptionSyncResource implements OfferingsApi {

  @RestClient @Inject InternalSubscriptionsApi internalSubscriptionsApi;

  @Inject ProductTagsMapper mapper;

  public OfferingProductTags getSkuProductTags(@PathParam("sku") String sku) {
    try {
      return mapper.clientToApi(internalSubscriptionsApi.getSkuProductTags(sku));
    } catch (ApiException e) {
      throw new ProcessingException(e);
    }
  }
}
