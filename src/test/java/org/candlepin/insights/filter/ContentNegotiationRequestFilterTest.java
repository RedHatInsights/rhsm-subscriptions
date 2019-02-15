/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.insights.filter;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import org.jboss.resteasy.util.MediaTypeHelper;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.ws.rs.NotAcceptableException;
import javax.ws.rs.NotSupportedException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;


public class ContentNegotiationRequestFilterTest {

    @Test
    public void acceptHeaderMustContainVendorSpecificJsonHeaderWithNoParameters() throws Exception {
        List<MediaType> types = MediaTypeHelper.parseHeader("application/vnd.api+json");
        assertEquals(1, types.size());

        MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
        headers.put("content-type", Collections.emptyList());

        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        when(requestContext.getHeaderString(eq("Accept"))).thenReturn(types.get(0).toString());
        when(requestContext.getAcceptableMediaTypes()).thenReturn(types);
        when(requestContext.getHeaders()).thenReturn(headers);

        ContentNegotiationRequestFilter filter = new ContentNegotiationRequestFilter();
        filter.filter(requestContext);
    }

    @Test
    public void notAcceptableThrownWhenAcceptHeaderContainsJsonMediaTypeWithParams() {
        List<MediaType> types = MediaTypeHelper.parseHeader("application/vnd.api+json;version=1.0");
        assertEquals(1, types.size());

        MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
        headers.put("content-type", Collections.emptyList());

        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        when(requestContext.getHeaderString(eq("Accept"))).thenReturn(types.get(0).toString());
        when(requestContext.getAcceptableMediaTypes()).thenReturn(types);
        when(requestContext.getHeaders()).thenReturn(headers);

        ContentNegotiationRequestFilter filter = new ContentNegotiationRequestFilter();
        assertThrows(NotAcceptableException.class, () -> {
            filter.filter(requestContext);
        });
    }

    @Test
    public void notAcceptableNotThrownWhenAcceptHeaderOneValidJsonMediaTypeWithoutParams() throws Exception {
        List<MediaType> types = MediaTypeHelper.parseHeader(
            "application/vnd.api+json;version=1.0,application/vnd.api+json,application/vnd.api+json;v=2");
        assertEquals(3, types.size());

        MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
        headers.put("content-type", Collections.emptyList());

        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        when(requestContext.getHeaderString(eq("Accept"))).thenReturn(types.get(0).toString());
        when(requestContext.getAcceptableMediaTypes()).thenReturn(types);
        when(requestContext.getHeaders()).thenReturn(headers);

        ContentNegotiationRequestFilter filter = new ContentNegotiationRequestFilter();
        filter.filter(requestContext);
    }

    @Test
    public void contentTypeHeaderCanNotSpecifyAnyMediaTypeParameters() {
        List<MediaType> types = MediaTypeHelper.parseHeader("application/vnd.api+json;version=1.0");
        assertEquals(1, types.size());

        MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
        headers.put("content-type", types.stream().map(MediaType::toString).collect(Collectors.toList()));

        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        when(requestContext.getHeaderString(eq("Accept"))).thenReturn(null);
        when(requestContext.getAcceptableMediaTypes()).thenReturn(Collections.EMPTY_LIST);
        when(requestContext.getHeaders()).thenReturn(headers);

        ContentNegotiationRequestFilter filter = new ContentNegotiationRequestFilter();
        assertThrows(NotSupportedException.class, () -> {
            filter.filter(requestContext);
        });
    }

    @Test
    public void nonMatchingContentTypesCanHaveParamteters() throws Exception {
        List<MediaType> types = MediaTypeHelper.parseHeader("application/foobar;version=1.0");
        assertEquals(1, types.size());
        assertEquals(1, types.get(0).getParameters().size());

        MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
        headers.put("content-type", types.stream().map(MediaType::toString).collect(Collectors.toList()));

        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        when(requestContext.getHeaderString(eq("Accept"))).thenReturn(null);
        when(requestContext.getAcceptableMediaTypes()).thenReturn(Collections.EMPTY_LIST);
        when(requestContext.getHeaders()).thenReturn(headers);

        ContentNegotiationRequestFilter filter = new ContentNegotiationRequestFilter();
        filter.filter(requestContext);
    }
}
