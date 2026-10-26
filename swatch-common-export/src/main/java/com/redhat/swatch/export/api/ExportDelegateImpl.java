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
package com.redhat.swatch.export.api;

import static com.redhat.swatch.export.ExportRequestHandler.INTERNAL_ERROR;

import com.redhat.swatch.clients.export.SwatchExportApi;
import com.redhat.swatch.clients.export.api.client.ApiException;
import com.redhat.swatch.clients.export.api.model.DownloadExportErrorRequest;
import com.redhat.swatch.export.ExportServiceException;
import com.redhat.swatch.export.ExportServiceRequest;
import java.io.File;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ExportDelegateImpl implements ExportDelegate {

  private final SwatchExportApi exportApi;

  public ExportDelegateImpl(SwatchExportApi exportApi) {
    this.exportApi = Objects.requireNonNull(exportApi, "exportApi");
  }

  @Override
  public void sendExportError(ExportServiceRequest request, Integer error, String message)
      throws ExportServiceException {
    invoke(
        request.getExportRequestUUID(),
        "Error marking the export as error",
        () ->
            exportApi.downloadExportError(
                request.getExportRequestUUID(),
                request.getApplication(),
                request.getRequest().getUUID(),
                new DownloadExportErrorRequest().error(error).message(message)));
  }

  @Override
  public void upload(File file, ExportServiceRequest request) throws ExportServiceException {
    invoke(
        request.getExportRequestUUID(),
        "Error sending upload request",
        () ->
            exportApi.downloadExportUpload(
                request.getExportRequestUUID(),
                request.getApplication(),
                request.getRequest().getUUID(),
                file));
  }

  private void invoke(Object requestId, String failureMessage, ExportApiCall call)
      throws ExportServiceException {
    try {
      call.execute();
    } catch (ApiException | RuntimeException error) {
      throw toExportServiceException(requestId, failureMessage, error);
    }
  }

  private static ExportServiceException toExportServiceException(
      Object requestId, String failureMessage, Throwable error) {
    log.error("{} for request {}", failureMessage, requestId, error);
    Exception cause = error instanceof Exception exception ? exception : new Exception(error);
    return new ExportServiceException(
        INTERNAL_ERROR, failureMessage + " with message: " + error.getMessage(), cause);
  }

  @FunctionalInterface
  private interface ExportApiCall {
    void execute() throws ApiException;
  }
}
