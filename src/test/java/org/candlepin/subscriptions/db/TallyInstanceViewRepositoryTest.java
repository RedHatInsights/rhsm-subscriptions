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
package org.candlepin.subscriptions.db;

import static com.redhat.swatch.configuration.util.MetricIdUtils.getMetricIdsFromConfigForTag;
import static org.candlepin.subscriptions.db.TallyInstanceViewRepository.FIELD_SORT_PARAM_MAPPING;
import static org.candlepin.subscriptions.util.TallyHostBucketFactory.createBucketTuples;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.ProductId;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.candlepin.subscriptions.configuration.FeatureFlags;
import org.candlepin.subscriptions.db.model.AccountServiceInventory;
import org.candlepin.subscriptions.db.model.AccountServiceInventoryId;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.Host;
import org.candlepin.subscriptions.db.model.HostTallyBucket;
import org.candlepin.subscriptions.db.model.InstanceMonthlyTotalKey;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.TallyInstanceView;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.test.ExtendWithSwatchDatabase;
import org.candlepin.subscriptions.util.PrimaryRecordUtils;
import org.candlepin.subscriptions.utilization.api.v1.model.SortDirection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles({"worker", "test-inventory"})
class TallyInstanceViewRepositoryTest implements ExtendWithSwatchDatabase {

  // Using this product ID because it has two valid metrics: Sockets and Cores
  private static final ProductId OPENSHIFT_CONTAINER_PLATFORM =
      ProductId.fromString("OpenShift Container Platform");
  // Using ROSA product because it also has the Instance-Hours metric.
  private static final ProductId ROSA = ProductId.fromString("rosa");
  private static final ProductId RHEL = ProductId.fromString("RHEL for x86");
  private static final String DEFAULT_DISPLAY_NAME = "REDHAT_PWNS";
  private static final String SORT_BY_CORES = "cores";
  private static final String SORT_BY_BILLING_PROVIDER = "billing_provider";
  private static final String BILLING_ACCOUNT_ID_ANY = "_ANY";
  private static final String DEFAULT_ORG_ID = "org123";

  @MockitoBean FeatureFlags featureFlags;
  @Autowired private TallyInstanceViewRepository repo;
  @Autowired private HostRepository hostRepo;
  @Autowired private AccountServiceInventoryRepository accountServiceInventoryRepository;

  private List<Host> defaultHosts;

  @Transactional
  @BeforeEach
  void setupTestData() {
    Host host8 = createBaseHost("inventory8", DEFAULT_ORG_ID);
    host8.setBillingProvider(BillingProvider.RED_HAT);
    host8.setBillingAccountId("RH-0008");
    host8.setLastAppliedEventRecordDate("HBI_EVENT", OffsetDateTime.now());

    Host host9 = createBaseHost("inventory9", DEFAULT_ORG_ID);
    host9.setBillingProvider(BillingProvider.RED_HAT);
    host9.setBillingAccountId("RH-0009");
    host9.setLastAppliedEventRecordDate("HBI_EVENT", OffsetDateTime.now().minusDays(1));

    Host host10 = createBaseHost("inventory10", DEFAULT_ORG_ID);
    host10.setBillingProvider(BillingProvider.RED_HAT);
    host10.setBillingAccountId("RH-0010");
    host10.setLastAppliedEventRecordDate("HBI_EVENT", OffsetDateTime.now().minusDays(2));

    for (MetricId metricId : MetricId.getAll()) {
      host8.setMeasurement(metricId.toString(), 100.0);
      host9.setMeasurement(metricId.toString(), 0.0);
      host10.setMeasurement(metricId.toString(), 50.0);
    }

    addMeasurementsToMonthlyTotals(
        host8, OffsetDateTime.of(LocalDateTime.of(2021, 1, 1, 0, 0, 0), ZoneOffset.UTC));
    withPayGoBuckets(
        host8,
        HardwareMeasurementType.PHYSICAL,
        ROSA,
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        100,
        100);

    addMeasurementsToMonthlyTotals(
        host9, OffsetDateTime.of(LocalDateTime.of(2021, 1, 1, 0, 0, 0), ZoneOffset.UTC));
    withPayGoBuckets(
        host9,
        HardwareMeasurementType.PHYSICAL,
        ROSA,
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        0,
        0);

    addMeasurementsToMonthlyTotals(
        host10, OffsetDateTime.of(LocalDateTime.of(2021, 2, 1, 0, 0, 0), ZoneOffset.UTC));
    withPayGoBuckets(
        host10,
        HardwareMeasurementType.PHYSICAL,
        ROSA,
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        50,
        50);

    defaultHosts = persistHosts(host8, host9, host10);
  }

