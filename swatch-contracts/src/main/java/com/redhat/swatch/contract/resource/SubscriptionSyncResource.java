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
package com.redhat.swatch.contract.resource;

import com.redhat.swatch.contract.model.BananaOfferingProductTags;
import com.redhat.swatch.contract.model.OfferingProductTagsMapper;
import com.redhat.swatch.contract.service.SubscriptionSyncService;
import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Contact;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.info.License;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Slf4j
@ApplicationScoped
@RegisterRestClient
@Path("/internal/offerings/{sku}/product_tags")
@OpenAPIDefinition(
    tags = {
      @Tag(name = "widget", description = "Widget operations."),
      @Tag(name = "gasket", description = "Operations related to gaskets")
    },
    info =
        @Info(
            title = "Example API",
            version = "1.0.1",
            contact =
                @Contact(
                    name = "Example API Support",
                    url = "http://exampleurl.com/contact",
                    email = "techsupport@example.com"),
            license =
                @License(
                    name = "Apache 2.0",
                    url = "https://www.apache.org/licenses/LICENSE-2.0.html")))
public class SubscriptionSyncResource {

  @Inject SubscriptionSyncService service;
  @Inject OfferingProductTagsMapper mapper;

  @GET
  @Produces({"application/json"})
  @RolesAllowed({"test", "support", "service"})
  public BananaOfferingProductTags getSkuProductTags(@PathParam("sku") String sku) {
    return mapper.openApiToPojo(service.getOfferingProductTags(sku));
  }
}
