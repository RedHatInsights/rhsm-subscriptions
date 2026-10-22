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
package org.candlepin.subscriptions.resteasy;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/** Utility to create page links for paginated APIs. */
@Component
public class PageLinkCreator {
  private static final Logger log = LoggerFactory.getLogger(PageLinkCreator.class);

  /**
   * Create a PageLinks object with first, last, previous, next API links.
   *
   * @param request the current HTTP request
   * @param page Spring Data JPA page object
   * @return a populated PageLinks object
   */
  public PageLinks getPaginationLinks(HttpServletRequest request, Page<?> page) {
    PageLinks links = new PageLinks();
    if (page.previousPageable() != Pageable.unpaged()) {
      links.setPrevious(buildLinkWithOffset(request, page.previousPageable().getOffset()));
    }
    if (page.nextPageable() != Pageable.unpaged()) {
      links.setNext(buildLinkWithOffset(request, page.nextPageable().getOffset()));
    }
    links.setFirst(buildLinkWithOffset(request, 0));
    int lastPage = page.getTotalPages() - 1;
    if (lastPage < 0) {
      lastPage = 0;
    }
    int lastOffset = (int) PageRequest.of(lastPage, page.getPageable().getPageSize()).getOffset();
    links.setLast(buildLinkWithOffset(request, lastOffset));
    return links;
  }

  private String buildLinkWithOffset(HttpServletRequest request, long offset) {
    try {
      String requestUri = request.getRequestURI();
      String queryString = request.getQueryString();

      UriComponentsBuilder builder = UriComponentsBuilder.fromPath(requestUri);
      if (queryString != null && !queryString.isEmpty()) {
        builder.query(queryString);
      }

      // Replace or add offset parameter
      builder.replaceQueryParam("offset", offset);

      return builder.build().toUriString();
    } catch (Exception e) {
      log.error("Unable to format URI for pagination", e);
      // Fallback to simple path with offset
      return request.getRequestURI() + "?offset=" + offset;
    }
  }
}