  abstract class FeatureFlagTests {
    @Transactional
    @ParameterizedTest
    @MethodSource(
        "org.candlepin.subscriptions.db.TallyInstanceViewRepositoryTest#instanceSortParams")
    void canSortByInstanceBasedSortMethods(String sort) {
      Page<TallyInstanceView> results =
          repo.findAllBy(
              DEFAULT_ORG_ID,
              ROSA,
              ServiceLevel._ANY,
              Usage._ANY,
              "",
              0,
              0,
              "2021-01",
              MetricIdUtils.getCores(),
              BillingProvider._ANY,
              BILLING_ACCOUNT_ID_ANY,
              null,
              0,
              2,
              sort,
              SortDirection.ASC);

      assertEquals(2, results.getTotalElements());
    }

    @Transactional
    @Test
    void testCanSortByMetricsForNonPayg() {
      // using instance hours, because sort by Cores and Sockets work and it's already covered by
      // the
      // previous test.
      MetricId referenceMetricId = MetricIdUtils.getInstanceHours();

      var host1 = createBaseHost(UUID.randomUUID().toString(), DEFAULT_ORG_ID);
      host1.setMeasurement(referenceMetricId.toString(), 5.0);
      withTraditionalProductBuckets(
          host1,
          HardwareMeasurementType.PHYSICAL,
          OPENSHIFT_CONTAINER_PLATFORM,
          ServiceLevel.PREMIUM,
          Usage.PRODUCTION,
          0,
          0);

      var host2 = createBaseHost(UUID.randomUUID().toString(), DEFAULT_ORG_ID);
      host2.setMeasurement(referenceMetricId.toString(), 10.0);
      withTraditionalProductBuckets(
          host2,
          HardwareMeasurementType.PHYSICAL,
          OPENSHIFT_CONTAINER_PLATFORM,
          ServiceLevel.PREMIUM,
          Usage.PRODUCTION,
          0,
          0);

      persistHosts(host1, host2);

      Page<TallyInstanceView> results =
          repo.findAllBy(
              DEFAULT_ORG_ID,
              OPENSHIFT_CONTAINER_PLATFORM,
              ServiceLevel._ANY,
              Usage._ANY,
              "",
              0,
              0,
              "2021-01",
              referenceMetricId,
              BillingProvider._ANY,
              BILLING_ACCOUNT_ID_ANY,
              null,
              0,
              2,
              referenceMetricId.toString(),
              SortDirection.DESC);

      // host2 should be returned first because we ordered by Instance-Hours descending
      assertEquals(host2.getInstanceId(), results.getContent().get(0).getKey().getInstanceId());
      assertEquals(host1.getInstanceId(), results.getContent().get(1).getKey().getInstanceId());
    }

