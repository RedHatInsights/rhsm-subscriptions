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
package org.candlepin.subscriptions.export;

import static org.candlepin.subscriptions.export.ExportSubscriptionConfiguration.SUBSCRIPTION_EXPORT_QUALIFIER;

import com.redhat.cloud.event.parser.ConsoleCloudEventParser;
import com.redhat.swatch.clients.export.api.client.ApiException;
import com.redhat.swatch.clients.export.api.model.DownloadExportErrorRequest;
import com.redhat.swatch.clients.export.api.resources.ExportApi;
import io.micrometer.core.annotation.Timed;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.exception.ExportServiceException;
import org.candlepin.subscriptions.rbac.RbacApiException;
import org.candlepin.subscriptions.rbac.RbacService;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.util.KafkaConsumerRegistry;
import org.candlepin.subscriptions.util.SeekableKafkaConsumer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Listener for Export messages from Kafka */
@Service
@Slf4j
public class ExportSubscriptionListener extends SeekableKafkaConsumer {

  public static final String ADMIN_ROLE = ":*:*";
  public static final String SWATCH_APP = "subscriptions";
  public static final String MISSING_PERMISSIONS = "Insufficient permission";
  private static final String REPORT_READER = ":reports:read";

  private final ExportApi exportApi;
  private final ConsoleCloudEventParser parser;
  private final RbacService rbacService;
  private final JsonExportFileWriter jsonExportFileWriter;
  private final List<DataExporterService<?>> exporterServices;
  private final CsvExportFileWriter csvExportFileWriter;

  protected ExportSubscriptionListener(
      @Qualifier(SUBSCRIPTION_EXPORT_QUALIFIER) TaskQueueProperties taskQueueProperties,
      KafkaConsumerRegistry kafkaConsumerRegistry,
      ConsoleCloudEventParser parser,
      ExportApi exportApi,
      RbacService rbacService,
      JsonExportFileWriter jsonExportFileWriter,
      List<DataExporterService<?>> exporterServices,
      CsvExportFileWriter csvExportFileWriter) {
    super(taskQueueProperties, kafkaConsumerRegistry);
    this.parser = parser;
    this.exportApi = exportApi;
    this.rbacService = rbacService;
    this.jsonExportFileWriter = jsonExportFileWriter;
    this.exporterServices = exporterServices;
    this.csvExportFileWriter = csvExportFileWriter;
  }

  @Timed("rhsm-subscriptions.exports.upload")
  @Transactional(readOnly = true)
  @KafkaListener(
      id = "#{__listener.groupId}",
      topics = "#{__listener.topic}",
      containerFactory = "exportListenerContainerFactory")
  public void receive(String exportEvent) throws ApiException {
    log.debug("New event has been received: {}", exportEvent);
    var request = new ExportServiceRequest(parser.fromJsonString(exportEvent));
    try {
      if (!request.hasRequest()) {
        throw new ExportServiceException(
            Status.INTERNAL_SERVER_ERROR.getStatusCode(),
            "Cloud event doesn't have any Export data: " + request.getId());
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
          "Error thrown for event: '{}' sending ErrorRequest: '{}'",
          request.getId(),
          e.getMessage());
      sendExportError(request, e.getStatus(), e.getMessage());
    }
  }

  private void sendMissingPermissionsError(ExportServiceRequest request) throws ApiException {
    log.warn(
        "Rejecting request because missing permissions for event: '{}' from application: '{}'",
        request.getId(),
        request.getSource());
    sendExportError(request, Status.FORBIDDEN.getStatusCode(), MISSING_PERMISSIONS);
  }

  private void sendExportError(ExportServiceRequest request, Integer error, String message)
      throws ApiException {
    exportApi.downloadExportError(
        request.getExportRequestUUID(),
        request.getApplication(),
        request.getRequest().getUUID(),
        new DownloadExportErrorRequest().error(error).message(message));
  }

  private void uploadData(DataExporterService<?> exporterService, ExportServiceRequest request) {
    Stream<?> data = exporterService.fetchData(request);
    File file = createTemporalFile(request);
    getFileWriter(request).write(file, exporterService.getMapper(request), data, request);
    upload(file, request);
    log.info("Event processed: '{}' from application: '{}'", request.getId(), request.getSource());
  }

  private ExportFileWriter getFileWriter(ExportServiceRequest request) {
    if (request.getFormat() == null) {
      throw new ExportServiceException(Status.FORBIDDEN.getStatusCode(), "Format isn't supported");
    }

    return switch (request.getFormat()) {
      case JSON -> jsonExportFileWriter;
      case CSV -> csvExportFileWriter;
    };
  }

  private void upload(File file, ExportServiceRequest request) {
    try {
      exportApi.downloadExportUpload(
          request.getExportRequestUUID(),
          request.getApplication(),
          request.getRequest().getUUID(),
          file);
    } catch (ApiException e) {
      log.error("Error sending the upload for request {}", request.getExportRequestUUID(), e);
      throw new ExportServiceException(
          Status.INTERNAL_SERVER_ERROR.getStatusCode(),
          "Error sending upload request with message: " + e.getMessage());
    }
  }

  private boolean checkRbac(ExportServiceRequest request) {
    // role base access control, service check for correct permissions
    // two permissions for rbac check for "subscriptions:*:*" or subscriptions:reports:read
    log.debug("Verifying identity: {}", request.getXRhIdentity());
    List<String> access;
    try {
      access = rbacService.getPermissions(request.getApplication(), request.getXRhIdentity());
    } catch (RbacApiException e) {
      throw new ExportServiceException(Status.NOT_FOUND.getStatusCode(), e.getMessage());
    }

    return access.contains(SWATCH_APP + ADMIN_ROLE) || access.contains(SWATCH_APP + REPORT_READER);
  }

  private static File createTemporalFile(ExportServiceRequest request) {
    try {
      return File.createTempFile("export", request.getFormat().toString());
    } catch (IOException e) {
      log.error("Error creating the temporal file for request {}", request.getId(), e);
      throw new ExportServiceException(
          Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
          "Error sending upload request with message: " + e.getMessage());
    }
  }
}
