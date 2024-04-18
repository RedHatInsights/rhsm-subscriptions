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

import com.redhat.cloud.event.apps.exportservice.v1.Format;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.db.SubscriptionRepository;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.SubscriptionMeasurementKey;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.export.BaseDataExporterServiceTest;
import org.candlepin.subscriptions.json.SubscriptionsExport;
import org.candlepin.subscriptions.json.SubscriptionsExportItem;
import org.candlepin.subscriptions.json.SubscriptionsExportMeasurement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles(value = {"kafka-queue", "test", "capacity-ingress"})
class SubscriptionDataExporterServiceTest extends BaseDataExporterServiceTest {

  @Autowired SubscriptionRepository subscriptionRepository;
  @Autowired ApplicationClock clock;

  protected List<Subscription> itemsToBeExported = new ArrayList<>();

  @Override
  protected String resourceType() {
    return "subscriptions";
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

  @ParameterizedTest
  @CsvSource(
      value = {
        "product_id,RHEL for x86",
        "usage,production",
        "category,hypervisor",
        "sla,premium",
        "metric_id,Cores",
        "billing_provider,aws",
        "billing_account_id,123"
      })
  void testFiltersFoundData(String filterName, String exists) {
    givenSubscriptionWithMeasurement();
    givenExportRequestWithPermissions(Format.JSON);
    givenFilterInExportRequest(filterName, exists);
    whenReceiveExportRequest();
    verifyRequestWasSentToExportService();
    verifyNoRequestsWereSentToExportServiceWithError();
  }

  @ParameterizedTest
  @CsvSource(
      value = {
        "product_id,rosa",
        "usage,disaster recovery",
        "category,cloud",
        "sla,standard",
        "metric_id,Sockets",
        "billing_provider,azure",
        "billing_account_id,345"
      })
  void testFiltersDoesNotFoundDataAndReportIsEmpty(String filterName, String doesNotExist) {
    givenSubscriptionWithMeasurement();
    givenExportRequestWithPermissions(Format.JSON);
    givenFilterInExportRequest(filterName, doesNotExist);
    whenReceiveExportRequest();
    verifyRequestWasSentToExportServiceWithNoDataFound();
    verifyNoRequestsWereSentToExportServiceWithError();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"product_id", "usage", "category", "sla", "metric_id", "billing_provider"})
  void testFiltersAreInvalid(String filterName) {
    givenSubscriptionWithMeasurement();
    givenExportRequestWithPermissions(Format.JSON);
    givenFilterInExportRequest(filterName, "wrong!");
    whenReceiveExportRequest();
    verifyNoRequestsWereSentToExportServiceWithUploadData();
    verifyRequestWasSentToExportServiceWithError(request);
  }

  @Transactional
  void givenSubscriptionWithMeasurement() {
    Subscription subscription = new Subscription();
    subscription.setSubscriptionId(UUID.randomUUID().toString());
    subscription.setStartDate(clock.now());
    subscription.setOffering(offering);
    subscription.setOrgId(ORG_ID);
    subscription.setBillingProvider(BillingProvider.AWS);
    subscription.setSubscriptionProductIds(Set.of("RHEL for x86"));
    subscription.setBillingAccountId("123");
    subscription.setSubscriptionMeasurements(
        Map.of(
            new SubscriptionMeasurementKey(MetricIdUtils.getCores().toString(), "HYPERVISOR"),
            5.0));
    subscriptionRepository.save(subscription);
    itemsToBeExported.add(subscription);
  }

  @Override
  protected void verifyRequestWasSentToExportService() {
    var expected = new SubscriptionsExport();
    expected.setData(new ArrayList<>());
    for (Subscription subscription : itemsToBeExported) {
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
      expected.getData().add(item);
    }

    verifyRequestWasSentToExportServiceWithUploadData(expected);
  }
}