    @Test
    @Transactional
    void testFilterByBillingModel() {
      Host host1 = createHost("i1", "a1");
      host1.setBillingProvider(BillingProvider.RED_HAT);
      host1.setBillingAccountId("RH-0001");
      addMeasurementsToMonthlyTotals(host1, OffsetDateTime.now());
      withPayGoBuckets(
          host1,
          HardwareMeasurementType.VIRTUAL,
          ROSA,
          ServiceLevel.PREMIUM,
          Usage.PRODUCTION,
          1,
          1);

      Host host2 = createHost("i2", "a1");
      host2.setBillingProvider(BillingProvider.AWS);
      host2.setBillingAccountId("AWS-0001");
      addMeasurementsToMonthlyTotals(host2, OffsetDateTime.now());
      withPayGoBuckets(
          host2,
          HardwareMeasurementType.VIRTUAL,
          ROSA,
          ServiceLevel.PREMIUM,
          Usage.PRODUCTION,
          1,
          1);

      Host host3 = createHost("i3", "a1");
      host3.setBillingProvider(BillingProvider.GCP);
      host3.setBillingAccountId("GCP-0001");
      addMeasurementsToMonthlyTotals(host3, OffsetDateTime.now());
      withPayGoBuckets(
          host3,
          HardwareMeasurementType.VIRTUAL,
          ROSA,
          ServiceLevel.PREMIUM,
          Usage.PRODUCTION,
          1,
          1);

      persistHosts(host1, host2, host3);

      Page<TallyInstanceView> results =
          repo.findAllBy(
              "a1",
              ROSA,
              ServiceLevel.PREMIUM,
              Usage.PRODUCTION,
              "",
              0,
              0,
              null,
              MetricIdUtils.getCores(),
              BillingProvider.AWS,
              BILLING_ACCOUNT_ID_ANY,
              null,
              0,
              10,
              SORT_BY_CORES,
              SortDirection.ASC);
      assertEquals(1L, results.getTotalElements());
      assertEquals(BillingProvider.AWS, results.getContent().get(0).getHostBillingProvider());

      Page<TallyInstanceView> allResults =
          repo.findAllBy(
              "a1",
              ROSA,
              ServiceLevel.PREMIUM,
              Usage.PRODUCTION,
              "",
              0,
              0,
              null,
              MetricIdUtils.getCores(),
              BillingProvider._ANY,
              BILLING_ACCOUNT_ID_ANY,
              null,
              0,
              10,
              SORT_BY_CORES,
              SortDirection.ASC);
      assertEquals(3L, allResults.getTotalElements());
      Map<String, TallyInstanceView> hostToBill =
          allResults.stream()
              .collect(Collectors.toMap(t -> t.getKey().getInstanceId(), Function.identity()));

      assertTrue(
          hostToBill
              .keySet()
              .containsAll(
                  Arrays.asList(
                      host1.getInstanceId(), host2.getInstanceId(), host3.getInstanceId())),
          "Result did not contain expected hosts!");
      assertEquals(
          BillingProvider.RED_HAT, hostToBill.get(host1.getInstanceId()).getHostBillingProvider());
      assertEquals(
          BillingProvider.AWS, hostToBill.get(host2.getInstanceId()).getHostBillingProvider());
      assertEquals(
          BillingProvider.GCP, hostToBill.get(host3.getInstanceId()).getHostBillingProvider());
    }

