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
package com.redhat.swatch.contract.service.export;

import static com.redhat.swatch.contract.service.export.SubscriptionDataExporterService.PRODUCT_ID;
import static com.redhat.swatch.export.ExportRequestHandler.ADMIN_ROLE;
import static com.redhat.swatch.export.ExportRequestHandler.MISSING_PERMISSIONS;
import static com.redhat.swatch.export.ExportRequestHandler.SWATCH_APP;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.redhat.cloud.event.apps.exportservice.v1.Format;
import com.redhat.cloud.event.apps.exportservice.v1.ResourceRequest;
import com.redhat.cloud.event.apps.exportservice.v1.ResourceRequestClass;
import com.redhat.cloud.event.parser.ConsoleCloudEventParser;
import com.redhat.cloud.event.parser.GenericConsoleCloudEvent;
import com.redhat.swatch.clients.rbac.RbacApiException;
import com.redhat.swatch.clients.rbac.RbacService;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import com.redhat.swatch.contract.config.Channels;
import com.redhat.swatch.contract.model.SubscriptionsExportCsvItem;
import com.redhat.swatch.contract.model.SubscriptionsExportJson;
import com.redhat.swatch.contract.repository.BillingProvider;
import com.redhat.swatch.contract.repository.OfferingEntity;
import com.redhat.swatch.contract.repository.OfferingRepository;
import com.redhat.swatch.common.model.ServiceLevel;
import com.redhat.swatch.contract.repository.SubscriptionCapacityViewRepository;
import com.redhat.swatch.contract.repository.SubscriptionEntity;
import com.redhat.swatch.contract.repository.SubscriptionMeasurementKey;
import com.redhat.swatch.contract.repository.SubscriptionRepository;
import com.redhat.swatch.common.model.Usage;
import com.redhat.swatch.contract.test.resources.ExportServiceWireMockResource;
import com.redhat.swatch.contract.test.resources.InMemoryMessageBrokerKafkaResource;
import com.redhat.swatch.contract.test.resources.InjectWireMock;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.jboss.logmanager.LogContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.shaded.org.awaitility.Awaitility;

@QuarkusTest
@QuarkusTestResource(ExportServiceWireMockResource.class)
@QuarkusTestResource(InMemoryMessageBrokerKafkaResource.class)
class SubscriptionDataExporterServiceTest {

  private static final String RHEL_FOR_X86 = "RHEL for x86";
  private static final String ROSA = "rosa";
  private static final String ORG_ID = "13259775";
  private static final LoggerCaptor LOGGER_CAPTOR = new LoggerCaptor();

  @Inject SubscriptionRepository subscriptionRepository;
  @Inject SubscriptionCapacityViewRepository subscriptionCapacityViewRepository;
  @Inject SubscriptionCsvDataMapperService csvDataMapperService;
  @Inject SubscriptionJsonDataMapperService jsonDataMapperService;
  @Inject ObjectMapper objectMapper;
  @Inject CsvMapper csvMapper;
  @Inject OfferingRepository offeringRepository;
  @InjectMock RbacService rbacService;
  @InjectWireMock ExportServiceWireMockResource wireMockResource;
  @Inject @Any InMemoryConnector connector;

  private ConsoleCloudEventParser parser;
  private OfferingEntity offering;
  private GenericConsoleCloudEvent<ResourceRequest> request;
  private InMemorySource<String> exportChannel;

  @BeforeAll
  static void configureLogging() {
    LogContext.getLogContext()
        .getLogger(ExportRequestConsumer.class.getName())
        .addHandler(LOGGER_CAPTOR);
  }

  @Transactional
  @BeforeEach
  public void setup() {
    wireMockResource.setup();
    exportChannel = connector.source(Channels.EXPORT_REQUESTS_TOPIC);
    parser = new ConsoleCloudEventParser(objectMapper);

    offering = new OfferingEntity();
    offering.setSku("MKU001");
    offering.setUsage(Usage.PRODUCTION);
    offering.setServiceLevel(ServiceLevel.PREMIUM);
    offeringRepository.persist(offering);
  }

  @Transactional
  @AfterEach
  public void tearDown() {
    subscriptionRepository.deleteAll();
  }

