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
package com.redhat.swatch.contract.service.export;

import static com.redhat.swatch.clowder.DependencyEndpointSelection.MODE_AUTO;
import static com.redhat.swatch.clowder.DependencyEndpointSelection.VERSION_V1;
import static com.redhat.swatch.clowder.DependencyEndpointSelection.VERSION_V2;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redhat.cloud.event.apps.exportservice.v1.ResourceRequestClass;
import com.redhat.swatch.clients.export.api.resources.ApiException;
import com.redhat.swatch.clients.export.api.resources.ExportApi;
import com.redhat.swatch.clowder.DependencyEndpoint;
import com.redhat.swatch.clowder.DependencyEndpointResolver;
import com.redhat.swatch.clowder.DependencyEndpointSelection;
import com.redhat.swatch.export.ExportServiceRequest;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.Response;
import java.io.File;
import java.net.ConnectException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExportDelegateImplFallbackTest {

  private static final UUID EXPORT_ID = UUID.randomUUID();
  private static final UUID RESOURCE_ID = UUID.randomUUID();

  @Mock DependencyEndpointResolver endpointResolver;
  @Mock ExportServiceApiFactory apiFactory;
  @Mock ExportApi v2Api;
  @Mock ExportApi v1Api;
  @Mock ExportServiceRequest request;
  @Mock ResourceRequestClass resourceRequest;

  private ExportDelegateImpl exportDelegate;

  @BeforeEach
  void setUp() {
    exportDelegate = new ExportDelegateImpl(endpointResolver, apiFactory);
    when(request.getExportRequestUUID()).thenReturn(EXPORT_ID);
    when(request.getApplication()).thenReturn("subscriptions");
    when(request.getRequest()).thenReturn(resourceRequest);
    when(resourceRequest.getUUID()).thenReturn(RESOURCE_ID);
  }

  /** multicluster-TC004 - When V2 is unavailable, the client falls back to V1. */
  @Test
  void fallsBackToV1WhenV2IsUnreachable() throws Exception {
    givenV2ThenV1();
    doThrow(new ProcessingException(new ConnectException("refused")))
        .when(v2Api)
        .downloadExportUpload(any(), any(), any(), any());

    File file = File.createTempFile("export-fallback", ".json");
    file.deleteOnExit();

    assertDoesNotThrow(() -> exportDelegate.upload(file, request));
    verify(v1Api).downloadExportUpload(EXPORT_ID, "subscriptions", RESOURCE_ID, file);
  }

  /** When V2 returns 401/403, the client falls back to V1. */
  @Test
  void fallsBackToV1WhenV2ReturnsUnauthorized() throws Exception {
    givenV2ThenV1();
    doThrow(new ApiException(Response.status(Response.Status.UNAUTHORIZED).build()))
        .when(v2Api)
        .downloadExportUpload(any(), any(), any(), any());

    File file = File.createTempFile("export-auth-fallback", ".json");
    file.deleteOnExit();

    assertDoesNotThrow(() -> exportDelegate.upload(file, request));
    verify(v1Api).downloadExportUpload(EXPORT_ID, "subscriptions", RESOURCE_ID, file);
  }

  @Test
  void fallsBackToV1WhenV2ReturnsForbidden() throws Exception {
    givenV2ThenV1();
    doThrow(new ApiException(Response.status(Response.Status.FORBIDDEN).build()))
        .when(v2Api)
        .downloadExportUpload(any(), any(), any(), any());

    File file = File.createTempFile("export-authz-fallback", ".json");
    file.deleteOnExit();

    assertDoesNotThrow(() -> exportDelegate.upload(file, request));
    verify(v1Api).downloadExportUpload(EXPORT_ID, "subscriptions", RESOURCE_ID, file);
  }

  @Test
  void doesNotFallbackOnOtherClientErrors() throws Exception {
    DependencyEndpointSelection v2 =
        new DependencyEndpointSelection(
            MODE_AUTO, VERSION_V2, DependencyEndpoint.of("https://v2.example", "", "", "", true));
    when(endpointResolver.current()).thenReturn(v2);
    when(apiFactory.clientFor(v2)).thenReturn(v2Api);
    doThrow(new ApiException(Response.status(Response.Status.BAD_REQUEST).build()))
        .when(v2Api)
        .downloadExportUpload(any(), any(), any(), any());

    File file = File.createTempFile("export-no-auth-fallback", ".json");
    file.deleteOnExit();

    org.junit.jupiter.api.Assertions.assertThrows(
        com.redhat.swatch.export.ExportServiceException.class,
        () -> exportDelegate.upload(file, request));
    verify(endpointResolver, never()).tryFallbackToV1();
    verify(v1Api, never()).downloadExportUpload(any(), any(), any(), any());
  }

  private void givenV2ThenV1() {
    DependencyEndpointSelection v2 =
        new DependencyEndpointSelection(
            MODE_AUTO, VERSION_V2, DependencyEndpoint.of("https://v2.example", "", "", "", true));
    DependencyEndpointSelection v1 =
        new DependencyEndpointSelection(
            MODE_AUTO, VERSION_V1, DependencyEndpoint.v1("http://v1.example", "", "", ""));
    when(endpointResolver.current()).thenReturn(v2, v1);
    when(endpointResolver.tryFallbackToV1()).thenReturn(true);
    when(apiFactory.clientFor(v2)).thenReturn(v2Api);
    when(apiFactory.clientFor(v1)).thenReturn(v1Api);
  }

  /** multicluster-TC008 - When both configured endpoints are unreachable, dependency calls fail. */
  @Test
  void doesNotFallbackWhenResolverRejects() throws Exception {
    DependencyEndpointSelection v2 =
        new DependencyEndpointSelection(
            MODE_AUTO, VERSION_V2, DependencyEndpoint.of("https://v2.example", "", "", "", true));
    // Both calls to current() still return V2: fallback was not possible at all.
    when(endpointResolver.current()).thenReturn(v2);
    when(endpointResolver.tryFallbackToV1()).thenReturn(false);
    when(apiFactory.clientFor(v2)).thenReturn(v2Api);
    doThrow(new ProcessingException(new ConnectException("refused")))
        .when(v2Api)
        .downloadExportUpload(any(), any(), any(), any());

    File file = File.createTempFile("export-no-fallback", ".json");
    file.deleteOnExit();

    org.junit.jupiter.api.Assertions.assertThrows(
        com.redhat.swatch.export.ExportServiceException.class,
        () -> exportDelegate.upload(file, request));
    verify(v1Api, never()).downloadExportUpload(any(), any(), any(), any());
  }

  /** multicluster-TC007 - When V2 becomes unavailable, later calls use V1. */
  @Test
  void retriesOnV1WhenAnotherCallAlreadyFellBack() throws Exception {
    DependencyEndpointSelection v2 =
        new DependencyEndpointSelection(
            MODE_AUTO, VERSION_V2, DependencyEndpoint.of("https://v2.example", "", "", "", true));
    DependencyEndpointSelection v1 =
        new DependencyEndpointSelection(
            MODE_AUTO, VERSION_V1, DependencyEndpoint.v1("http://v1.example", "", "", ""));
    // First current() → V2; second current() → V1 (another call already fell back).
    when(endpointResolver.current()).thenReturn(v2, v1);
    when(endpointResolver.tryFallbackToV1()).thenReturn(false);
    when(apiFactory.clientFor(v2)).thenReturn(v2Api);
    when(apiFactory.clientFor(v1)).thenReturn(v1Api);
    doThrow(new ProcessingException(new ConnectException("refused")))
        .when(v2Api)
        .downloadExportUpload(any(), any(), any(), any());

    File file = File.createTempFile("export-already-fell-back", ".json");
    file.deleteOnExit();

    assertDoesNotThrow(() -> exportDelegate.upload(file, request));
    verify(v1Api).downloadExportUpload(EXPORT_ID, "subscriptions", RESOURCE_ID, file);
  }
}