    @Test
    @Transactional
    void testSortByBillingProvider() {
      Host host1 = createHost("i1", "a1");
      host1.setBillingProvider(BillingProvider.RED_HAT);
      addBucketToHost(
          host1,
          RHEL,
          ServiceLevel.PREMIUM,
          Usage.PRODUCTION,
          HardwareMeasurementType.PHYSICAL,
          BillingProvider.RED_HAT);
      addBucketToHost(
          host1,
          RHEL,
          ServiceLevel.PREMIUM,
          Usage.PRODUCTION,
          HardwareMeasurementType.PHYSICAL,
          BillingProvider._ANY);

      Host host2 = createHost("i2", "a1");
      host2.setBillingProvider(BillingProvider.AWS);
      addBucketToHost(
          host2,
          RHEL,
          ServiceLevel.PREMIUM,
          Usage.PRODUCTION,
          HardwareMeasurementType.PHYSICAL,
          BillingProvider.AWS);
      addBucketToHost(
          host2,
          RHEL,
          ServiceLevel.PREMIUM,
          Usage.PRODUCTION,
          HardwareMeasurementType.PHYSICAL,
          BillingProvider._ANY);

      Host host3 = createHost("i3", "a1");
      host3.setBillingProvider(BillingProvider.EMPTY);
      addBucketToHost(
          host3,
          RHEL,
          ServiceLevel.PREMIUM,
          Usage.PRODUCTION,
          HardwareMeasurementType.PHYSICAL,
          BillingProvider.EMPTY);
      addBucketToHost(
          host3,
          RHEL,
          ServiceLevel.PREMIUM,
          Usage.PRODUCTION,
          HardwareMeasurementType.PHYSICAL,
          BillingProvider._ANY);

      Host host4 = createHost("i4", "a1");
      host4.setBillingProvider(BillingProvider.ORACLE);
      addBucketToHost(
          host4,
          RHEL,
          ServiceLevel.PREMIUM,
          Usage.PRODUCTION,
          HardwareMeasurementType.PHYSICAL,
          BillingProvider.ORACLE);
      addBucketToHost(
          host4,
          RHEL,
          ServiceLevel.PREMIUM,
          Usage.PRODUCTION,
          HardwareMeasurementType.PHYSICAL,
          BillingProvider._ANY);

      persistHosts(host1, host2, host3, host4);
      hostRepo.flush();

      Page<TallyInstanceView> results =
          repo.findAllBy(
              "a1",
              RHEL,
              ServiceLevel.PREMIUM,
              Usage.PRODUCTION,
              "",
              0,
              0,
              null,
              MetricIdUtils.getCores(),
              BillingProvider._ANY,
              BILLING_ACCOUNT_ID_ANY,
              null,
              0,
              10,
              SORT_BY_BILLING_PROVIDER,
              SortDirection.DESC);
      assertEquals(4L, results.getTotalElements());
      assertEquals(BillingProvider.RED_HAT, results.getContent().get(0).getHostBillingProvider());
      assertEquals(BillingProvider.ORACLE, results.getContent().get(1).getHostBillingProvider());
      assertEquals(BillingProvider.AWS, results.getContent().get(2).getHostBillingProvider());
      assertEquals(BillingProvider.EMPTY, results.getContent().get(3).getHostBillingProvider());
    }

    @Transactional
    @Test
    void testFilterByHardwareMeasurementTypes() {
      Host host1 = createHost(UUID.randomUUID().toString(), "a1");
      withTraditionalProductBuckets(
          host1,
          HardwareMeasurementType.PHYSICAL,
          RHEL,
          ServiceLevel.PREMIUM,
          Usage.PRODUCTION,
          1,
          1);

      Host host2 = createHost(UUID.randomUUID().toString(), "a1");
      withTraditionalProductBuckets(
          host2,
          HardwareMeasurementType.VIRTUAL,
          RHEL,
          ServiceLevel.PREMIUM,
          Usage.PRODUCTION,
          1,
          1);

      Host host3 = createHost(UUID.randomUUID().toString(), "a1");
      withTraditionalProductBuckets(
          host3,
          HardwareMeasurementType.HYPERVISOR,
          RHEL,
          ServiceLevel.PREMIUM,
          Usage.PRODUCTION,
          1,
          1);
      persistHosts(host1, host2, host3);

      Page<TallyInstanceView> results =
          repo.findAllBy(
              "a1",
              RHEL,
              ServiceLevel.PREMIUM,
              Usage.PRODUCTION,
              "",
              0,
              0,
              null,
              MetricIdUtils.getCores(),
              BillingProvider._ANY,
              BILLING_ACCOUNT_ID_ANY,
              List.of(HardwareMeasurementType.VIRTUAL),
              0,
              10,
              SORT_BY_CORES,
              SortDirection.ASC);
      assertEquals(1L, results.getTotalElements());
      assertEquals(
          HardwareMeasurementType.VIRTUAL,
          results.getContent().get(0).getKey().getMeasurementType());

      Page<TallyInstanceView> allResults =
          repo.findAllBy(
              "a1",
              RHEL,
              ServiceLevel.PREMIUM,
              Usage.PRODUCTION,
              "",
              0,
              0,
              null,
              MetricIdUtils.getCores(),
              BillingProvider._ANY,
              BILLING_ACCOUNT_ID_ANY,
              null,
              0,
              10,
              SORT_BY_CORES,
              SortDirection.ASC);
      assertEquals(3L, allResults.getTotalElements());
      Map<String, TallyInstanceView> hostToBill =
          allResults.stream()
              .collect(
                  Collectors.toMap(
                      instance -> instance.getKey().getInstanceId(), Function.identity()));
      assertTrue(
          hostToBill
              .keySet()
              .containsAll(
                  Arrays.asList(
                      host1.getInstanceId(), host2.getInstanceId(), host3.getInstanceId())),
          "Result did not contain expected hosts!");
      assertEquals(
          HardwareMeasurementType.PHYSICAL,
          hostToBill.get(host1.getInstanceId()).getKey().getMeasurementType());
      assertEquals(
          HardwareMeasurementType.VIRTUAL,
          hostToBill.get(host2.getInstanceId()).getKey().getMeasurementType());
      assertEquals(
          HardwareMeasurementType.HYPERVISOR,
          hostToBill.get(host3.getInstanceId()).getKey().getMeasurementType());
    }

