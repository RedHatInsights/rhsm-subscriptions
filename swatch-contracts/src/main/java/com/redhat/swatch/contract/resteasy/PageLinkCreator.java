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
package com.redhat.swatch.contract.resteasy;

import com.redhat.swatch.contract.exception.ErrorCode;
import com.redhat.swatch.contract.exception.SubscriptionsException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.net.URISyntaxException;

/** Utility to create page links for paginated APIs. */
@ApplicationScoped
public class PageLinkCreator {

  /**
   * Create a PageLinks object with first, last, previous, next API links.
   *
   * @param uriInfo pre-existing URI to be used as a template for the page links
   * @param page Spring Data JPA page object
   * @return a populate PageLinks object
   */
  public PageLinks getPaginationLinks(UriInfo uriInfo, Page<?> page) {
    PageLinks links = new PageLinks();
    if (page.hasPrevious()) {
      links.setPrevious(formatUri(uriWithOffset(uriInfo, page.getPreviousOffset())));
    }
    if (page.hasNext()) {
      links.setNext(formatUri(uriWithOffset(uriInfo, page.getNextOffset())));
    }
    links.setFirst(formatUri(uriWithOffset(uriInfo, 0)));
    links.setLast(formatUri(uriWithOffset(uriInfo, page.getLastOffset())));
    return links;
  }

  private URI uriWithOffset(UriInfo uriInfo, long newOffset) {
    return uriInfo.getRequestUriBuilder().replaceQueryParam("offset", newOffset).build();
  }

  private String formatUri(URI uri) {
    try {
      URI serverUri =
          new URI(
              uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), "/", null, null);
      return String.format("/%s", serverUri.relativize(uri).toString());
    } catch (URISyntaxException e) {
      throw new SubscriptionsException(
          ErrorCode.REQUEST_PROCESSING_ERROR,
          Response.Status.INTERNAL_SERVER_ERROR,
          "Unable to format URI",
          e);
    }
  }
}
