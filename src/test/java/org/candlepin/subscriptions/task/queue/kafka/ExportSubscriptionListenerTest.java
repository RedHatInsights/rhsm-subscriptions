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
package org.candlepin.subscriptions.task.queue.kafka;

import static org.candlepin.subscriptions.subscription.export.ExportSubscriptionConfiguration.SUBSCRIPTION_EXPORT_QUALIFIER;
import static org.candlepin.subscriptions.subscription.export.ExportSubscriptionListener.ADMIN_ROLE;
import static org.candlepin.subscriptions.subscription.export.ExportSubscriptionListener.SWATCH_APP;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.event.apps.exportservice.v1.Format;
import com.redhat.cloud.event.apps.exportservice.v1.ResourceRequest;
import com.redhat.cloud.event.apps.exportservice.v1.ResourceRequestClass;
import com.redhat.cloud.event.parser.ConsoleCloudEventParser;
import com.redhat.cloud.event.parser.GenericConsoleCloudEvent;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.db.OfferingRepository;
import org.candlepin.subscriptions.db.SubscriptionRepository;
import org.candlepin.subscriptions.db.model.Offering;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.SubscriptionMeasurementKey;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.json.SubscriptionSummary;
import org.candlepin.subscriptions.json.SubscriptionsExport;
import org.candlepin.subscriptions.rbac.RbacApiException;
import org.candlepin.subscriptions.rbac.RbacService;
import org.candlepin.subscriptions.subscription.export.ExportSubscriptionListener;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.test.ExtendWithEmbeddedKafka;
import org.candlepin.subscriptions.test.ExtendWithExportServiceWireMock;
import org.candlepin.subscriptions.test.ExtendWithSwatchDatabase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles(value = {"worker", "kafka-queue", "api", "test-inventory", "capacity-ingress"})
class ExportSubscriptionListenerTest
    implements ExtendWithEmbeddedKafka, ExtendWithSwatchDatabase, ExtendWithExportServiceWireMock {

  private static final String ORG_ID = "13259775";

  @Autowired
  @Qualifier(SUBSCRIPTION_EXPORT_QUALIFIER)
  TaskQueueProperties taskQueueProperties;

  @Autowired ConsoleCloudEventParser parser;
  @Autowired ObjectMapper objectMapper;
  @Autowired ExportSubscriptionListener listener;
  @Autowired KafkaProperties kafkaProperties;
  @Autowired OfferingRepository offeringRepository;
  @Autowired SubscriptionRepository subscriptionRepository;
  @Autowired ApplicationClock clock;

  @MockBean RbacService rbacService;

  KafkaTemplate<String, String> kafkaTemplate;
  Offering offering;
  List<Subscription> subscriptionsToBeExported = new ArrayList<>();
  GenericConsoleCloudEvent<ResourceRequest> request;

  @Transactional
  @BeforeEach
  public void setup() {
    Map<String, Object> properties = kafkaProperties.buildProducerProperties(null);
    properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

    var factory = new DefaultKafkaProducerFactory<String, String>(properties);
    kafkaTemplate = new KafkaTemplate<>(factory);

    offering = new Offering();
    offering.setSku("MKU001");
    offering.setUsage(Usage.PRODUCTION);
    offeringRepository.save(offering);
  }

  @Test
  void testRequestWithoutPermissions() {
    givenExportRequestWithoutPermissions();
    whenReceiveExportRequest();
    verifyRequestWasSentToExportServiceWithError(request);
  }

  @Test
  void testRequestWithPermissions() {
    givenExportRequestWithPermissions(Format.JSON);
    whenReceiveExportRequest();
    verifyRequestWasSentToExportService();
  }

  @Test
  void testRequestShouldBeUploadedWithSubscriptionsAsJson() {
    givenSubscriptionWithMeasurement();
    givenExportRequestWithPermissions(Format.JSON);
    whenReceiveExportRequest();
    verifyRequestWasSentToExportService();
  }

  @Test
  void testRequestShouldBeUploadedWithSubscriptionsAsCsv() {
    givenSubscriptionWithMeasurement();
    givenExportRequestWithPermissions(Format.CSV);
    whenReceiveExportRequest();
    // Since this is not implemented yet, we send an error:
    verifyRequestWasSentToExportServiceWithError(request);
  }

  @Transactional
  void givenSubscriptionWithMeasurement() {
    Subscription subscription = new Subscription();
    subscription.setSubscriptionId(UUID.randomUUID().toString());
    subscription.setStartDate(clock.now());
    subscription.setOffering(offering);
    subscription.setOrgId(ORG_ID);
    subscription.setSubscriptionMeasurements(
        Map.of(
            new SubscriptionMeasurementKey(MetricIdUtils.getCores().toString(), "PHYSICAL"), 5.0));
    subscriptionRepository.save(subscription);
    subscriptionsToBeExported.add(subscription);
  }

  private void givenExportRequestWithoutPermissions() {
    givenExportRequest(Format.JSON);
    givenRbacPermissions(List.of());
  }

  private void givenExportRequestWithPermissions(Format format) {
    givenExportRequest(format);
    givenRbacPermissions(List.of(SWATCH_APP + ADMIN_ROLE));
  }

  private void givenExportRequest(Format format) {
    request = new GenericConsoleCloudEvent<>();
    request.setId(UUID.randomUUID());
    request.setSource("urn:redhat:source:console:app:export-service");
    request.setSpecVersion("1.0");
    request.setType("com.redhat.console.export-service.request");
    request.setDataSchema(
        "https://console.redhat.com/api/schemas/apps/export-service/v1/resource-request.json");
    request.setTime(LocalDateTime.now());
    request.setOrgId(ORG_ID);

    var resourceRequest = new ResourceRequest();
    resourceRequest.setResourceRequest(new ResourceRequestClass());
    resourceRequest.getResourceRequest().setExportRequestUUID(UUID.randomUUID());
    resourceRequest.getResourceRequest().setUUID(UUID.randomUUID());
    resourceRequest.getResourceRequest().setApplication("subscriptions");
    resourceRequest.getResourceRequest().setFormat(format);
    resourceRequest.getResourceRequest().setResource("subscriptions");
    resourceRequest.getResourceRequest().setXRhIdentity("MTMyNTk3NzU=");
    resourceRequest.getResourceRequest().setFilters(new HashMap<>());

    request.setData(resourceRequest);
  }

  private void givenRbacPermissions(List<String> SWATCH_APP) {
    try {
      when(rbacService.getPermissions(
              request.getData().getResourceRequest().getApplication(),
              request.getData().getResourceRequest().getXRhIdentity()))
          .thenReturn(SWATCH_APP);
    } catch (RbacApiException e) {
      Assertions.fail("Failed to call the get permissions method", e);
    }
  }

  private void whenReceiveExportRequest() {
    kafkaTemplate.send(taskQueueProperties.getTopic(), parser.toJson(request));
  }

  private void verifyRequestWasSentToExportService() {
    var expected = new SubscriptionsExport();
    expected.setSubscriptions(new ArrayList<>());
    for (Subscription subscription : subscriptionsToBeExported) {
      var item = new org.candlepin.subscriptions.json.Subscription();
      item.setOrgId(subscription.getOrgId());
      item.setSubscriptionSummaries(new ArrayList<>());

      // map offering
      var offering = subscription.getOffering();
      item.setSku(offering.getSku());
      Optional.ofNullable(offering.getUsage()).map(Usage::getValue).ifPresent(item::setUsage);
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
        item.getSubscriptionSummaries().add(summary);
      }
      expected.getSubscriptions().add(item);
    }

    try {
      verifyRequestWasSentToExportServiceWithUploadData(
          request, objectMapper.writeValueAsString(expected));
    } catch (JsonProcessingException e) {
      Assertions.fail("Failed to serialize the export data", e);
    }
  }
}
