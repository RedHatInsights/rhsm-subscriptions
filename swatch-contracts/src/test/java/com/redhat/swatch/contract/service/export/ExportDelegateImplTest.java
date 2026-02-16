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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.redhat.cloud.event.apps.exportservice.v1.ResourceRequestClass;
import com.redhat.swatch.contract.test.resources.ExportServiceWireMockResource;
import com.redhat.swatch.contract.test.resources.InjectWireMock;
import com.redhat.swatch.export.ExportServiceRequest;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.io.File;
import java.nio.file.Files;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(ExportServiceWireMockResource.class)
class ExportDelegateImplTest {

  private static final UUID EXPORT_ID = UUID.randomUUID();
  private static final String APPLICATION = "subscriptions";
  private static final UUID RESOURCE_ID = UUID.randomUUID();

  @Inject ExportDelegateImpl exportDelegate;
  @InjectWireMock ExportServiceWireMockResource wireMockResource;

  private ExportServiceRequest request;

  @BeforeEach
  void setup() {
    wireMockResource.setup();
    request = mock(ExportServiceRequest.class);
    when(request.getExportRequestUUID()).thenReturn(EXPORT_ID);
    when(request.getApplication()).thenReturn(APPLICATION);
    ResourceRequestClass resourceRequestClass = mock(ResourceRequestClass.class);
    when(resourceRequestClass.getUUID()).thenReturn(RESOURCE_ID);
    when(request.getRequest()).thenReturn(resourceRequestClass);
  }

  /**
   * Test to reproduce the IllegalAccessError reported by <a
   * href="https://issues.redhat.com/browse/SWATCH-4569">SWATCH-4569</a>.
   *
   * <p>Note that this is not happening in Quarkus because it uses an old version of Resteasy but it
   * might happen in the future.
   */
  @Test
  void testUploadWithLargeFileShouldNotThrowIllegalAccessError() throws Exception {
    File file = File.createTempFile("export-large-test", ".json");
    file.deleteOnExit();
    // 2MB exceeds the default 1MB EntityOutputStream memory threshold, forcing the REST client
    // to use the file-backed entity code path.
    byte[] data = new byte[2 * 1024 * 1024];
    java.util.Arrays.fill(data, (byte) '{');
    Files.write(file.toPath(), data);

    assertDoesNotThrow(() -> exportDelegate.upload(file, request));

    wireMockResource.verifyUploadWasSent(EXPORT_ID, APPLICATION, RESOURCE_ID);
  }
}
