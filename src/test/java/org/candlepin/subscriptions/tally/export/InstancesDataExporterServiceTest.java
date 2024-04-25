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
package org.candlepin.subscriptions.tally.export;

import static org.candlepin.subscriptions.resource.InstancesResource.getCategoryByMeasurementType;
import static org.candlepin.subscriptions.resource.InstancesResource.getCloudProviderByMeasurementType;
import static org.candlepin.subscriptions.resource.ResourceUtils.ANY;
import static org.candlepin.subscriptions.tally.export.InstancesDataExporterService.PRODUCT_ID;

import com.redhat.cloud.event.apps.exportservice.v1.Format;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.Variant;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.candlepin.subscriptions.db.HostRepository;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.Host;
import org.candlepin.subscriptions.db.model.HostBucketKey;
import org.candlepin.subscriptions.db.model.HostHardwareType;
import org.candlepin.subscriptions.db.model.HostTallyBucket;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.export.BaseDataExporterServiceTest;
import org.candlepin.subscriptions.json.InstancesExportJson;
import org.candlepin.subscriptions.json.InstancesExportJsonGuest;
import org.candlepin.subscriptions.json.InstancesExportJsonItem;
import org.candlepin.subscriptions.json.InstancesExportJsonMetric;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles({"worker", "kafka-queue", "test"})
class InstancesDataExporterServiceTest extends BaseDataExporterServiceTest {

  private static final String RHEL_FOR_X86 = "RHEL for x86";

  private final List<HostWithGuests> itemsToBeExported = new ArrayList<>();

  @Autowired HostRepository repository;

  @AfterEach
  public void tearDown() {
    repository.deleteAll();
  }

  @Override
  protected String resourceType() {
    return "instances";
  }

  @Test
  void testRequestWithoutPermissions() {
    givenExportRequestWithoutPermissions();
    whenReceiveExportRequest();
    verifyRequestWasSentToExportServiceWithError(request);
  }

  @Test
  void testRequestShouldFailIfItDoesNotFilterByProductId() {
    givenInstanceWithMetrics();
    givenExportRequestWithPermissions(Format.JSON);
    whenReceiveExportRequest();
    verifyRequestWasSentToExportServiceWithError(request);
  }

  @Test
  void testRequestShouldBeUploadedWithInstancesAsJsonFilteringByProductId() {
    givenInstanceWithMetrics();
    givenExportRequestWithPermissions(Format.JSON);
    givenFilterInExportRequest(PRODUCT_ID, RHEL_FOR_X86);
    whenReceiveExportRequest();
    verifyRequestWasSentToExportService();
  }

  @Test
  void testRequestShouldBeUploadedWithInstancesAsJsonFilteringByWrongProductId() {
    givenInstanceWithMetrics();
    givenExportRequestWithPermissions(Format.JSON);
    givenFilterInExportRequest(PRODUCT_ID, "wrong!");
    whenReceiveExportRequest();
    verifyNoRequestsWereSentToExportServiceWithUploadData();
    verifyRequestWasSentToExportServiceWithError(request);
  }

  @Test
  void testRequestShouldBeUploadedWithInstancesAsJsonFilteringByProductIdAndReturnsNoData() {
    givenInstanceWithMetrics();
    givenExportRequestWithPermissions(Format.JSON);
    givenFilterInExportRequest(PRODUCT_ID, "rosa");
    whenReceiveExportRequest();
    verifyRequestWasSentToExportServiceWithNoDataFound();
    verifyNoRequestsWereSentToExportServiceWithError();
  }

  @Test
  void testRequestShouldBeUploadedWithInstancesAsCsv() {
    givenInstanceWithMetrics();
    givenExportRequestWithPermissions(Format.CSV);
    whenReceiveExportRequest();
    // Since this is not implemented yet, we send an error:
    verifyRequestWasSentToExportServiceWithError(request);
  }

  @ParameterizedTest
  @CsvSource(
      value = {
        "usage,_ANY",
        "category,physical",
        "sla,_ANY",
        "metric_id,Sockets",
        "billing_provider,_ANY",
        "billing_account_id,_ANY",
        "display_name_contains,ho"
      })
  void testFiltersFoundData(String filterName, String exists) {
    givenInstanceWithMetrics();
    givenExportRequestWithPermissions(Format.JSON);
    givenFilterInExportRequest(PRODUCT_ID, RHEL_FOR_X86);
    givenFilterInExportRequest(filterName, exists);
    whenReceiveExportRequest();
    verifyRequestWasSentToExportService();
    verifyNoRequestsWereSentToExportServiceWithError();
  }

  @ParameterizedTest
  @CsvSource(
      value = {
        "usage,disaster recovery",
        "category,virtual",
        "sla,standard",
        "metric_id,Instance-hours",
        "billing_provider,azure",
        "billing_account_id,345",
        "display_name_contains,another host"
      })
  void testFiltersDoesNotFoundDataAndReportIsEmpty(String filterName, String doesNotExist) {
    givenInstanceWithMetrics();
    givenExportRequestWithPermissions(Format.JSON);
    givenFilterInExportRequest(PRODUCT_ID, RHEL_FOR_X86);
    givenFilterInExportRequest(filterName, doesNotExist);
    whenReceiveExportRequest();
    verifyRequestWasSentToExportServiceWithNoDataFound();
    verifyNoRequestsWereSentToExportServiceWithError();
  }

