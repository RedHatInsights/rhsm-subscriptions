/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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

import static org.junit.jupiter.api.Assertions.*;

import org.candlepin.subscriptions.exception.SubscriptionsException;
import org.candlepin.subscriptions.resource.OpenApiJsonResource;
import org.candlepin.subscriptions.resource.OpenApiYamlResource;
import org.candlepin.subscriptions.resource.TallyResource;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Collections;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

@ExtendWith(MockitoExtension.class)
public class IdentityHeaderAuthFilterTest {
    @Mock
    ObjectMapper mockMapper;

    @Mock
    UriInfo mockUriInfo;

    @Mock
    ContainerRequestContext requestContext;

    @Test
    public void filterShouldPassHeaderToSecurityContext() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        IdentityHeaderAuthFilter filter = new IdentityHeaderAuthFilter(mapper);
        // produced via echo '{"identity":{"account_number":"12345678"}}' | base64
        String mockHeader = "eyJpZGVudGl0eSI6eyJhY2NvdW50X251bWJlciI6IjEyMzQ1Njc4In19Cg==";
        Mockito.when(requestContext.getHeaderString("x-rh-identity")).thenReturn(mockHeader);
        Mockito.when(requestContext.getUriInfo()).thenReturn(mockUriInfo);
        Mockito.when(mockUriInfo.getMatchedResources()).thenReturn(
            Collections.singletonList(new TallyResource()));
        filter.filter(requestContext);
        ArgumentCaptor<SecurityContext> securityContextArgument =
            ArgumentCaptor.forClass(SecurityContext.class);
        Mockito.verify(requestContext).setSecurityContext(securityContextArgument.capture());
        SecurityContext context = securityContextArgument.getValue();
        assertNotNull(context);
        assertNotNull(context.getUserPrincipal());
        assertEquals("12345678", context.getUserPrincipal().getName());
    }

    @Test
    public void filterShouldThrowExceptionIfHeaderMissing() {
        IdentityHeaderAuthFilter filter = new IdentityHeaderAuthFilter(mockMapper);
        Mockito.when(requestContext.getHeaderString("x-rh-identity")).thenReturn(null);
        Mockito.when(requestContext.getUriInfo()).thenReturn(mockUriInfo);
        Mockito.when(mockUriInfo.getMatchedResources()).thenReturn(
            Collections.singletonList(new TallyResource()));
        SubscriptionsException e = assertThrows(SubscriptionsException.class,
            () -> filter.filter(requestContext));
        assertEquals(Response.Status.UNAUTHORIZED, e.getStatus());
        Mockito.verifyZeroInteractions(mockMapper);
    }

    @Test
    public void filterShouldSkipIfOpenApiJsonRequested() throws IOException {
        IdentityHeaderAuthFilter filter = new IdentityHeaderAuthFilter(mockMapper);
        Mockito.when(requestContext.getUriInfo()).thenReturn(mockUriInfo);
        Mockito.when(mockUriInfo.getMatchedResources()).thenReturn(
            Collections.singletonList(new OpenApiJsonResource()));
        filter.filter(requestContext);
    }

    @Test
    public void filterShouldSkipIfOpenApiYamlRequested() throws IOException {
        IdentityHeaderAuthFilter filter = new IdentityHeaderAuthFilter(mockMapper);
        Mockito.when(requestContext.getUriInfo()).thenReturn(mockUriInfo);
        Mockito.when(mockUriInfo.getMatchedResources()).thenReturn(
            Collections.singletonList(new OpenApiYamlResource()));
        filter.filter(requestContext);
    }
}
