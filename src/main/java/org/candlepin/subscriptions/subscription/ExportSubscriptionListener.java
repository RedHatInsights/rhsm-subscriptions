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
import java.util.MissingFormatArgumentException;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
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
  private final ExportApi exportApi;
  private ResourceRequestClass resourceRequest;

  protected ExportSubscriptionListener(
      @Qualifier("subscriptionExport") TaskQueueProperties taskQueueProperties,
      KafkaConsumerRegistry kafkaConsumerRegistry,
      ConsoleCloudEventParser parser,
      ExportApi exportApi) {
    super(taskQueueProperties, kafkaConsumerRegistry);
    this.parser = parser;
    this.exportApi = exportApi;
  }

  @KafkaListener(
      id = "#{__listener.groupId}",
      topics = "#{__listener.topic}",
      containerFactory = "exportListenerContainerFactory")
  public void receive(String exportEvent) throws ApiException {
    log.info("New Export event message has been received : {} ", exportEvent);
    ConsoleCloudEvent event = parser.fromJsonString(exportEvent);
    try {
      var data = event.getData(ResourceRequest.class);
      if (data.isPresent()
          && Objects.equals(data.get().getResourceRequest().getApplication(), "subscription")
          && Objects.equals(data.get().getResourceRequest().getResource(), "subscription")) {
        // Global variable to hold data for exportDownload
        this.resourceRequest = data.get().getResourceRequest();
        uploadData(event);
      }
      throw new MissingFormatArgumentException("Cannot process export message");
    } catch (Exception e) {
      log.error(
          "Error getting data from Export service: {}", resourceRequest.getExportRequestUUID());
      var errorMsg = new DownloadExportErrorRequest().message("Unexpected error").error(500);
      exportApi.downloadExportError(
          resourceRequest.getUUID(),
          resourceRequest.getApplication(),
          resourceRequest.getExportRequestUUID(),
          errorMsg);
    }
  }

  private void uploadData(ConsoleCloudEvent cloudEvent) {
    checkRbac(resourceRequest.getXRhIdentity());
    // Here we need to determine format (csv or json, if other throw error)
    if (Objects.equals(resourceRequest.getFormat(), Format.CSV)) {
      fetchData(cloudEvent);
      uploadCsv(resourceRequest);
    } else if (Objects.equals(resourceRequest.getFormat(), Format.JSON)) {
      fetchData(cloudEvent);
      uploadJson(resourceRequest);
    } else {
      throw new MissingFormatArgumentException("Format isn't supported");
    }
  }

  private void uploadCsv(ResourceRequestClass data) {
    log.debug("Uploading CSV for request {}", data.getExportRequestUUID());
    throw new MissingFormatArgumentException("Export upload csv isn't implemented ");
  }

  private void uploadJson(ResourceRequestClass data) {
    log.debug("Uploading Json for request {}", data.getExportRequestUUID());
    throw new MissingFormatArgumentException("Export Upload json isn't implemented");
  }

  private void checkRbac(String role) {
    // role base access control, service check for correct permissions
    log.debug("Verifying identity: {}", role);
    throw new MissingFormatArgumentException("RBAC isn't implemented");
  }

  private void fetchData(ConsoleCloudEvent cloudEvent) {
    // Check subscription table for data for the event
    log.debug("Fetching subscriptionOrg for {}", cloudEvent.getOrgId());
    throw new MissingFormatArgumentException(
        "Fetching data from subscription table isn't implemented");
  }
}