    @Transactional
    @Test
    void testGetResultWithEmptyMeasurementType() {
      Host host1 = createHost(UUID.randomUUID().toString(), "a1");
      withTraditionalProductBuckets(
          host1, null, RHEL, ServiceLevel.PREMIUM, Usage.PRODUCTION, 1, 1);

      persistHosts(host1);

      Page<TallyInstanceView> results =
          repo.findAllBy(
              "a1",
              RHEL,
              ServiceLevel.PREMIUM,
              Usage.PRODUCTION,
              "",
              0,
              0,
              null,
              MetricIdUtils.getCores(),
              BillingProvider._ANY,
              BILLING_ACCOUNT_ID_ANY,
              null,
              0,
              10,
              SORT_BY_CORES,
              SortDirection.ASC);
      assertEquals(1L, results.getTotalElements());
      assertEquals(
          HardwareMeasurementType.EMPTY, results.getContent().get(0).getKey().getMeasurementType());
    }

    @Transactional
    @Test
    void testFilterByCloudHardwareMeasurementTypes() {
      Host host1 = createHost(UUID.randomUUID().toString(), "a1");
      withTraditionalProductBuckets(
          host1, HardwareMeasurementType.AWS, RHEL, ServiceLevel.PREMIUM, Usage.PRODUCTION, 1, 1);

      Host host2 = createHost(UUID.randomUUID().toString(), "a1");
      withTraditionalProductBuckets(
          host2, HardwareMeasurementType.AZURE, RHEL, ServiceLevel.PREMIUM, Usage.PRODUCTION, 1, 1);

      Host host3 = createHost(UUID.randomUUID().toString(), "a1");
      withTraditionalProductBuckets(
          host3,
          HardwareMeasurementType.GOOGLE,
          RHEL,
          ServiceLevel.PREMIUM,
          Usage.PRODUCTION,
          1,
          1);

      Host host5 = createHost(UUID.randomUUID().toString(), "a1");
      withTraditionalProductBuckets(
          host5,
          HardwareMeasurementType.PHYSICAL,
          RHEL,
          ServiceLevel.PREMIUM,
          Usage.PRODUCTION,
          1,
          1);

      persistHosts(host1, host2, host3, host5);

      Page<TallyInstanceView> results =
          repo.findAllBy(
              "a1",
              RHEL,
              ServiceLevel.PREMIUM,
              Usage.PRODUCTION,
              "",
              0,
              0,
              null,
              MetricIdUtils.getCores(),
              BillingProvider._ANY,
              BILLING_ACCOUNT_ID_ANY,
              HardwareMeasurementType.getCloudProviderTypes(),
              0,
              10,
              SORT_BY_CORES,
              SortDirection.ASC);
      assertEquals(3L, results.getTotalElements());
      Map<String, TallyInstanceView> hostToBill =
          results.stream()
              .collect(Collectors.toMap(t -> t.getKey().getInstanceId(), Function.identity()));
      assertTrue(
          hostToBill
              .keySet()
              .containsAll(
                  Arrays.asList(
                      host1.getInstanceId(), host2.getInstanceId(), host3.getInstanceId())));
    }