  @Test
  void testRequestWithoutPermissions() {
    givenExportRequestWithoutPermissions();
    whenReceiveExportRequest();
    verifyRequestWasSentToExportServiceWithError(request, MISSING_PERMISSIONS);
  }

  @Test
  void testRequestWithPermissions() {
    givenExportRequestWithPermissions(Format.JSON);
    whenReceiveExportRequest();
    verifyRequestWasSentToExportService();
  }

  @Test
  void testRequestShouldBeUploadedWithSubscriptionsAsJson() {
    givenSubscriptionWithMeasurement(RHEL_FOR_X86);
    givenExportRequestWithPermissions(Format.JSON);
    whenReceiveExportRequest();
    verifyRequestWasSentToExportService();
  }

  @Disabled(
      value =
          """
          This test needs a real PostgreSQL instance with all the migrations executed: the ones from the
          monolith and from the swatch-contracts service.
          This should be addressed after SWATCH-2820.
          """)
  @Test
  void testRequestShouldBeUploadedWithSubscriptionsUsingPrometheusEnabledProductAsJson() {
    givenSubscriptionWithMeasurement(ROSA);
    givenExportRequestWithPermissions(Format.JSON);
    givenFilterInExportRequest(PRODUCT_ID, ROSA);
    whenReceiveExportRequest();
    verifyRequestWasSentToExportService();
  }

  @Test
  void testRequestShouldBeUploadedWithSubscriptionsAsCsv() {
    givenSubscriptionWithMeasurement(RHEL_FOR_X86);
    givenExportRequestWithPermissions(Format.CSV);
    whenReceiveExportRequest();
    verifyRequestWasSentToExportService();
  }

  @Disabled(
      value =
          """
          This test needs a real PostgreSQL instance with all the migrations executed: the ones from the
          monolith and from the swatch-contracts service.
          This should be addressed after SWATCH-2820.
          """)
  @ParameterizedTest
  @EnumSource(value = Format.class)
  void testGivenDuplicateSubscriptionsThenItReturnsOnlyOneRecordAndCapacityIsSum(Format format) {
    givenSubscriptionWithMeasurement(RHEL_FOR_X86);
    givenSameSubscriptionWithOtherMeasurement();
    givenExportRequestWithPermissions(format);
    whenReceiveExportRequest();
    verifyRequestWasSentToExportService();
  }

  @ParameterizedTest
  @CsvSource(
      value = {
        PRODUCT_ID + "," + RHEL_FOR_X86,
        "usage,production",
        // Uncommented after SWATCH-2820 is done
        // "category,hypervisor",
        "sla,premium",
        // Uncommented after SWATCH-2820 is done
        // "metric_id,Cores",
        "billing_provider,aws",
        "billing_account_id,123"
      })
  void testFiltersFoundData(String filterName, String exists) {
    givenSubscriptionWithMeasurement(RHEL_FOR_X86);
    givenExportRequestWithPermissions(Format.JSON);
    givenFilterInExportRequest(filterName, exists);
    whenReceiveExportRequest();
    verifyRequestWasSentToExportService();
    verifyNoRequestsWereSentToExportServiceWithError();
  }

  @ParameterizedTest
  @CsvSource(
      value = {
        PRODUCT_ID + "," + ROSA,
        "usage,disaster recovery",
        // Uncommented after SWATCH-2820 is done
        // "category,cloud",
        "sla,standard",
        // Uncommented after SWATCH-2820 is done
        // "metric_id,Sockets",
        "billing_provider,azure",
        "billing_account_id,345"
      })
  void testFiltersDoesNotFoundDataAndReportIsEmpty(String filterName, String doesNotExist) {
    givenSubscriptionWithMeasurement(RHEL_FOR_X86);
    givenExportRequestWithPermissions(Format.JSON);
    givenFilterInExportRequest(filterName, doesNotExist);
    whenReceiveExportRequest();
    verifyRequestWasSentToExportServiceWithNoDataFound();
    verifyNoRequestsWereSentToExportServiceWithError();
  }

