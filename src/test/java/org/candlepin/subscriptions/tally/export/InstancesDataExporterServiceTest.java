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

import static org.candlepin.subscriptions.db.model.InstanceMonthlyTotalKey.formatMonthId;
import static org.candlepin.subscriptions.resource.ResourceUtils.ANY;
import static org.candlepin.subscriptions.resource.api.v1.InstancesResource.getCloudProviderByMeasurementType;
import static org.candlepin.subscriptions.tally.export.InstancesCsvDataMapperService.METRIC_MAPPER;
import static org.candlepin.subscriptions.tally.export.InstancesDataExporterService.BEGINNING;
import static org.candlepin.subscriptions.tally.export.InstancesDataExporterService.PRODUCT_ID;

import com.redhat.cloud.event.apps.exportservice.v1.Format;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.Variant;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.candlepin.subscriptions.json.InstancesExportCsvItem;
import org.candlepin.subscriptions.json.InstancesExportJson;
import org.candlepin.subscriptions.json.InstancesExportJsonGuest;
import org.candlepin.subscriptions.json.InstancesExportJsonItem;
import org.candlepin.subscriptions.json.InstancesExportJsonMetric;
import org.candlepin.subscriptions.util.ApiModelMapperV1;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles({"worker", "kafka-queue", "test-inventory"})
class InstancesDataExporterServiceTest extends BaseDataExporterServiceTest {

  private static final String RHEL_FOR_X86 = "RHEL for x86";
  private static final String ROSA = "rosa";
  private static final String APRIL = "2024-04-16T07:12:52.426707Z";

  private final List<HostWithGuests> itemsToBeExported = new ArrayList<>();

  @Autowired HostRepository repository;