    @Test
    @Transactional
    void testFilterByMinCoresAndSockets() {
      Host host1 = createBaseHost("i1", "a1");
      host1.setMeasurement(MetricIdUtils.getSockets().toString(), 0.0);
      host1.setMeasurement(MetricIdUtils.getCores().toString(), 4.0);
      withTraditionalProductBuckets(
          host1,
          HardwareMeasurementType.PHYSICAL,
          RHEL,
          ServiceLevel.PREMIUM,
          Usage.PRODUCTION,
          0,
          4);

      Host host2 = createBaseHost("i2", "a1");
      host2.setMeasurement(MetricIdUtils.getSockets().toString(), 2.0);
      host2.setMeasurement(MetricIdUtils.getCores().toString(), 0.0);
      withTraditionalProductBuckets(
          host2, HardwareMeasurementType.AWS, RHEL, ServiceLevel.PREMIUM, Usage.PRODUCTION, 2, 0);

      Host host3 = createBaseHost("i3", "a1");
      host3.setMeasurement(MetricIdUtils.getCores().toString(), 2.0);
      host3.setMeasurement(MetricIdUtils.getSockets().toString(), 2.0);
      withTraditionalProductBuckets(
          host3, HardwareMeasurementType.EMPTY, RHEL, ServiceLevel.PREMIUM, Usage.PRODUCTION, 2, 2);

      persistHosts(host1, host2, host3);

      Page<TallyInstanceView> coresResults =
          repo.findAllBy(
              "a1",
              RHEL,
              ServiceLevel.PREMIUM,
              Usage.PRODUCTION,
              "",
              1,
              0,
              null,
              MetricIdUtils.getCores(),
              null,
              BILLING_ACCOUNT_ID_ANY,
              null,
              0,
              10,
              SORT_BY_CORES,
              SortDirection.ASC);
      assertEquals(2L, coresResults.getTotalElements());
      List<String> coreResultsIds =
          coresResults.stream().map(r -> r.getKey().getInstanceId()).toList();
      assertTrue(coreResultsIds.containsAll(List.of(host1.getInstanceId(), host3.getInstanceId())));

      Page<TallyInstanceView> socketsResults =
          repo.findAllBy(
              "a1",
              RHEL,
              ServiceLevel.PREMIUM,
              Usage.PRODUCTION,
              "",
              0,
              1,
              null,
              MetricIdUtils.getSockets(),
              null,
              BILLING_ACCOUNT_ID_ANY,
              null,
              0,
              10,
              SORT_BY_CORES,
              SortDirection.ASC);
      assertEquals(2L, socketsResults.getTotalElements());
      List<String> socketsResultIds =
          socketsResults.stream().map(r -> r.getKey().getInstanceId()).collect(Collectors.toList());
      assertThat(
          socketsResultIds, containsInAnyOrder(host2.getInstanceId(), host3.getInstanceId()));
    }

    @Transactional
    @Test
    void testWithoutAnyFilterForMetricId() {
      Page<TallyInstanceView> results =
          repo.findAllBy(
              DEFAULT_ORG_ID,
              ROSA,
              ServiceLevel._ANY,
              Usage._ANY,
              null,
              0,
              0,
              null,
              null,
              BillingProvider._ANY,
              BILLING_ACCOUNT_ID_ANY,
              null,
              0,
              10,
              null,
              null);
      List<MetricId> validMetricsByProduct = getMetricIdsFromConfigForTag(ROSA.toString()).toList();

      // to ensure we're using a product with two metrics
      assertEquals(2, validMetricsByProduct.size());
      assertEquals(defaultHosts.size(), (int) results.getTotalElements());
      for (var host : defaultHosts) {
        for (var metric : validMetricsByProduct) {
          assertTrue(
              results.stream().anyMatch(h -> h.getMetrics().containsKey(metric)),
              "Metric " + metric + " not found in the host " + host.getInventoryId());
        }
      }
    }
  }

