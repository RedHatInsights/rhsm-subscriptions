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

import static com.redhat.swatch.export.ExportRequestHandler.INTERNAL_ERROR;

import com.redhat.swatch.clients.export.api.model.DownloadExportErrorRequest;
import com.redhat.swatch.clients.export.api.resources.ApiException;
import com.redhat.swatch.clients.export.api.resources.ExportApi;
import com.redhat.swatch.export.ExportServiceException;
import com.redhat.swatch.export.ExportServiceRequest;
import com.redhat.swatch.export.api.ExportDelegate;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.File;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@Slf4j
@ApplicationScoped
public class ExportDelegateImpl implements ExportDelegate {
  @RestClient ExportApi exportApi;

  @Override
  public void sendExportError(ExportServiceRequest request, Integer error, String message)
      throws ExportServiceException {
    try {
      exportApi.downloadExportError(
          request.getExportRequestUUID(),
          request.getApplication(),
          request.getRequest().getUUID(),
          new DownloadExportErrorRequest().error(error).message(message));
    } catch (ApiException e) {
      log.error(
          "Error marking the export as error for request {}", request.getExportRequestUUID(), e);
      throw new ExportServiceException(
          INTERNAL_ERROR, "Error marking the export as error with message: " + e.getMessage(), e);
    }
  }

  @Override
  public void upload(File file, ExportServiceRequest request) throws ExportServiceException {
    try {
      exportApi.downloadExportUpload(
          request.getExportRequestUUID(),
          request.getApplication(),
          request.getRequest().getUUID(),
          file);
    } catch (ApiException e) {
      log.error("Error sending the upload for request {}", request.getExportRequestUUID(), e);
      throw new ExportServiceException(
          INTERNAL_ERROR, "Error sending upload request with message: " + e.getMessage(), e);
    }
  }
}
