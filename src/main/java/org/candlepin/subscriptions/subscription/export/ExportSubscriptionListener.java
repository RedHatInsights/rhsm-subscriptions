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
package org.candlepin.subscriptions.subscription.export;

import static org.candlepin.subscriptions.subscription.export.ExportSubscriptionConfiguration.SUBSCRIPTION_EXPORT_QUALIFIER;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.event.apps.exportservice.v1.Format;
import com.redhat.cloud.event.parser.ConsoleCloudEventParser;
import com.redhat.swatch.clients.export.api.client.ApiException;
import com.redhat.swatch.clients.export.api.model.DownloadExportErrorRequest;
import com.redhat.swatch.clients.export.api.resources.ExportApi;
import io.micrometer.core.annotation.Timed;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response.Status;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.db.SubscriptionRepository;
import org.candlepin.subscriptions.db.model.DbReportCriteria;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.exception.ExportServiceException;
import org.candlepin.subscriptions.json.SubscriptionsExportItem;
import org.candlepin.subscriptions.json.SubscriptionsExportMeasurement;
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

  public static final String ADMIN_ROLE = ":*:*";
  public static final String SWATCH_APP = "subscriptions";
  private static final String REPORT_READER = ":reports:read";

  private final ExportApi exportApi;
  private final ConsoleCloudEventParser parser;
  private final RbacService rbacService;
  private final SubscriptionRepository subscriptionRepository;
  private final ObjectMapper objectMapper;
  private final ApplicationClock clock;

  protected ExportSubscriptionListener(
      @Qualifier(SUBSCRIPTION_EXPORT_QUALIFIER) TaskQueueProperties taskQueueProperties,
      SubscriptionRepository subscriptionRepository,
      KafkaConsumerRegistry kafkaConsumerRegistry,
      ConsoleCloudEventParser parser,
      ExportApi exportApi,
      RbacService rbacService,
      ObjectMapper objectMapper,
      ApplicationClock clock) {
    super(taskQueueProperties, kafkaConsumerRegistry);
    this.subscriptionRepository = subscriptionRepository;
    this.parser = parser;
    this.exportApi = exportApi;
    this.rbacService = rbacService;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  @Timed("rhsm-subscriptions.exports.upload")
  @Transactional
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
      if (request.isRequestFor(SWATCH_APP)) {
        log.info(
            "Processing event: '{}' from application: '{}'", request.getId(), request.getSource());
        checkRbac(request);
        uploadData(request);

        log.info(
            "Event processed: '{}' from application: '{}'", request.getId(), request.getSource());
      }
    } catch (ExportServiceException e) {
      log.error(
          "Error thrown for event: '{}' sending ErrorRequest: '{}'",
          request.getId(),
          e.getMessage());
      exportApi.downloadExportError(
          request.getExportRequestUUID(),
          request.getApplication(),
          request.getRequest().getUUID(),
          new DownloadExportErrorRequest().error(e.getStatus()).message(e.getMessage()));
    }
  }

  private void uploadData(ExportServiceRequest request) throws ApiException {
    // Here we need to determine format (csv or json)
    var subscriptions = fetchData(request);
    if (Objects.equals(request.getFormat(), Format.CSV)) {
      uploadCsv(request);
    } else if (Objects.equals(request.getFormat(), Format.JSON)) {
      uploadJson(subscriptions, request);
    } else {
      throw new ExportServiceException(Status.FORBIDDEN.getStatusCode(), "Format isn't supported");
    }
  }

  private void uploadCsv(ExportServiceRequest request) {
    log.debug("Uploading CSV for request {}", request.getExportRequestUUID());
    throw new ExportServiceException(
        Status.NOT_IMPLEMENTED.getStatusCode(), "Export upload csv isn't implemented ");
  }

  private void uploadJson(Stream<Subscription> subscriptions, ExportServiceRequest request) {
    log.debug("Uploading Json for request {}", request.getExportRequestUUID());
    File file = createTemporalFile(request);
    try (subscriptions;
        FileOutputStream stream = new FileOutputStream(file);
        JsonGenerator jGenerator = objectMapper.createGenerator(stream, JsonEncoding.UTF8)) {
      jGenerator.writeStartObject();
      jGenerator.writeArrayFieldStart("data");
      var serializerProvider = objectMapper.getSerializerProviderInstance();
      var serializer =
          serializerProvider.findTypedValueSerializer(SubscriptionsExportItem.class, false, null);
      subscriptions.forEach(
          subscription -> {
            var item = new SubscriptionsExportItem();
            item.setOrgId(subscription.getOrgId());
            item.setMeasurements(new ArrayList<>());

            // map offering
            var offering = subscription.getOffering();
            item.setSku(offering.getSku());
            Optional.ofNullable(offering.getUsage()).map(Usage::getValue).ifPresent(item::setUsage);
            Optional.ofNullable(offering.getServiceLevel())
                .map(ServiceLevel::getValue)
                .ifPresent(item::setServiceLevel);
            item.setProductName(offering.getProductName());
            item.setSubscriptionNumber(subscription.getSubscriptionNumber());
            item.setQuantity(subscription.getQuantity());

            // map measurements
            for (var entry : subscription.getSubscriptionMeasurements().entrySet()) {
              var measurement = new SubscriptionsExportMeasurement();
              measurement.setMeasurementType(entry.getKey().getMeasurementType());
              measurement.setCapacity(entry.getValue());
              measurement.setMetricId(entry.getKey().getMetricId());

              item.getMeasurements().add(measurement);
            }
            try {
              serializer.serialize(item, jGenerator, serializerProvider);
            } catch (IOException e) {
              log.error(
                  "Error serializing the subscription {} for request {}",
                  subscription,
                  request.getExportRequestUUID(),
                  e);
              throw new ExportServiceException(
                  Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Error writing the Json payload");
            }
          });
      jGenerator.writeEndArray();
      jGenerator.writeEndObject();
    } catch (IOException e) {
      log.error("Error writing the Json payload for request {}", request.getExportRequestUUID(), e);
      throw new ExportServiceException(
          Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Error writing the Json payload");
    }

    try {
      exportApi.downloadExportUpload(
          request.getExportRequestUUID(),
          request.getApplication(),
          request.getRequest().getUUID(),
          file);
    } catch (ApiException e) {
      log.error("Error sending the Json upload for request {}", request.getExportRequestUUID(), e);
      throw new ExportServiceException(
          Status.INTERNAL_SERVER_ERROR.getStatusCode(),
          "Error sending upload request with message: " + e.getMessage());
    }
  }

  private void checkRbac(ExportServiceRequest request) {
    // role base access control, service check for correct permissions
    // two permissions for rbac check for "subscriptions:*:*" or subscriptions:reports:read
    log.debug("Verifying identity: {}", request.getXRhIdentity());
    List<String> access;
    try {
      access = rbacService.getPermissions(request.getApplication(), request.getXRhIdentity());
    } catch (RbacApiException e) {
      throw new ExportServiceException(Status.NOT_FOUND.getStatusCode(), e.getMessage());
    }

    if (access.contains(SWATCH_APP + ADMIN_ROLE) || access.contains(SWATCH_APP + REPORT_READER)) {
      // allow the app to fetch and upload data once permissions are verified
      return;
    }
    throw new ExportServiceException(Status.FORBIDDEN.getStatusCode(), "Insufficient permission");
  }

  private Stream<Subscription> fetchData(ExportServiceRequest request) throws ApiException {
    // Check subscription table for data for the event
    log.debug("Fetching subscriptionOrg for {}", request.getOrgId());
    var reportCriteria = extractExportFilter(request);
    return subscriptionRepository.streamBy(reportCriteria);
  }

  private DbReportCriteria extractExportFilter(ExportServiceRequest request) {
    var report =
        DbReportCriteria.builder()
            .orgId(request.getOrgId())
            .beginning(clock.now())
            .ending(clock.now());
    if (request.getFilters() != null) {
      var filters = request.getFilters().entrySet();
      try {
        for (var entry : filters) {
          var filterHandler =
              ExportSubscriptionRequestFilters.get().get(entry.getKey().toLowerCase(Locale.ROOT));
          if (filterHandler == null) {
            log.warn("Filter '{}' isn't currently supported. Ignoring.", entry.getKey());
          } else if (entry.getValue() != null) {
            filterHandler.accept(report, entry.getValue().toString());
          }
        }

      } catch (IllegalArgumentException ex) {
        throw new ExportServiceException(
            Status.BAD_REQUEST.getStatusCode(),
            "Wrong filter in export request: " + ex.getMessage());
      }
    }

    return report.build();
  }

  private static File createTemporalFile(ExportServiceRequest request) {
    try {
      return File.createTempFile("export", "json");
    } catch (IOException e) {
      log.error("Error creating the temporal file for request {}", request.getId(), e);
      throw new ExportServiceException(
          Status.INTERNAL_SERVER_ERROR.getStatusCode(),
          "Error sending upload request with message: " + e.getMessage());
    }
  }
}
