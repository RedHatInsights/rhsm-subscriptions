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

import static org.candlepin.subscriptions.export.ExportSubscriptionListener.MISSING_PERMISSIONS;
import static org.candlepin.subscriptions.subscription.export.SubscriptionDataExporterService.PRODUCT_ID;

import com.redhat.cloud.event.apps.exportservice.v1.Format;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.db.SubscriptionCapacityViewRepository;
import org.candlepin.subscriptions.db.SubscriptionRepository;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.SubscriptionCapacityView;
import org.candlepin.subscriptions.db.model.SubscriptionMeasurementKey;
import org.candlepin.subscriptions.export.BaseDataExporterServiceTest;
import org.candlepin.subscriptions.json.SubscriptionsExportCsvItem;
import org.candlepin.subscriptions.json.SubscriptionsExportJson;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles(value = {"kafka-queue", "test-inventory", "capacity-ingress"})
class SubscriptionDataExporterServiceTest extends BaseDataExporterServiceTest {

  @Autowired SubscriptionRepository subscriptionRepository;
  @Autowired SubscriptionCapacityViewRepository subscriptionCapacityViewRepository;
  @Autowired ApplicationClock clock;
  @Autowired SubscriptionCsvDataMapperService csvDataMapperService;
  @Autowired SubscriptionJsonDataMapperService jsonDataMapperService;

  @AfterEach
  public void tearDown() {
    subscriptionRepository.deleteAll();
  }

  @Override
  protected String resourceType() {
    return "subscriptions";
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
        "category,hypervisor",
        "sla,premium",
        "metric_id,Cores",
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
        "category,cloud",
        "sla,standard",
        "metric_id,Sockets",
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

  @Override
  protected void verifyRequestWasSentToExportService() {
    boolean isCsvFormat = request.getData().getResourceRequest().getFormat() == Format.CSV;
    List<Object> data = new ArrayList<>();
    for (SubscriptionCapacityView subscription : subscriptionCapacityViewRepository.findAll()) {
      if (isCsvFormat) {
        data.addAll(csvDataMapperService.mapDataItem(subscription, null));
      } else {
        data.addAll(jsonDataMapperService.mapDataItem(subscription, null));
      }
    }

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

  private void givenSubscriptionWithMeasurement(String productId) {
    Subscription subscription = new Subscription();
    subscription.setSubscriptionId(UUID.randomUUID().toString());
    subscription.setSubscriptionNumber(UUID.randomUUID().toString());
    subscription.setStartDate(OffsetDateTime.parse("2024-04-23T11:48:15.888129Z"));
    subscription.setEndDate(OffsetDateTime.parse("2028-05-23T11:48:15.888129Z"));
    subscription.setOffering(offering);
    subscription.setOrgId(ORG_ID);
    subscription.setBillingProvider(BillingProvider.AWS);
    offering.getProductTags().clear();
    offering.getProductTags().add(productId);
    updateOffering();
    subscription.setBillingAccountId("123");
    subscription.setSubscriptionMeasurements(
        Map.of(
            new SubscriptionMeasurementKey(MetricIdUtils.getCores().toString(), "HYPERVISOR"),
            5.0));
    subscriptionRepository.save(subscription);
  }

  private void givenSameSubscriptionWithOtherMeasurement() {
    var subscriptions = subscriptionRepository.findAll();
    if (subscriptions.isEmpty()) {
      throw new RuntimeException(
          "No subscriptions found. Use 'givenSubscriptionWithMeasurement' to add one.");
    }

    var existing = subscriptions.get(0);
    Subscription subscription = new Subscription();
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
    subscriptionRepository.save(subscription);
  }
}