  @ParameterizedTest
  @ValueSource(strings = {PRODUCT_ID, "usage", "category", "sla", "metric_id", "billing_provider"})
  void testFiltersAreInvalid(String filterName) {
    givenSubscriptionWithMeasurement(RHEL_FOR_X86);
    givenExportRequestWithPermissions(Format.JSON);
    givenFilterInExportRequest(filterName, "wrong!");
    whenReceiveExportRequest();
    verifyNoRequestsWereSentToExportServiceWithUploadData();
    verifyRequestWasSentToExportServiceWithError(request);
  }

  @Test
  void testRequestShouldFilterByOrgId() {
    givenSubscriptionWithMeasurementForAnotherOrgId(RHEL_FOR_X86);
    givenSubscriptionWithMeasurement(RHEL_FOR_X86);
    givenExportRequestWithPermissions(Format.JSON);
    whenReceiveExportRequest();
    verifyRequestWasSentToExportService();
  }

  @Test
  void testErrorWhenServiceReturnsTimeout() {
    givenSubscriptionWithMeasurement(RHEL_FOR_X86);
    givenExportRequestWithPermissions(Format.JSON);
    givenExportServiceReturnsGatewayTimeout();
    whenReceiveExportRequest();
    thenErrorLogWithMessage("Error handling export request");
  }

  private void givenExportServiceReturnsGatewayTimeout() {
    wireMockResource.mockRequestToReturnGatewayTimeout(request);
  }

  private void verifyRequestWasSentToExportService() {
    boolean isCsvFormat = request.getData().getResourceRequest().getFormat() == Format.CSV;
    List<Object> data = new ArrayList<>();
    subscriptionCapacityViewRepository
        .streamBy(SubscriptionCapacityViewRepository.orgIdEquals(ORG_ID))
        .forEach(
            subscription -> {
              if (isCsvFormat) {
                data.addAll(csvDataMapperService.mapDataItem(subscription, null));
              } else {
                data.addAll(jsonDataMapperService.mapDataItem(subscription, null));
              }
            });

    if (isCsvFormat) {
      verifyRequestWasSentToExportServiceWithUploadCsvData(data);
    } else {
      verifyRequestWasSentToExportServiceWithUploadJsonData(
          new SubscriptionsExportJson().withData(data));
    }
  }

  private void verifyRequestWasSentToExportServiceWithUploadCsvData(List<Object> data) {
    verifyRequestWasSentToExportServiceWithUploadData(
        request, toCsv(data, SubscriptionsExportCsvItem.class));
  }

  private void verifyRequestWasSentToExportServiceWithUploadJsonData(SubscriptionsExportJson data) {
    verifyRequestWasSentToExportServiceWithUploadData(request, toJson(data));
  }

  private void givenSubscriptionWithMeasurementForAnotherOrgId(String productId) {
    givenSubscriptionWithMeasurement(UUID.randomUUID().toString(), productId);
  }

  private void givenSubscriptionWithMeasurement(String productId) {
    givenSubscriptionWithMeasurement(ORG_ID, productId);
  }

  @Transactional
  void givenSubscriptionWithMeasurement(String orgId, String productId) {
    SubscriptionEntity subscription = new SubscriptionEntity();
    subscription.setSubscriptionId(UUID.randomUUID().toString());
    subscription.setSubscriptionNumber(UUID.randomUUID().toString());
    subscription.setStartDate(OffsetDateTime.parse("2024-04-23T11:48:15.888129Z"));
    subscription.setEndDate(OffsetDateTime.parse("2028-05-23T11:48:15.888129Z"));
    subscription.setOffering(offering);
    subscription.setOrgId(orgId);
    subscription.setBillingProvider(BillingProvider.AWS);
    offering.getProductTags().clear();
    offering.getProductTags().add(productId);
    updateOffering();
    subscription.setBillingAccountId("123");
    subscription.setSubscriptionMeasurements(
        Map.of(
            new SubscriptionMeasurementKey(MetricIdUtils.getCores().toString(), "HYPERVISOR"),
            5.0));
    subscriptionRepository.persist(subscription);
  }

