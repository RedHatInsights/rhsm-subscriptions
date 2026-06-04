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
package com.redhat.swatch.export;

import com.redhat.cloud.event.parser.ConsoleCloudEventParser;
import com.redhat.swatch.export.api.ExportDelegate;
import com.redhat.swatch.export.api.RbacDelegate;
import com.redhat.swatch.export.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
public class ExportRequestHandler {

  public static final String ADMIN_ROLE = ":*:*";
  public static final String SWATCH_APP = "subscriptions";
  public static final String MISSING_PERMISSIONS = "Insufficient permission";
  public static final int INTERNAL_ERROR = HttpURLConnection.HTTP_INTERNAL_ERROR;
  public static final int FORBIDDEN_STATUS = HttpURLConnection.HTTP_FORBIDDEN;
  private static final String REPORT_READER = ":reports:read";

  private final ExportDelegate exportDelegate;
  private final RbacDelegate rbacDelegate;
  private final List<DataExporterService<?>> exporterServices;
  private final ConsoleCloudEventParser parser;
  private final JsonExportFileWriter jsonExportFileWriter;
  private final CsvExportFileWriter csvExportFileWriter;

  public void handle(String exportEvent) throws ExportServiceException {
    log.debug("New event has been received: {}", exportEvent);
    var request = new ExportServiceRequest(parser.fromJsonString(exportEvent));
    try {
      if (!request.hasRequest()) {
        log.warn("Cloud event doesn't have any Export data: {}", request.getId());
        return;
      }

      if (request.isRequestForApplication(SWATCH_APP)) {
        for (var service : exporterServices) {
          if (service.handles(request)) {
            log.info(
                "Processing event: '{}' from application: '{}'",
                request.getId(),
                request.getSource());
            if (checkRbac(request)) {
              uploadData(service, request);
            } else {
              sendMissingPermissionsError(request);
            }

            break;
          }
        }
      }
    } catch (ExportServiceException e) {
      log.error(
          "Export request failed with error '{}', sending to export service for request ID '{}' and resource '{}'",
          e.getMessage(),
          request.getExportRequestUUID(),
          request.getRequest().getUUID());
      exportDelegate.sendExportError(request, e.getStatus(), e.getMessage());
    }
  }

  private void sendMissingPermissionsError(ExportServiceRequest request)
      throws ExportServiceException {
    log.warn(
        "Rejecting request because missing permissions for event: '{}' from application: '{}'",
        request.getId(),
        request.getSource());
    exportDelegate.sendExportError(request, FORBIDDEN_STATUS, MISSING_PERMISSIONS);
  }

  private void uploadData(DataExporterService<?> exporterService, ExportServiceRequest request) {
    Stream<?> data = exporterService.fetchData(request);
    File file = createTemporalFile(request);
    getFileWriter(request).write(file, exporterService.getMapper(request), data, request);
    log.info(
        "Uploading file of size '{}' to export service for request ID '{}' and resource '{}'",
        FileUtils.getFileSize(file),
        request.getExportRequestUUID(),
        request.getRequest().getUUID());
    exportDelegate.upload(file, request);
    log.info("Event processed: '{}' from application: '{}'", request.getId(), request.getSource());
  }

  private ExportFileWriter getFileWriter(ExportServiceRequest request) {
    if (request.getFormat() == null) {
      throw new ExportServiceException(FORBIDDEN_STATUS, "Format isn't supported");
    }

    return switch (request.getFormat()) {
      case JSON -> jsonExportFileWriter;
      case CSV -> csvExportFileWriter;
    };
  }

  private boolean checkRbac(ExportServiceRequest request) {
    // role base access control, service check for correct permissions
    // two permissions for rbac check for "subscriptions:*:*" or subscriptions:reports:read
    log.debug("Verifying identity: {}", request.getXRhIdentity());
    List<String> access =
        rbacDelegate.getPermissions(request.getApplication(), request.getXRhIdentity());
    return access.contains(SWATCH_APP + ADMIN_ROLE) || access.contains(SWATCH_APP + REPORT_READER);
  }

  private static File createTemporalFile(ExportServiceRequest request) {
    try {
      return File.createTempFile("export", request.getFormat().toString());
    } catch (IOException e) {
      log.error("Error creating the temporal file for request {}", request.getId(), e);
      throw new ExportServiceException(
          INTERNAL_ERROR, "Error sending upload request with message: " + e.getMessage());
    }
  }
}