  @Autowired ApiModelMapperV1 mapper;

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
    givenInstanceWithMetrics(RHEL_FOR_X86);
    givenExportRequestWithPermissions(Format.JSON);
    whenReceiveExportRequest();
    verifyRequestWasSentToExportServiceWithError(request);
  }

  @Test
  void testRequestShouldBeUploadedWithInstancesAsJsonFilteringByProductId() {
    givenInstanceWithMetrics(RHEL_FOR_X86);
    givenExportRequestWithPermissions(Format.JSON);
    givenFilterInExportRequest(PRODUCT_ID, RHEL_FOR_X86);
    whenReceiveExportRequest();
    verifyRequestWasSentToExportService();
  }

  @Test
  void testRequestShouldBeUploadedWithInstancesAsJsonFilteringByProductIdAndMonth() {
    givenInstanceWithMetrics(ROSA);
    givenExportRequestWithPermissions(Format.JSON);
    givenFilterInExportRequest(PRODUCT_ID, ROSA);
    givenFilterInExportRequest(BEGINNING, APRIL);
    whenReceiveExportRequest();
    verifyRequestWasSentToExportService();
  }

  @Test
  void testRequestShouldBeUploadedWithInstancesAsJsonFilteringByWrongProductId() {
    givenInstanceWithMetrics(RHEL_FOR_X86);
    givenExportRequestWithPermissions(Format.JSON);
    givenFilterInExportRequest(PRODUCT_ID, "wrong!");
    whenReceiveExportRequest();
    verifyNoRequestsWereSentToExportServiceWithUploadData();
    verifyRequestWasSentToExportServiceWithError(request);
  }

  @Test
  void testRequestShouldBeUploadedWithInstancesAsJsonFilteringByProductIdAndReturnsNoData() {
    givenInstanceWithMetrics(RHEL_FOR_X86);
    givenExportRequestWithPermissions(Format.JSON);
    givenFilterInExportRequest(PRODUCT_ID, ROSA);
    whenReceiveExportRequest();
    verifyRequestWasSentToExportServiceWithNoDataFound();
    verifyNoRequestsWereSentToExportServiceWithError();
  }

  @Test
  void testRequestShouldBeUploadedWithInstancesAsCsv() {
    givenInstanceWithMetrics(RHEL_FOR_X86);
    givenExportRequestWithPermissions(Format.CSV);
    givenFilterInExportRequest(PRODUCT_ID, RHEL_FOR_X86);
    whenReceiveExportRequest();
    verifyRequestWasSentToExportService();
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
    givenInstanceWithMetrics(RHEL_FOR_X86);
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
    givenInstanceWithMetrics(RHEL_FOR_X86);
    givenExportRequestWithPermissions(Format.JSON);
    givenFilterInExportRequest(PRODUCT_ID, RHEL_FOR_X86);
    givenFilterInExportRequest(filterName, doesNotExist);
    whenReceiveExportRequest();
    verifyRequestWasSentToExportServiceWithNoDataFound();
    verifyNoRequestsWereSentToExportServiceWithError();
  }

  @ParameterizedTest
  @ValueSource(strings = {"usage", "category", "sla", "metric_id", "billing_provider", BEGINNING})
  void testFiltersAreInvalid(String filterName) {
    givenInstanceWithMetrics(RHEL_FOR_X86);
    givenExportRequestWithPermissions(Format.JSON);
    givenFilterInExportRequest(PRODUCT_ID, RHEL_FOR_X86);
    givenFilterInExportRequest(filterName, "wrong!");
    whenReceiveExportRequest();
    verifyNoRequestsWereSentToExportServiceWithUploadData();
    verifyRequestWasSentToExportServiceWithError(request);
  }

  @Test
  void testRequestShouldFilterByOrgId() {
    givenInstanceWithMetricsForAnotherOrgId(RHEL_FOR_X86);
    givenInstanceWithMetrics(RHEL_FOR_X86);
    givenExportRequestWithPermissions(Format.JSON);
    givenFilterInExportRequest(PRODUCT_ID, RHEL_FOR_X86);
    whenReceiveExportRequest();
    verifyRequestWasSentToExportService();
  }

  @Override
  protected void verifyRequestWasSentToExportService() {
    boolean isCsvFormat = request.getData().getResourceRequest().getFormat() == Format.CSV;
    String expected = isCsvFormat ? parseExpectedAsCsv() : parseExpectedAsJson();
    verifyRequestWasSentToExportServiceWithUploadData(request, expected);
  }

  private String parseExpectedAsCsv() {
    List<Object> data = new ArrayList<>();
    for (HostWithGuests item : itemsToBeExported) {
      Host host = item.host;
      var instance = new InstancesExportCsvItem();
      instance.setId(host.getId().toString());
      instance.setInstanceId(host.getInstanceId());
      instance.setDisplayName(host.getDisplayName());
      if (host.getBillingProvider() != null) {
        instance.setBillingProvider(host.getBillingProvider().getValue());
      }
      var bucket = host.getBuckets().iterator().next();
      var category = mapper.measurementTypeToReportCategory(bucket.getMeasurementType());
      if (category != null) {
        instance.setCategory(category.toString());
      }

      instance.setBillingAccountId(host.getBillingAccountId());
      var variant = Variant.findByTag(bucket.getKey().getProductId());
      var metrics = MetricIdUtils.getMetricIdsFromConfigForVariant(variant.orElse(null)).toList();
      for (var metric : metrics) {
        var setter = METRIC_MAPPER.get(metric);
        if (setter != null) {
          setter.accept(instance, resolveMetricValue(item, metric));
        }
      }

      instance.setLastSeen(host.getLastSeen());
      instance.setHypervisorUuid(host.getHypervisorUuid());
      data.add(instance);
    }

    return toCsv(data, InstancesExportCsvItem.class);
  }

  private String parseExpectedAsJson() {
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
      var category = mapper.measurementTypeToReportCategory(bucket.getMeasurementType());
      if (category != null) {
        instance.setCategory(category.toString());
      }

      var cloudProvider = getCloudProviderByMeasurementType(bucket.getMeasurementType());
      if (cloudProvider != null) {
        instance.setCloudProvider(cloudProvider.toString());
      }

      instance.setBillingAccountId(host.getBillingAccountId());
      var variant = Variant.findByTag(bucket.getKey().getProductId());
      var metrics =
          MetricIdUtils.getMetricIdsFromConfigForVariant(variant.orElse(null))
              .map(MetricId::toString)
              .toList();
      instance.setMeasurements(new ArrayList<>());
      for (var metricId : metrics) {
        instance
            .getMeasurements()
            .add(
                new InstancesExportJsonMetric()
                    .withMetricId(metricId)
                    .withValue(resolveMetricValue(item, MetricId.fromString(metricId))));
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

    return toJson(expected);
  }

  private void givenInstanceWithMetricsForAnotherOrgId(String productId) {
    var account = givenHostInAccountServices(UUID.randomUUID().toString());
    HostWithGuests instance = givenInstanceWithMetrics(account.getOrgId(), productId);
    // removing it as we don't expect this host from being exported.
    itemsToBeExported.remove(instance);
  }

  private void givenInstanceWithMetrics(String productId) {
    givenInstanceWithMetrics(ORG_ID, productId);
  }

  private HostWithGuests givenInstanceWithMetrics(String orgId, String productId) {
    Host guest = new Host();
    guest.setOrgId(orgId);
    guest.setDisplayName("this is the guest");
    guest.setLastSeen(OffsetDateTime.parse(APRIL));
    guest.setHardwareType(HostHardwareType.PHYSICAL);
    guest.setInstanceType(INSTANCE_TYPE);
    guest.setInstanceId(UUID.randomUUID().toString());
    guest.setHypervisorUuid(UUID.randomUUID().toString());
    repository.save(guest);

    Host instance = new Host();
    instance.setOrgId(orgId);
    instance.setNumOfGuests(1);
    instance.setInstanceId("456");
    instance.setDisplayName("my host");
    instance.setBillingProvider(BillingProvider.AWS);
    instance.setBillingAccountId("123");
    instance.setInstanceType(INSTANCE_TYPE);
    instance.setSubscriptionManagerId(guest.getHypervisorUuid());

    // buckets
    HostTallyBucket bucket = new HostTallyBucket();
    bucket.setKey(new HostBucketKey());
    bucket.getKey().setProductId(productId);
    bucket.getKey().setUsage(Usage._ANY);
    bucket.getKey().setSla(ServiceLevel._ANY);
    bucket.getKey().setAsHypervisor(true);
    bucket.getKey().setBillingProvider(BillingProvider._ANY);
    bucket.getKey().setBillingAccountId(ANY);
    bucket.setMeasurementType(HardwareMeasurementType.PHYSICAL);
    // in non-payg products, buckets metrics should be used over the instance measurements
    bucket.setCores(5);
    bucket.setSockets(6);
    bucket.setHost(instance);
    instance.addBucket(bucket);
    boolean isPayg = !productId.equals(RHEL_FOR_X86);

    if (isPayg) {
      // metrics for payg
      instance.addToMonthlyTotal(OffsetDateTime.parse(APRIL), MetricIdUtils.getSockets(), 7.0);
      instance.addToMonthlyTotal(OffsetDateTime.parse(APRIL), MetricIdUtils.getCores(), 8.0);
    } else {
      // metrics for non-payg
      instance.setMeasurements(
          Map.of(
              MetricIdUtils.getSockets().toUpperCaseFormatted(),
              9.0,
              MetricIdUtils.getCores().toUpperCaseFormatted(),
              10.0));
    }

    // save
    repository.save(instance);
    HostWithGuests item = new HostWithGuests();
    item.host = instance;
    item.guests = List.of(guest);
    item.usePaygProduct = isPayg;
    itemsToBeExported.add(item);
    return item;
  }

  private static double resolveMetricValue(HostWithGuests item, MetricId metricId) {
    Double value = null;
    if (item.usePaygProduct) {
      // then use the monthly totals
      value = item.host.getMonthlyTotal(formatMonthId(OffsetDateTime.parse(APRIL)), metricId);
      System.out.println(
          "Looking for " + metricId + " in " + item.host.getMonthlyTotals() + ". Found: " + value);
    } else {
      // for non payg products:
      if (metricId.equals(MetricIdUtils.getSockets())) {
        value = Double.valueOf(item.host.getBuckets().iterator().next().getSockets());
      } else if (metricId.equals(MetricIdUtils.getCores())) {
        value = Double.valueOf(item.host.getBuckets().iterator().next().getCores());
      }
    }

    return Optional.ofNullable(value).orElse(0.0);
  }

  private static class HostWithGuests {
    Host host;
    List<Host> guests;
    boolean usePaygProduct;
  }
}
