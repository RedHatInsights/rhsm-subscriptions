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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import jakarta.ws.rs.core.UriInfo;
import java.util.Arrays;
import java.util.Collections;
import org.jboss.resteasy.specimpl.ResteasyUriBuilderImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PageLinkCreatorTest {
  @Mock UriInfo uriInfo;

  @BeforeEach
  void setupUriInfo() {
    Mockito.when(uriInfo.getRequestUriBuilder()).thenReturn(new ResteasyUriBuilderImpl());
  }

  @Test
  void testNoResultsOffsets() {
    Pageable pageable = new Pageable(50, 0, 0);
    Page<Object> page = new Page<>(Collections.emptyList(), pageable);
    PageLinks links = new PageLinkCreator().getPaginationLinks(uriInfo, page);
    assertEquals("/?offset=0", links.getFirst());
    assertEquals("/?offset=0", links.getLast());
    assertNull(links.getPrevious());
    assertNull(links.getNext());
  }

  @Test
  void testPagingWorksFromFirstPage() {
    Pageable pageable = new Pageable(1, 0, 3);
    Page<Object> page =
        new Page<Object>(Arrays.asList(new Object(), new Object(), new Object()), pageable);
    PageLinks links = new PageLinkCreator().getPaginationLinks(uriInfo, page);
    assertEquals("/?offset=0", links.getFirst());
    assertEquals("/?offset=2", links.getLast());
    assertNull(links.getPrevious());
    assertEquals("/?offset=1", links.getNext());
  }

  @Test
  void testPagingWorksFromLastPage() {
    Pageable pageable = new Pageable(1, 2, 3);
    Page<Object> page =
        new Page<>(Arrays.asList(new Object(), new Object(), new Object()), pageable);
    PageLinks links = new PageLinkCreator().getPaginationLinks(uriInfo, page);
    assertEquals("/?offset=0", links.getFirst());
    assertEquals("/?offset=2", links.getLast());
    assertEquals("/?offset=1", links.getPrevious());
    assertNull(links.getNext());
  }

  @Test
  void testPagingWorksFromMiddlePage() {
    Pageable pageable = new Pageable(1, 1, 3);
    Page<Object> page =
        new Page<>(Arrays.asList(new Object(), new Object(), new Object()), pageable);
    PageLinks links = new PageLinkCreator().getPaginationLinks(uriInfo, page);
    assertEquals("/?offset=0", links.getFirst());
    assertEquals("/?offset=2", links.getLast());
    assertEquals("/?offset=0", links.getPrevious());
    assertEquals("/?offset=2", links.getNext());
  }
}
