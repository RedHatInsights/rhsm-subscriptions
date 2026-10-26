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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.redhat.cloud.event.apps.exportservice.v1.ResourceRequestClass;
import com.redhat.swatch.clients.export.SwatchExportApi;
import com.redhat.swatch.clients.export.api.client.ApiException;
import com.redhat.swatch.export.ExportServiceException;
import com.redhat.swatch.export.ExportServiceRequest;
import com.redhat.swatch.export.api.ExportDelegateImpl;
import java.io.File;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExportDelegateImplFallbackTest {

  private static final UUID EXPORT_ID = UUID.randomUUID();
  private static final UUID RESOURCE_ID = UUID.randomUUID();

  @Mock SwatchExportApi exportApi;
  @Mock ExportServiceRequest request;
  @Mock ResourceRequestClass resourceRequest;

  private ExportDelegateImpl exportDelegate;

  @BeforeEach
  void setUp() {
    exportDelegate = new ExportDelegateImpl(exportApi);
    Mockito.when(request.getExportRequestUUID()).thenReturn(EXPORT_ID);
    Mockito.when(request.getApplication()).thenReturn("subscriptions");
    Mockito.when(request.getRequest()).thenReturn(resourceRequest);
    Mockito.when(resourceRequest.getUUID()).thenReturn(RESOURCE_ID);
  }

  @Test
  void uploadDelegatesToSwatchExportApi() throws Exception {
    File file = File.createTempFile("export-ok", ".json");
    file.deleteOnExit();

    assertDoesNotThrow(() -> exportDelegate.upload(file, request));
    verify(exportApi).downloadExportUpload(EXPORT_ID, "subscriptions", RESOURCE_ID, file);
  }

  @Test
  void uploadWrapsApiException() throws Exception {
    doThrow(new ApiException(500, "boom"))
        .when(exportApi)
        .downloadExportUpload(any(), any(), any(), any());

    File file = File.createTempFile("export-fail", ".json");
    file.deleteOnExit();

    assertThrows(ExportServiceException.class, () -> exportDelegate.upload(file, request));
  }
}
