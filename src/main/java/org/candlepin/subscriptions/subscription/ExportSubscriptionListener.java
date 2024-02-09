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

import static org.candlepin.subscriptions.ApplicationConfiguration.SUBSCRIPTION_EXPORT_QUALIFIER;

import com.redhat.cloud.event.apps.exportservice.v1.Format;
import com.redhat.cloud.event.apps.exportservice.v1.ResourceRequest;
import com.redhat.cloud.event.apps.exportservice.v1.ResourceRequestClass;
import com.redhat.cloud.event.parser.ConsoleCloudEvent;
import com.redhat.cloud.event.parser.ConsoleCloudEventParser;
import com.redhat.swatch.clients.export.api.client.ApiException;
import com.redhat.swatch.clients.export.api.model.DownloadExportErrorRequest;
import com.redhat.swatch.clients.export.api.resources.ExportApi;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response.Status;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.db.HypervisorReportCategory;
import org.candlepin.subscriptions.db.SubscriptionRepository;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.DbReportCriteria;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.exception.ExportServiceException;
import org.candlepin.subscriptions.json.SubscriptionSummary;
import org.candlepin.subscriptions.json.SubscriptionsExport;
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
@Transactional
@Profile("capacity-ingress")
public class ExportSubscriptionListener extends SeekableKafkaConsumer {

  public static final String SWATCH_APP = "subscriptions";
  public static final String ADMIN_ROLE = ":*:*";
  private static final String REPORT_READER = ":reports:read";

  private final ConsoleCloudEventParser parser;
  private final SubscriptionRepository subscriptionRepository;

  private final ExportApi exportApi;
  private final RbacService rbacService;

  protected ExportSubscriptionListener(
      @Qualifier(SUBSCRIPTION_EXPORT_QUALIFIER) TaskQueueProperties taskQueueProperties,
      SubscriptionRepository subscriptionRepository,
      KafkaConsumerRegistry kafkaConsumerRegistry,
      ConsoleCloudEventParser parser,
      ExportApi exportApi,
      RbacService rbacService) {
    super(taskQueueProperties, kafkaConsumerRegistry);
    this.parser = parser;
    this.exportApi = exportApi;
    this.rbacService = rbacService;
    this.subscriptionRepository = subscriptionRepository;
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

        checkRbac(exportRequest);
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
    // Here we need to determine format (csv or json)
    var dataRequest = fetchData(cloudEvent.getOrgId(), exportData);
    if (Objects.equals(exportData.getFormat(), Format.CSV)) {
      uploadCsv(exportData, dataRequest);
    } else if (Objects.equals(exportData.getFormat(), Format.JSON)) {
      uploadJson(exportData, dataRequest);
    } else {
      throw new ExportServiceException(Status.FORBIDDEN.getStatusCode(), "Format isn't supported");
    }
  }

  private void uploadCsv(ResourceRequestClass exportData, SubscriptionsExport data) {
    log.debug(
        "Uploading CSV for request {} with orgId: {}",
        exportData.getExportRequestUUID(),
        data.getSubscriptions().get(0).getOrgId());
    throw new ExportServiceException(
        Status.NOT_IMPLEMENTED.getStatusCode(), "Export upload csv isn't implemented ");
  }

  private void uploadJson(ResourceRequestClass exportData, SubscriptionsExport data) {
    log.debug("Uploading Json for request {}", exportData.getExportRequestUUID());
    try {
      exportApi.downloadExportUpload(
          exportData.getUUID(),
          exportData.getApplication(),
          exportData.getExportRequestUUID(),
          data);
    } catch (ApiException e) {
      throw new ExportServiceException(Status.BAD_REQUEST.getStatusCode(), e.getMessage());
    }
  }