  private void addMeasurementsToMonthlyTotals(Host host, OffsetDateTime month) {
    host.getMeasurements()
        .forEach(
            (metricId, value) -> {
              host.addToMonthlyTotal(
                  InstanceMonthlyTotalKey.formatMonthId(month),
                  MetricId.fromString(metricId),
                  value);
            });
  }

  @Nested
  class WithPrimaryRowSearchesEnabled extends FeatureFlagTests {
    @BeforeEach
    void setup() {
      when(featureFlags.isEnabled(FeatureFlags.ENABLE_HTB_PRIMARY_ROW_SEARCHES, false))
          .thenReturn(true);
    }

    @Test
    @Transactional
    void testNonPrimaryBucketIsExcluded() {
      Host primaryHost = createHost("inv-primary", "org-primary");
      addBucketToHost(
          primaryHost,
          RHEL,
          ServiceLevel.PREMIUM,
          Usage.PRODUCTION,
          HardwareMeasurementType.PHYSICAL,
          BillingProvider._ANY,
          2,
          4);

      Host nonPrimaryHost = createHost("inv-non-primary", "org-primary");
      addBucketToHost(
          nonPrimaryHost,
          RHEL,
          ServiceLevel._ANY,
          Usage._ANY,
          HardwareMeasurementType.PHYSICAL,
          BillingProvider._ANY,
          2,
          4);

      persistHosts(primaryHost, nonPrimaryHost);

      Page<TallyInstanceView> results =
          repo.findAllBy(
              "org-primary",
              RHEL,
              null,
              null,
              "",
              0,
              0,
              null,
              MetricIdUtils.getCores(),
              BillingProvider._ANY,
              BILLING_ACCOUNT_ID_ANY,
              null,
              0,
              10,
              SORT_BY_CORES,
              SortDirection.ASC);
      assertEquals(1L, results.getTotalElements());
      assertEquals(
          primaryHost.getInstanceId(), results.getContent().get(0).getKey().getInstanceId());
    }
  }

  @Nested
  class WithPrimaryRowSearchesDisabled extends FeatureFlagTests {
    @BeforeEach
    void setup() {
      when(featureFlags.isEnabled(FeatureFlags.ENABLE_HTB_PRIMARY_ROW_SEARCHES, false))
          .thenReturn(false);
    }

    @Test
    @Transactional
    void testNonPrimaryBucketIsIncluded() {
      Host primaryHost = createHost("inv-primary", "org-primary");
      addBucketToHost(
          primaryHost,
          RHEL,
          ServiceLevel.PREMIUM,
          Usage.PRODUCTION,
          HardwareMeasurementType.PHYSICAL,
          BillingProvider._ANY,
          2,
          4);

      Host nonPrimaryHost = createHost("inv-non-primary", "org-primary");
      addBucketToHost(
          nonPrimaryHost,
          RHEL,
          ServiceLevel._ANY,
          Usage._ANY,
          HardwareMeasurementType.PHYSICAL,
          BillingProvider._ANY,
          2,
          4);

      persistHosts(primaryHost, nonPrimaryHost);

      Page<TallyInstanceView> results =
          repo.findAllBy(
              "org-primary",
              RHEL,
              null,
              null,
              "",
              0,
              0,
              null,
              MetricIdUtils.getCores(),
              BillingProvider._ANY,
              BILLING_ACCOUNT_ID_ANY,
              null,
              0,
              10,
              SORT_BY_CORES,
              SortDirection.ASC);
      assertEquals(2L, results.getTotalElements());
    }
  }