  @Transactional
  void givenSameSubscriptionWithOtherMeasurement() {
    var subscriptions = subscriptionRepository.listAll();
    if (subscriptions.isEmpty()) {
      throw new RuntimeException(
          "No subscriptions found. Use 'givenSubscriptionWithMeasurement' to add one.");
    }

    var existing = subscriptions.get(0);
    SubscriptionEntity subscription = new SubscriptionEntity();
    subscription.setSubscriptionId(existing.getSubscriptionId());
    subscription.setSubscriptionNumber(existing.getSubscriptionNumber());
    subscription.setStartDate(OffsetDateTime.parse("2024-05-23T11:48:15.888129Z"));
    subscription.setEndDate(OffsetDateTime.parse("2024-06-23T11:48:15.888129Z"));
    subscription.setOffering(offering);
    subscription.setOrgId(ORG_ID);
    subscription.setBillingProvider(BillingProvider.AWS);
    subscription.setBillingAccountId(existing.getBillingAccountId());
    subscription.setSubscriptionMeasurements(
        Map.of(
            new SubscriptionMeasurementKey(MetricIdUtils.getCores().toString(), "HYPERVISOR"),
            10.0));
    subscriptionRepository.persist(subscription);
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

  private void givenFilterInExportRequest(String filter, String value) {
    request.getData().getResourceRequest().getFilters().put(filter, value);
  }

  private void givenRbacPermissions(List<String> permissions) {
    try {
      when(rbacService.getPermissions(
              request.getData().getResourceRequest().getApplication(),
              request.getData().getResourceRequest().getXRhIdentity()))
          .thenReturn(permissions);
    } catch (RbacApiException e) {
      Assertions.fail("Failed to call the get permissions method", e);
    }
  }

  private void whenReceiveExportRequest() {
    exportChannel.send(parser.toJson(request));
  }

  private void verifyNoRequestsWereSentToExportServiceWithError() {
    wireMockResource.verifyNoRequestsWereSentToExportServiceWithError(request);
  }

  private void verifyNoRequestsWereSentToExportServiceWithUploadData() {
    wireMockResource.verifyNoRequestsWereSentToExportServiceWithUploadData(request);
  }

  private void verifyRequestWasSentToExportServiceWithNoDataFound() {
    wireMockResource.verifyRequestWasSentToExportServiceWithUploadData(
        request, toJson(new SubscriptionsExportJson().withData(new ArrayList<>())));
  }

  private void verifyRequestWasSentToExportServiceWithUploadData(
      GenericConsoleCloudEvent<ResourceRequest> request, String expected) {
    wireMockResource.verifyRequestWasSentToExportServiceWithUploadData(request, expected);
  }

  private void verifyRequestWasSentToExportServiceWithError(
      GenericConsoleCloudEvent<ResourceRequest> request) {
    verifyRequestWasSentToExportServiceWithError(request, "");
  }

  private void verifyRequestWasSentToExportServiceWithError(
      GenericConsoleCloudEvent<ResourceRequest> request, String message) {
    wireMockResource.verifyRequestWasSentToExportServiceWithError(request, message);
  }

  private void updateOffering() {
    offeringRepository.persist(offering);
  }

  private String toJson(Object data) {
    try {
      return objectMapper.writeValueAsString(data);
    } catch (JsonProcessingException e) {
      Assertions.fail("Failed to serialize the export data", e);
      return null;
    }
  }

  private String toCsv(List<Object> data, Class<?> dataItemClass) {
    try {
      var csvSchema = csvMapper.schemaFor(dataItemClass).withUseHeader(true);
      var writer = csvMapper.writer(csvSchema);
      return writer.writeValueAsString(data);
    } catch (JsonProcessingException e) {
      Assertions.fail("Failed to serialize the export data", e);
      return null;
    }
  }

  private void thenErrorLogWithMessage(String str) {
    Awaitility.await()
        .untilAsserted(
            () ->
                assertTrue(
                    LOGGER_CAPTOR.records.stream()
                        .anyMatch(
                            r ->
                                r.getLevel().equals(Level.SEVERE)
                                    && r.getMessage().contains(str))));
  }

  public static class LoggerCaptor extends Handler {

    private final List<LogRecord> records = new ArrayList<>();

    @Override
    public void publish(LogRecord trace) {
      records.add(trace);
    }

    @Override
    public void flush() {
      // no need to flush any sink
    }

    @Override
    public void close() throws SecurityException {
      clearRecords();
    }

    public void clearRecords() {
      records.clear();
    }
  }
}