  private void checkRbac(ResourceRequestClass exportData) {
    // role base access control, service check for correct permissions
    // two permissions for rbac check for "subscriptions:*:*" or subscriptions:reports:read
    log.debug("Verifying identity: {}", exportData.getXRhIdentity());
    List<String> access;
    try {
      access = rbacService.getPermissions(exportData.getApplication(), exportData.getXRhIdentity());
    } catch (RbacApiException e) {
      throw new ExportServiceException(Status.NOT_FOUND.getStatusCode(), e.getMessage());
    }

    if (access.contains(SWATCH_APP + ADMIN_ROLE) || access.contains(SWATCH_APP + REPORT_READER)) {
      // allow the app to fetch and upload data once permissions are verified
      return;
    }
    throw new ExportServiceException(Status.FORBIDDEN.getStatusCode(), "Insufficient permission");
  }

  private SubscriptionsExport fetchData(String orgId, ResourceRequestClass exportData) {
    // Check subscription table for data for the event
    log.debug("Fetching subscriptions for {}", orgId);

    var reportCriteria = extractExportFilter(exportData, orgId);

    var searchSpec = SubscriptionRepository.buildSearchSpecification(reportCriteria);

    var subscriptions = subscriptionRepository.streamAll(searchSpec);

    // pass streams for json and csv upload method
    if (Objects.nonNull(subscriptions)) {
      return transformSubscriptionForExport(subscriptions);
    } else {
      throw new ExportServiceException(
          Status.NOT_FOUND.getStatusCode(), "Unable to find subscriptions for orgId: " + orgId);
    }
  }

  private DbReportCriteria extractExportFilter(ResourceRequestClass data, String orgId) {
    var report = DbReportCriteria.builder().orgId(orgId);
    if (data.getFilters() != null) {
      var filters = data.getFilters().entrySet();
      for (var entry : filters) {
        switch (entry.getKey().toLowerCase()) {
          case "productid" -> report.productId(entry.getValue().toString());
          case "usage" -> report.usage(Usage.fromString(entry.getValue().toString()));
          case "category" ->
              report.hypervisorReportCategory(
                  HypervisorReportCategory.valueOf(entry.getValue().toString()));
          case "sla" -> report.serviceLevel(ServiceLevel.fromString(entry.getValue().toString()));
          case "uom" -> report.metricId(entry.getValue().toString());
          case "billingprovider" ->
              report.billingProvider(BillingProvider.fromString(entry.getValue().toString()));
          case "billingaccountid" -> report.billingAccountId(entry.getValue().toString());
          default -> log.warn("Filter {} isn't supported currently", entry.getKey());
        }
      }
    }

    return report.build();
  }

  protected SubscriptionsExport transformSubscriptionForExport(Stream<Subscription> subscriptions) {

    var subscriptionExport = new SubscriptionsExport();

    var subscriptionsStream =
        subscriptions
            .map(
                subscription -> {
                  var item = new org.candlepin.subscriptions.json.Subscription();
                  item.setOrgId(subscription.getOrgId());
                  item.setSubscriptionSummaries(new ArrayList<>());

                  // map offering
                  var offering = subscription.getOffering();
                  item.setSku(offering.getSku());
                  Optional.ofNullable(offering.getUsage())
                      .map(Usage::getValue)
                      .ifPresent(item::setUsage);
                  Optional.ofNullable(offering.getServiceLevel())
                      .map(ServiceLevel::getValue)
                      .ifPresent(item::setServiceLevel);
                  item.setProductName(offering.getProductName());

                  // map measurements
                  for (var entry : subscription.getSubscriptionMeasurements().entrySet()) {
                    var summary = new SubscriptionSummary();
                    summary.setMeasurementType(entry.getKey().getMeasurementType());
                    summary.setCapacity(entry.getValue());
                    summary.setMetricId(entry.getKey().getMetricId());
                    summary.setSubscriptionNumber(subscription.getSubscriptionNumber());
                    summary.setQuantity(subscription.getQuantity());
                    summary.setBeginning(subscription.getStartDate());
                    summary.setEnding(subscription.getEndDate());
                    item.getSubscriptionSummaries().add(summary);
                  }

                  return item;
                })
            .toList();

    subscriptionExport.setSubscriptions(subscriptionsStream);

    return subscriptionExport;
  }
}
