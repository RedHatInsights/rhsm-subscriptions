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
package org.candlepin.subscriptions.subscription;

import com.redhat.cloud.event.apps.exportservice.v1.Format;
import com.redhat.cloud.event.apps.exportservice.v1.ResourceRequest;
import com.redhat.cloud.event.apps.exportservice.v1.ResourceRequestClass;
import com.redhat.cloud.event.parser.ConsoleCloudEvent;
import com.redhat.cloud.event.parser.ConsoleCloudEventParser;
import com.redhat.swatch.clients.export.api.client.ApiException;
import com.redhat.swatch.clients.export.api.model.DownloadExportErrorRequest;
import com.redhat.swatch.clients.export.api.resources.ExportApi;
import jakarta.ws.rs.core.Response.Status;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.exception.ExportServiceException;
import org.candlepin.subscriptions.rbac.RbacApiException;
import org.candlepin.subscriptions.rbac.RbacService;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.util.KafkaConsumerRegistry;
import org.candlepin.subscriptions.util.SeekableKafkaConsumer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/** Listener for Export messages from Kafka */
@Service
@Slf4j
@Profile("capacity-ingress")
public class ExportSubscriptionListener extends SeekableKafkaConsumer {
  private final ConsoleCloudEventParser parser;

  private static final String ADMIN_ROLE = ":*:*";
  private static final String REPORT_READER = ":reports:read";
  private static final String SWATCH_APP = "subscriptions";
  private final ExportApi exportApi;
  private final RbacService rbacService;

  protected ExportSubscriptionListener(
      @Qualifier("subscriptionExport") TaskQueueProperties taskQueueProperties,
      KafkaConsumerRegistry kafkaConsumerRegistry,
      ConsoleCloudEventParser parser,
      ExportApi exportApi,
      RbacService rbacService) {
    super(taskQueueProperties, kafkaConsumerRegistry);
    this.parser = parser;
    this.exportApi = exportApi;
    this.rbacService = rbacService;
  }

  @KafkaListener(
      id = "#{__listener.groupId}",
      topics = "#{__listener.topic}",
      containerFactory = "exportListenerContainerFactory")
  public void receive(String exportEvent) throws ApiException {
    ConsoleCloudEvent cloudEvent = parser.fromJsonString(exportEvent);
    log.info(
        "New event has been received for : {} from application: {} ",
        cloudEvent.getId(),
        cloudEvent.getSource());
    var exportData = cloudEvent.getData(ResourceRequest.class).stream().findFirst().orElse(null);
    var exportRequest = new ResourceRequestClass();
    try {
      if (Objects.isNull(exportData)) {
        throw new ExportServiceException(
            Status.INTERNAL_SERVER_ERROR.getStatusCode(),
            "Cloud event doesn't have any Export data: " + cloudEvent.getId().toString());
      }
      exportRequest = exportData.getResourceRequest();
      if (Objects.equals(exportRequest.getApplication(), SWATCH_APP)
          && Objects.equals(exportRequest.getResource(), SWATCH_APP)) {
        uploadData(cloudEvent, exportRequest);
      }
    } catch (ExportServiceException e) {
      log.error(
          "Error thrown for exportRequest: {} sending ErrorRequest: {}",
          exportRequest.getExportRequestUUID(),
          e.getMessage());
      var exportErrorMsg = createErrorRequest(e.getStatus(), e.getMessage());
      exportApi.downloadExportError(
          exportRequest.getUUID(),
          exportRequest.getApplication(),
          exportRequest.getExportRequestUUID(),
          exportErrorMsg);
    }
  }

  private DownloadExportErrorRequest createErrorRequest(Integer code, String description) {
    return new DownloadExportErrorRequest().error(code).message(description);
  }

  private void uploadData(ConsoleCloudEvent cloudEvent, ResourceRequestClass exportData) {
    checkRbac(exportData.getApplication(), exportData.getXRhIdentity());
    // Here we need to determine format (csv or json)
    if (Objects.equals(exportData.getFormat(), Format.CSV)) {
      fetchData(cloudEvent);
      uploadCsv(exportData);
    } else if (Objects.equals(exportData.getFormat(), Format.JSON)) {
      fetchData(cloudEvent);
      uploadJson(exportData);
    } else {
      throw new ExportServiceException(Status.FORBIDDEN.getStatusCode(), "Format isn't supported");
    }
  }

  private void uploadCsv(ResourceRequestClass data) {
    log.debug("Uploading CSV for request {}", data.getExportRequestUUID());
    throw new ExportServiceException(
        Status.NOT_IMPLEMENTED.getStatusCode(), "Export upload csv isn't implemented ");
  }

  private void uploadJson(ResourceRequestClass data) {
    log.debug("Uploading Json for request {}", data.getExportRequestUUID());
    throw new ExportServiceException(
        Status.NOT_IMPLEMENTED.getStatusCode(), "Export Upload json isn't implemented");
  }

  private void checkRbac(String appName, String identity) {
    // role base access control, service check for correct permissions
    // two permissions for rbac check for "subscriptions:*:*" or subscriptions:reports:read
    log.debug("Verifying identity: {}", identity);
    List<String> access;
    try {
      access = rbacService.getPermissions(appName, identity);
    } catch (RbacApiException e) {
      throw new ExportServiceException(Status.NOT_FOUND.getStatusCode(), e.getMessage());
    }

    if (access.contains(SWATCH_APP + ADMIN_ROLE) || access.contains(SWATCH_APP + REPORT_READER)) {
      // allow the app to fetch and upload data once permissions are verified
      return;
    }
    throw new ExportServiceException(Status.FORBIDDEN.getStatusCode(), "Insufficient permission");
  }

  private void fetchData(ConsoleCloudEvent cloudEvent) {
    // Check subscription table for data for the event
    log.debug("Fetching subscriptionOrg for {}", cloudEvent.getOrgId());
    throw new ExportServiceException(
        Status.NOT_IMPLEMENTED.getStatusCode(),
        "Fetching data from subscription table isn't implemented");
  }
}