  @ParameterizedTest
  @ValueSource(strings = {"usage", "category", "sla", "metric_id", "billing_provider", "beginning"})
  void testFiltersAreInvalid(String filterName) {
    givenInstanceWithMetrics();
    givenExportRequestWithPermissions(Format.JSON);
    givenFilterInExportRequest(PRODUCT_ID, RHEL_FOR_X86);
    givenFilterInExportRequest(filterName, "wrong!");
    whenReceiveExportRequest();
    verifyNoRequestsWereSentToExportServiceWithUploadData();
    verifyRequestWasSentToExportServiceWithError(request);
  }

  @Transactional
  void givenInstanceWithMetrics() {
    Host guest = new Host();
    guest.setOrgId(ORG_ID);
    guest.setDisplayName("this is the guest");
    guest.setLastSeen(OffsetDateTime.parse("2024-04-16T07:12:52.426707Z"));
    guest.setHardwareType(HostHardwareType.PHYSICAL);
    guest.setInstanceType(INSTANCE_TYPE);
    guest.setInstanceId(UUID.randomUUID().toString());
    guest.setHypervisorUuid(UUID.randomUUID().toString());
    repository.save(guest);

    Host instance = new Host();
    instance.setOrgId(ORG_ID);
    instance.setNumOfGuests(1);
    instance.setInstanceId("456");
    instance.setDisplayName("my host");
    instance.setBillingProvider(BillingProvider.AWS);
    instance.setBillingAccountId("123");
    instance.setInstanceType(INSTANCE_TYPE);
    instance.setSubscriptionManagerId(guest.getHypervisorUuid());
    instance.addToMonthlyTotal(OffsetDateTime.now(), MetricIdUtils.getSockets(), 6.0);

    // buckets
    HostTallyBucket bucket = new HostTallyBucket();
    bucket.setKey(new HostBucketKey());
    bucket.getKey().setProductId(RHEL_FOR_X86);
    bucket.getKey().setUsage(Usage._ANY);
    bucket.getKey().setSla(ServiceLevel._ANY);
    bucket.getKey().setAsHypervisor(true);
    bucket.getKey().setBillingProvider(BillingProvider._ANY);
    bucket.getKey().setBillingAccountId(ANY);
    bucket.setMeasurementType(HardwareMeasurementType.PHYSICAL);
    bucket.setCores(5);
    bucket.setSockets(6);
    bucket.setHost(instance);
    instance.addBucket(bucket);

    // metrics
    instance.setMeasurements(Map.of(MetricIdUtils.getSockets().toUpperCaseFormatted(), 6.0));

    // save
    repository.save(instance);
    HostWithGuests item = new HostWithGuests();
    item.host = instance;
    item.guests = List.of(guest);
    itemsToBeExported.add(item);
  }

  @Override
  protected void verifyRequestWasSentToExportService() {
    var expected = new InstancesExportJson();
    expected.setData(new ArrayList<>());
    for (HostWithGuests item : itemsToBeExported) {
      Host host = item.host;
      var instance = new InstancesExportJsonItem();
      instance.setId(host.getId().toString());
      instance.setInstanceId(host.getInstanceId());
      instance.setDisplayName(host.getDisplayName());
      if (host.getBillingProvider() != null) {
        instance.setBillingProvider(host.getBillingProvider().getValue());
      }
      var bucket = host.getBuckets().iterator().next();
      var category = getCategoryByMeasurementType(bucket.getMeasurementType());
      if (category != null) {
        instance.setCategory(category.toString());
      }

      var cloudProvider = getCloudProviderByMeasurementType(bucket.getMeasurementType());
      if (cloudProvider != null) {
        instance.setCloudProvider(cloudProvider.toString());
      }

      instance.setBillingAccountId(host.getBillingAccountId());
      var variant = Variant.findByTag(RHEL_FOR_X86);
      var metrics =
          MetricIdUtils.getMetricIdsFromConfigForVariant(variant.orElse(null)).sorted().toList();
      instance.setMeasurements(new ArrayList<>());
      for (var metricId : metrics) {
        instance
            .getMeasurements()
            .add(
                new InstancesExportJsonMetric()
                    .withMetricId(metricId.toString())
                    .withValue(resolveMetricValue(bucket, metricId)));
      }

      instance.setLastSeen(host.getLastSeen());
      instance.setNumberOfGuests(host.getNumOfGuests());
      instance.setSubscriptionManagerId(host.getSubscriptionManagerId());
      instance.setInventoryId(host.getInventoryId());
      if (item.guests != null) {
        instance.setGuests(
            item.guests.stream()
                .map(
                    g ->
                        new InstancesExportJsonGuest()
                            .withDisplayName(g.getDisplayName())
                            .withHardwareType(g.getHardwareType().toString())
                            .withLastSeen(g.getLastSeen())
                            .withIsHypervisor(g.isHypervisor())
                            .withIsUnmappedGuest(g.isUnmappedGuest()))
                .toList());
      }

      expected.getData().add(instance);
    }

    verifyRequestWasSentToExportServiceWithUploadData(request, toJson(expected));
  }

  private static double resolveMetricValue(HostTallyBucket bucket, MetricId metricId) {
    if (metricId.equals(MetricIdUtils.getSockets())) {
      return bucket.getSockets();
    } else if (metricId.equals(MetricIdUtils.getCores())) {
      return bucket.getCores();
    }

    return 0;
  }

  private static class HostWithGuests {
    Host host;
    List<Host> guests;
  }
}
