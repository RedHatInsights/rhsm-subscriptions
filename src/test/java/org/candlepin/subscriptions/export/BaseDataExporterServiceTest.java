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
import static org.candlepin.subscriptions.export.ExportSubscriptionListener.ADMIN_ROLE;
import static org.candlepin.subscriptions.export.ExportSubscriptionListener.SWATCH_APP;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.redhat.cloud.event.apps.exportservice.v1.Format;
import com.redhat.cloud.event.apps.exportservice.v1.ResourceRequest;
import com.redhat.cloud.event.apps.exportservice.v1.ResourceRequestClass;
import com.redhat.cloud.event.parser.ConsoleCloudEventParser;
import com.redhat.cloud.event.parser.GenericConsoleCloudEvent;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.candlepin.subscriptions.db.AccountServiceInventoryRepository;
import org.candlepin.subscriptions.db.OfferingRepository;
import org.candlepin.subscriptions.db.model.AccountServiceInventory;
import org.candlepin.subscriptions.db.model.AccountServiceInventoryId;
import org.candlepin.subscriptions.db.model.Offering;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.json.SubscriptionsExportJson;
import org.candlepin.subscriptions.rbac.RbacApiException;
import org.candlepin.subscriptions.rbac.RbacService;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.test.ExtendWithEmbeddedKafka;
import org.candlepin.subscriptions.test.ExtendWithExportServiceWireMock;
import org.candlepin.subscriptions.test.ExtendWithSwatchDatabase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

@SpringBootTest
public abstract class BaseDataExporterServiceTest
    implements ExtendWithExportServiceWireMock, ExtendWithEmbeddedKafka, ExtendWithSwatchDatabase {

  protected static final String RHEL_FOR_X86 = "RHEL for x86";
  protected static final String ROSA = "rosa";
  protected static final String ORG_ID = "13259775";
  protected static final String INSTANCE_TYPE = "HBI_HOST";

  @Autowired
  @Qualifier(SUBSCRIPTION_EXPORT_QUALIFIER)
  TaskQueueProperties taskQueueProperties;

  @Autowired ConsoleCloudEventParser parser;
  @Autowired ObjectMapper objectMapper;
  @Autowired CsvMapper csvMapper;
  @Autowired ExportSubscriptionListener listener;
  @Autowired KafkaProperties kafkaProperties;
  @Autowired OfferingRepository offeringRepository;
  @Autowired AccountServiceInventoryRepository accountServiceInventoryRepository;
  @Autowired DataExporterService<?> dataExporterService;
  @MockBean RbacService rbacService;

  protected KafkaTemplate<String, String> kafkaTemplate;
  protected Offering offering;
  protected AccountServiceInventory accountServiceInventory;
  protected GenericConsoleCloudEvent<ResourceRequest> request;

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
    offering.setServiceLevel(ServiceLevel.PREMIUM);
    offeringRepository.save(offering);

    accountServiceInventory = givenHostInAccountServices(ORG_ID);
  }

  protected abstract String resourceType();

  protected abstract void verifyRequestWasSentToExportService();

  protected void givenExportRequestWithoutPermissions() {
    givenExportRequest(Format.JSON);
    givenRbacPermissions(List.of());
  }

  protected void givenExportRequestWithPermissions(Format format) {
    givenExportRequest(format);
    givenRbacPermissions(List.of(SWATCH_APP + ADMIN_ROLE));
  }

  protected void givenExportRequest(Format format) {
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
    resourceRequest.getResourceRequest().setResource(resourceType());
    resourceRequest.getResourceRequest().setXRhIdentity("MTMyNTk3NzU=");
    resourceRequest.getResourceRequest().setFilters(new HashMap<>());

    request.setData(resourceRequest);
  }

  protected void givenFilterInExportRequest(String filter, String value) {
    request.getData().getResourceRequest().getFilters().put(filter, value);
  }

  protected void givenRbacPermissions(List<String> SWATCH_APP) {
    try {
      when(rbacService.getPermissions(
              request.getData().getResourceRequest().getApplication(),
              request.getData().getResourceRequest().getXRhIdentity()))
          .thenReturn(SWATCH_APP);
    } catch (RbacApiException e) {
      Assertions.fail("Failed to call the get permissions method", e);
    }
  }

  protected AccountServiceInventory givenHostInAccountServices(String orgId) {
    AccountServiceInventory inventory = new AccountServiceInventory();
    inventory.setId(new AccountServiceInventoryId());
    inventory.getId().setServiceType(INSTANCE_TYPE);
    inventory.getId().setOrgId(orgId);
    accountServiceInventoryRepository.save(inventory);
    return inventory;
  }

  protected void whenReceiveExportRequest() {
    kafkaTemplate.send(taskQueueProperties.getTopic(), parser.toJson(request));
  }

  protected void verifyNoRequestsWereSentToExportServiceWithError() {
    verifyNoRequestsWereSentToExportServiceWithError(request);
  }

  protected void verifyNoRequestsWereSentToExportServiceWithUploadData() {
    verifyNoRequestsWereSentToExportServiceWithUploadData(request);
  }

  protected void verifyRequestWasSentToExportServiceWithNoDataFound() {
    verifyRequestWasSentToExportServiceWithUploadData(
        request, toJson(new SubscriptionsExportJson().withData(new ArrayList<>())));
  }

  protected void updateOffering() {
    offeringRepository.save(offering);
  }

  protected String toJson(Object data) {
    try {
      return objectMapper.writeValueAsString(data);
    } catch (JsonProcessingException e) {
      Assertions.fail("Failed to serialize the export data", e);
      return null;
    }
  }

  protected String toCsv(List<Object> data, Class<?> dataItemClass) {
    try {
      var csvSchema = csvMapper.schemaFor(dataItemClass).withUseHeader(true);
      var writer = csvMapper.writer(csvSchema);
      return writer.writeValueAsString(data);
    } catch (JsonProcessingException e) {
      Assertions.fail("Failed to serialize the export data", e);
      return null;
    }
  }
}