  private List<Host> persistHosts(Host... hosts) {
    List<Host> toSave = Arrays.asList(hosts);
    toSave.stream()
        .filter(h -> h.getDisplayName() == null)
        .forEach(x -> x.setDisplayName(DEFAULT_DISPLAY_NAME));
    List<Host> results = new ArrayList<>();
    Arrays.stream(hosts)
        .forEach(
            host -> {
              AccountServiceInventory accountServiceInventory =
                  accountServiceInventoryRepository
                      .findById(
                          AccountServiceInventoryId.builder()
                              .orgId(host.getOrgId())
                              .serviceType("HBI_HOST")
                              .build())
                      .orElse(new AccountServiceInventory(host.getOrgId(), "HBI_HOST"));
              accountServiceInventory.getServiceInstances().put(host.getInstanceId(), host);
              accountServiceInventoryRepository.save(accountServiceInventory);
              results.add(accountServiceInventory.getServiceInstances().get(host.getInstanceId()));
            });
    accountServiceInventoryRepository.flush();
    return results;
  }

  private Host createBaseHost(String inventoryId, String org) {
    Host host = new Host(inventoryId, UUID.randomUUID().toString(), org, "SUBMAN_" + inventoryId);
    host.setBillingAccountId(BILLING_ACCOUNT_ID_ANY);
    host.setBillingProvider(BillingProvider._ANY);
    host.setInstanceId(UUID.randomUUID().toString());
    return host;
  }

  private Host createHost(String inventoryId, String org) {
    Host host = createBaseHost(inventoryId, org);
    host.setMeasurement(MetricIdUtils.getSockets().toString(), 1.0);
    host.setMeasurement(MetricIdUtils.getCores().toString(), 1.0);
    return host;
  }

  private void withTraditionalProductBuckets(
      Host host,
      HardwareMeasurementType hmt,
      ProductId productId,
      ServiceLevel serviceLevel,
      Usage usage,
      double sockets,
      double cores) {
    // Traditional products create cartesian product with:
    // - SLA: {actual, _ANY}
    // - Usage: {actual, _ANY}
    // - BillingProvider: {_ANY} (always)
    // - BillingAccountId: {"_ANY"} (always)
    // This creates 2 × 2 × 1 × 1 = 4 buckets

    List<HostTallyBucket> buckets =
        createBucketTuples(
            productId,
            hmt,
            serviceLevel,
            usage,
            host.getBillingProvider(),
            host.getBillingAccountId(),
            sockets,
            cores);

    buckets.forEach(host::addBucket);
  }

  /**
   * Creates the cartesian product of buckets for the given host and product.
   *
   * @param host the host
   * @param hmt the hardware measurement type
   * @param productId the product id
   * @param serviceLevel the service level
   * @param usage the usage
   */
  private void withPayGoBuckets(
      Host host,
      HardwareMeasurementType hmt,
      ProductId productId,
      ServiceLevel serviceLevel,
      Usage usage,
      double sockets,
      double cores) {
    // PAYG products create cartesian product with:
    // - SLA: {actual, _ANY}
    // - Usage: {actual, _ANY}
    // - BillingProvider: {actual, _ANY}
    // - BillingAccountId: {actual, "_ANY"}
    // This creates 2 × 2 × 2 × 2 = 16 buckets

    List<HostTallyBucket> buckets =
        createBucketTuples(
            productId,
            hmt,
            serviceLevel,
            usage,
            host.getBillingProvider(),
            host.getBillingAccountId(),
            sockets,
            cores);

    buckets.forEach(host::addBucket);
  }

  private void addBucketToHost(
      Host host,
      ProductId productId,
      ServiceLevel sla,
      Usage usage,
      HardwareMeasurementType measurementType,
      BillingProvider billingProvider) {
    addBucketToHost(host, productId, sla, usage, measurementType, billingProvider, 4, 2);
  }

  private void addBucketToHost(
      Host host,
      ProductId productId,
      ServiceLevel sla,
      Usage usage,
      HardwareMeasurementType measurementType,
      BillingProvider billingProvider,
      int sockets,
      int cores) {
    var bucket =
        host.addBucket(
            productId.toString(),
            sla,
            usage,
            billingProvider,
            BILLING_ACCOUNT_ID_ANY,
            true,
            sockets,
            cores,
            measurementType);
    bucket.setPrimary(PrimaryRecordUtils.isPrimaryRecord(bucket));
  }

  static String[] instanceSortParams() {
    return FIELD_SORT_PARAM_MAPPING.keySet().toArray(new String[0]);
  }
}
