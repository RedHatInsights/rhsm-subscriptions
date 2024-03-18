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

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.event.apps.exportservice.v1.Format;
import com.redhat.cloud.event.apps.exportservice.v1.ResourceRequest;
import com.redhat.cloud.event.apps.exportservice.v1.ResourceRequestClass;
import com.redhat.cloud.event.parser.ConsoleCloudEvent;
import com.redhat.cloud.event.parser.ConsoleCloudEventParser;
import com.redhat.swatch.clients.export.api.client.ApiException;
import com.redhat.swatch.clients.export.api.model.DownloadExportErrorRequest;
import com.redhat.swatch.clients.export.api.resources.ExportApi;
import jakarta.ws.rs.core.Response.Status;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.exception.ExportServiceException;
import org.candlepin.subscriptions.rbac.RbacApiException;
import org.candlepin.subscriptions.rbac.RbacService;
import org.candlepin.subscriptions.subscription.api.model.SubscriptionProduct;
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
  private final ObjectMapper objectMapper;

  protected ExportSubscriptionListener(
      @Qualifier("subscriptionExport") TaskQueueProperties taskQueueProperties,
      KafkaConsumerRegistry kafkaConsumerRegistry,
      ConsoleCloudEventParser parser,
      ExportApi exportApi,
      RbacService rbacService,
      ObjectMapper objectMapper) {
    super(taskQueueProperties, kafkaConsumerRegistry);
    this.parser = parser;
    this.exportApi = exportApi;
    this.rbacService = rbacService;
    this.objectMapper = objectMapper;
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
    Stream<Subscription> data = fetchData(cloudEvent);

    // Here we need to determine format (csv or json)
    if (Objects.equals(exportData.getFormat(), Format.CSV)) {
      uploadCsv(data, exportData);
    } else if (Objects.equals(exportData.getFormat(), Format.JSON)) {
      uploadJson(data, exportData);
    } else {
      throw new ExportServiceException(Status.FORBIDDEN.getStatusCode(), "Format isn't supported");
    }
  }

  private void uploadCsv(Stream<Subscription> data, ResourceRequestClass request) {
    log.debug("Uploading CSV for request {}", request.getExportRequestUUID());
    throw new ExportServiceException(
        Status.NOT_IMPLEMENTED.getStatusCode(), "Export upload csv isn't implemented ");
  }

  public void uploadJson(Stream<Subscription> data, ResourceRequestClass request) {
    log.debug("Uploading Json for request {}", request.getExportRequestUUID());
    File file = createTemporalFile();
    try (FileOutputStream stream = new FileOutputStream(file);
        JsonGenerator jGenerator = objectMapper.createGenerator(stream, JsonEncoding.UTF8)) {
      jGenerator.writeStartObject();
      jGenerator.writeStringField("name", "Example export payload");
      jGenerator.writeStringField("description", "This is an example export payload");
      jGenerator.writeArrayFieldStart("data");
      var serializerProvider = objectMapper.getSerializerProviderInstance();
      var serializer =
          serializerProvider.findTypedValueSerializer(
              org.candlepin.subscriptions.subscription.api.model.Subscription.class, false, null);
      data.forEach(
          item -> {
            var model = new org.candlepin.subscriptions.subscription.api.model.Subscription();
            model.setQuantity((int) item.getQuantity());
            model.setId(Integer.valueOf(item.getSubscriptionId()));
            model.setSubscriptionNumber(item.getSubscriptionNumber());
            model.setEffectiveStartDate(toEpochMillis(item.getStartDate()));
            model.setEffectiveEndDate(toEpochMillis(item.getEndDate()));
            model.setWebCustomerId(Integer.parseInt(item.getOrgId()));
            model.setSubscriptionProducts(
                List.of(new SubscriptionProduct().sku(item.getOffering().getSku())));
            try {
              serializer.serialize(model, jGenerator, serializerProvider);
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          });
      jGenerator.writeEndArray();
      jGenerator.writeEndObject();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    try {
      exportApi.downloadExportUpload(
          request.getUUID(), request.getApplication(), request.getExportRequestUUID(), file);
    } catch (ApiException e) {
      throw new RuntimeException(e);
    }
  }

  private File createTemporalFile() {
    try {
      return File.createTempFile("export", "json");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
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

  private Stream<Subscription> fetchData(ConsoleCloudEvent cloudEvent) {
    // Check subscription table for data for the event
    log.debug("Fetching subscriptionOrg for {}", cloudEvent.getOrgId());
    throw new ExportServiceException(
        Status.NOT_IMPLEMENTED.getStatusCode(),
        "Fetching data from subscription table isn't implemented");
  }

  private static Long toEpochMillis(OffsetDateTime offsetDateTime) {
    if (offsetDateTime == null) {
      return null;
    }
    return offsetDateTime.toEpochSecond() * 1000L;
  }
}
