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
package org.candlepin.subscriptions.conduit.resteasy;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.net.URISyntaxException;
import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.exception.SubscriptionsException;
import org.candlepin.subscriptions.utilization.api.model.PageLinks;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/** Utility to create page links for paginated APIs. */
@Component
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
    if (page.previousPageable() != Pageable.unpaged()) {
      links.setPrevious(formatUri(uriWithOffset(uriInfo, page.previousPageable().getOffset())));
    }
    if (page.nextPageable() != Pageable.unpaged()) {
      links.setNext(formatUri(uriWithOffset(uriInfo, page.nextPageable().getOffset())));
    }
    links.setFirst(formatUri(uriWithOffset(uriInfo, 0)));
    int lastPage = page.getTotalPages() - 1;
    if (lastPage < 0) {
      lastPage = 0;
    }
    int lastOffset = (int) PageRequest.of(lastPage, page.getPageable().getPageSize()).getOffset();
    links.setLast(formatUri(uriWithOffset(uriInfo, lastOffset)));
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
