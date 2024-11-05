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
package com.redhat.swatch.billable.usage.admin.api;

import static com.redhat.swatch.billable.usage.util.MockHelper.setSubscriptionDefinitionRegistry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redhat.swatch.billable.usage.configuration.ApplicationConfiguration;
import com.redhat.swatch.billable.usage.data.BillableUsageRemittanceEntity;
import com.redhat.swatch.billable.usage.data.BillableUsageRemittanceFilter;
import com.redhat.swatch.billable.usage.data.BillableUsageRemittanceRepository;
import com.redhat.swatch.billable.usage.data.RemittanceStatus;
import com.redhat.swatch.billable.usage.openapi.model.MonthlyRemittance;
import com.redhat.swatch.billable.usage.services.BillingProducer;
import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import com.redhat.swatch.configuration.registry.SubscriptionDefinitionRegistry;
import com.redhat.swatch.configuration.registry.Variant;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.billable.usage.AccumulationPeriodFormatter;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@QuarkusTest
class InternalBillableUsageControllerTest {

  private static final String PRODUCT_ID = "rosa";
  private static final int REMITTANCES_BATCH_SIZE = 2;
  private static final String AWS_METRIC_ID = "aws_metric";

  @Inject BillableUsageRemittanceRepository remittanceRepo;
  @Inject ApplicationClock clock;
  @InjectMock BillingProducer billingProducer;
  @Inject InternalBillableUsageController controller;
  @InjectMock ApplicationConfiguration configuration;

  private static SubscriptionDefinitionRegistry originalReference;

  private final SubscriptionDefinitionRegistry mockSubscriptionDefinitionRegistry =
      mock(SubscriptionDefinitionRegistry.class);

  @BeforeAll
  static void setupClass() {
    originalReference = SubscriptionDefinitionRegistry.getInstance();
  }

  @Transactional
  @BeforeEach
  void setup() {
    remittanceRepo.deleteAll();
    BillableUsageRemittanceEntity remittance1 =
        remittance(
            "111",
            "product1",
            BillableUsage.BillingProvider.AWS,
            24.0,
            clock.startOfCurrentMonth(),
            RemittanceStatus.SUCCEEDED);
    BillableUsageRemittanceEntity remittance2 =
        remittance(
            "org123",
            "product1",
            BillableUsage.BillingProvider.AWS,
            12.0,
            clock.endOfCurrentQuarter(),
            RemittanceStatus.PENDING);
    BillableUsageRemittanceEntity remittance3 =
        remittance(
            "org123",
            "product1",
            BillableUsage.BillingProvider.RED_HAT,
            12.0,
            clock.startOfCurrentMonth(),
            RemittanceStatus.PENDING);
    remittance3.setMetricId("Transfer-gibibytes");
    BillableUsageRemittanceEntity remittance4 =
        remittance(
            "org345",
            "product2",
            BillableUsage.BillingProvider.RED_HAT,
            8.0,
            clock.startOfCurrentMonth(),
            RemittanceStatus.PENDING);
    BillableUsageRemittanceEntity remittance5 =
        remittance(
            "org345",
            "product3",
            BillableUsage.BillingProvider.AZURE,
            4.0,
            clock.startOfCurrentMonth(),
            RemittanceStatus.FAILED);
    BillableUsageRemittanceEntity remittance6 =
        remittance(
            "1234",
            "rosa",
            BillableUsage.BillingProvider.AWS,
            24.0,
            clock.startOfCurrentMonth(),
            RemittanceStatus.PENDING);
    BillableUsageRemittanceEntity remittance7 =
        remittance(
            "5678",
            "rosa",
            BillableUsage.BillingProvider.AWS,
            24.0,
            clock.startOfCurrentMonth(),
            RemittanceStatus.PENDING);
    remittanceRepo.persist(
        List.of(
            remittance1,
            remittance2,
            remittance3,
            remittance4,
            remittance5,
            remittance6,
            remittance7));
    remittanceRepo.flush();
    when(configuration.getRetryRemittancesBatchSize()).thenReturn(REMITTANCES_BATCH_SIZE);

    // reset original subscription definition registry
    setSubscriptionDefinitionRegistry(originalReference);
  }

  @Transactional
  @AfterEach
  void tearDown() {
    remittanceRepo.deleteAll();
    /* We need to reset the registry because the mock SubscriptionDefinitionRegistry uses a stubbed
     * SubscriptionDefinition.  If the test run order happens to result in that stub being used first for a tag lookup
     * then the SubscriptionDefinition cache will be populated with the stub's particulars which we don't want. */
    SubscriptionDefinitionRegistry.reset();
  }

  @Test
  void ifAccountNotFoundDisplayEmptyAccountRemittance() {
    var response =
        controller.getRemittances(
            BillableUsageRemittanceFilter.builder()
                .orgId("not_found")
                .productId("product1")
                .build());
    assertFalse(response.isEmpty());
    assertEquals(0.0, response.get(0).getRemittedValue());
  }

  @Test
  void testFilterByOrgId() {
    var response =
        controller.getRemittances(
            BillableUsageRemittanceFilter.builder().productId("product1").orgId("111").build());
    assertFalse(response.isEmpty());
    assertEquals(24.0, response.get(0).getRemittedValue());
    assertEquals("Instance-hours", response.get(0).getMetricId());
    assertEquals(BillableUsage.Status.SUCCEEDED.value(), response.get(0).getRemittanceStatus());
  }

  @Test
  void testFilterByAccountAndProduct() {
    var response =
        controller.getRemittances(
            BillableUsageRemittanceFilter.builder().orgId("org123").productId("product1").build());
    assertFalse(response.isEmpty());
    assertEquals(2, response.size());
    assertEquals(24.0, response.get(0).getRemittedValue() + response.get(1).getRemittedValue());
    assertEquals(BillableUsage.Status.PENDING.value(), response.get(0).getRemittanceStatus());
  }

  @Test
  void testFilterByAccountAndProductAndMetricId() {
    var response =
        controller.getRemittances(
            BillableUsageRemittanceFilter.builder()
                .orgId("org123")
                .productId("product1")
                .metricId("Transfer-gibibytes")
                .build());

    assertFalse(response.isEmpty());
    assertEquals(1, response.size());
    assertEquals(12.0, response.get(0).getRemittedValue());
    assertEquals("Transfer-gibibytes", response.get(0).getMetricId());
  }

  @Test
  void testFilterByOrgIdAndProductAndMetricId() {
    var response =
        controller.getRemittances(
            BillableUsageRemittanceFilter.builder()
                .orgId("org123")
                .productId("product1")
                .metricId("Instance-hours")
                .build());
    assertEquals(1, response.size());
    MonthlyRemittance result = response.get(0);
    assertEquals("product1", result.getProductId());
    assertEquals("org123", result.getOrgId());
    assertEquals("Instance-hours", result.getMetricId());
    assertEquals(12, result.getRemittedValue());
    assertEquals(BillableUsage.BillingProvider.AWS.value(), result.getBillingProvider());
  }

  @Test
  void testAccountAndOrgIdShouldReturnEmpty() {
    var response =
        controller.getRemittances(
            BillableUsageRemittanceFilter.builder()
                .productId("product1")
                .metricId("Instance-hours")
                .build());
    assertTrue(response.isEmpty());
  }

  @Test
  void testFilterByBillingProviderAndOrgId() {
    var response =
        controller.getRemittances(
            BillableUsageRemittanceFilter.builder()
                .orgId("org123")
                .billingProvider(BillableUsage.BillingProvider.RED_HAT.value())
                .build());
    assertFalse(response.isEmpty());
    assertEquals(1, response.size());
    MonthlyRemittance result = response.get(0);
    assertEquals(BillableUsage.BillingProvider.RED_HAT.value(), result.getBillingProvider());
    assertEquals("org123", result.getOrgId());
    assertEquals(12, result.getRemittedValue());
  }

  @Test
  void testFilterByBillingAccountIdAndOrgId() {
    var response =
        controller.getRemittances(
            BillableUsageRemittanceFilter.builder()
                .orgId("org345")
                .billingAccountId("org345_product3_ba")
                .build());
    assertFalse(response.isEmpty());
    assertEquals(1, response.size());
    MonthlyRemittance result = response.get(0);
    assertEquals("org345_product3_ba", result.getBillingAccountId());
    assertEquals("org345", result.getOrgId());
    assertEquals(BillableUsage.BillingProvider.AZURE.value(), result.getBillingProvider());
    assertEquals(4, result.getRemittedValue());
    assertEquals(BillableUsage.Status.FAILED.value(), result.getRemittanceStatus());
  }

  @Test
  void testResetRemittanceValueForCriteria() {
    int remittancePresent =
        controller.resetBillableUsageRemittance(
            "rosa",
            clock.startOfCurrentMonth().minusDays(1),
            clock.startOfCurrentMonth().plusDays(1),
            Set.of("1234", "5678"),
            null);
    int remittanceNotPresent =
        controller.resetBillableUsageRemittance(
            "rosa",
            clock.startOfCurrentMonth().plusDays(1),
            clock.startOfCurrentMonth().plusDays(2),
            Set.of("1234"),
            null);
    assertEquals(2, remittancePresent);
    assertEquals(0, remittanceNotPresent);
  }

  @Transactional
  @Test
  void testDeleteDataForOrg() {
    givenRemittanceForOrgId("org1");
    givenRemittanceForOrgId("org2");
    controller.deleteDataForOrg("org1");
    var remittances = remittanceRepo.listAll();
    assertFalse(remittances.stream().anyMatch(r -> r.getOrgId().equals("org1")));
    assertTrue(remittances.stream().anyMatch(r -> r.getOrgId().equals("org2")));
  }

  @Test
  void testProcessRetries() {
    String orgId = "testProcessRetriesOrg123";
    givenRemittanceWithOldRetryAfter(orgId);

    controller.processRetries(OffsetDateTime.now());

    // verify remittance has been sent
    verify(billingProducer).produce(argThat(b -> b.getOrgId().equals(orgId)));
    // verify retry after is reset
    var found =
        remittanceRepo.listAll().stream().filter(b -> b.getOrgId().equals(orgId)).findFirst();
    assertTrue(found.isPresent());
    assertNull(found.get().getRetryAfter());
  }

  @Test
  @Transactional
  void testProcessRetriesAppliesBillingFactor() {
    String orgId = "testProcessRetriesOrg123";
    var remittance =
        remittance(
            orgId,
            "product",
            BillableUsage.BillingProvider.AWS,
            20.0,
            clock.startOfCurrentMonth(),
            RemittanceStatus.RETRYABLE);
    remittance.setRetryAfter(clock.now().minusMonths(30));
    remittanceRepo.persistAndFlush(remittance);

    setSubscriptionDefinitionRegistry(mockSubscriptionDefinitionRegistry);
    stubSubscriptionDefinition(remittance.getProductId(), remittance.getMetricId(), .25);

    controller.processRetries(OffsetDateTime.now());

    ArgumentCaptor<BillableUsage> billableUsageArgumentCaptor =
        ArgumentCaptor.forClass(BillableUsage.class);
    verify(billingProducer).produce(billableUsageArgumentCaptor.capture());
    // Billable Usage value should be remitted_pending_value * billing_factor (20 * .25)
    assertEquals(5, billableUsageArgumentCaptor.getValue().getValue());
  }

  @Test
  @Transactional
  void testProcessRetriesAppliesNoBillingFactor() {
    String orgId = "testProcessRetriesOrg123";
    var remittance =
        remittance(
            orgId,
            "product",
            BillableUsage.BillingProvider.AWS,
            20.0,
            clock.startOfCurrentMonth(),
            RemittanceStatus.RETRYABLE);
    remittance.setRetryAfter(clock.now().minusMonths(30));
    remittanceRepo.persistAndFlush(remittance);

    setSubscriptionDefinitionRegistry(mockSubscriptionDefinitionRegistry);
    stubSubscriptionDefinition(remittance.getProductId(), remittance.getMetricId(), 1);

    controller.processRetries(OffsetDateTime.now());

    ArgumentCaptor<BillableUsage> billableUsageArgumentCaptor =
        ArgumentCaptor.forClass(BillableUsage.class);
    verify(billingProducer).produce(billableUsageArgumentCaptor.capture());
    // Billable Usage value should be remitted_pending_value * billing_factor (20 * 1)
    assertEquals(20, billableUsageArgumentCaptor.getValue().getValue());
  }

  @Test
  void testProcessRetriesInPages() {
    String orgId = "testProcessRetriesInPages";
    for (var i = 0; i < REMITTANCES_BATCH_SIZE * 2; i++) {
      givenRemittanceWithOldRetryAfter(orgId + i);
    }

    controller.processRetries(OffsetDateTime.now());

    // verify remittance has been sent
    verify(billingProducer, times(REMITTANCES_BATCH_SIZE * 2)).produce(any());
    // verify retry after is reset
    var remittances =
        remittanceRepo.listAll().stream().filter(b -> b.getOrgId().startsWith(orgId)).toList();
    assertFalse(remittances.isEmpty());
    assertTrue(remittances.stream().allMatch(u -> u.getRetryAfter() == null));
  }

  @Test
  void testProcessRetriesShouldRestoreStateIfSendFails() {
    String orgId = "testProcessRetriesOrg123";
    givenRemittanceWithOldRetryAfter(orgId);
    doThrow(RuntimeException.class).when(billingProducer).produce(any(BillableUsage.class));
    OffsetDateTime retryAfter = OffsetDateTime.now();

    assertThrows(RuntimeException.class, () -> controller.processRetries(retryAfter));

    // verify retry after is set
    var found =
        remittanceRepo.listAll().stream().filter(b -> b.getOrgId().equals(orgId)).findFirst();
    assertTrue(found.isPresent());
    assertNotNull(found.get().getRetryAfter());
  }

  @Transactional
  void givenRemittanceWithOldRetryAfter(String orgId) {
    var remittance =
        remittance(
            orgId,
            "product",
            BillableUsage.BillingProvider.AZURE,
            4.0,
            clock.startOfCurrentMonth(),
            RemittanceStatus.RETRYABLE);
    remittance.setRetryAfter(clock.now().minusMonths(30));
    remittanceRepo.persistAndFlush(remittance);
  }

  private void givenRemittanceForOrgId(String orgId) {
    remittanceRepo.persist(
        BillableUsageRemittanceEntity.builder()
            .usage(BillableUsage.Usage.PRODUCTION.value())
            .orgId(orgId)
            .billingProvider(BillableUsage.BillingProvider.AZURE.value())
            .billingAccountId(String.format("%s_%s_ba", orgId, PRODUCT_ID))
            .productId(PRODUCT_ID)
            .accumulationPeriod("mm-DD")
            .sla(BillableUsage.Sla.PREMIUM.value())
            .metricId("Cores")
            .remittancePendingDate(OffsetDateTime.now())
            .remittedPendingValue(2.0)
            .build());
  }

  private BillableUsageRemittanceEntity remittance(
      String orgId,
      String productId,
      BillableUsage.BillingProvider billingProvider,
      Double value,
      OffsetDateTime remittanceDate,
      RemittanceStatus remittanceStatus) {
    return BillableUsageRemittanceEntity.builder()
        .usage(BillableUsage.Usage.PRODUCTION.value())
        .orgId(orgId)
        .billingProvider(billingProvider.value())
        .billingAccountId(String.format("%s_%s_ba", orgId, productId))
        .productId(productId)
        .sla(BillableUsage.Sla.PREMIUM.value())
        .metricId("Instance-hours")
        .accumulationPeriod(AccumulationPeriodFormatter.toMonthId(remittanceDate))
        .remittancePendingDate(remittanceDate)
        .remittedPendingValue(value)
        .status(remittanceStatus)
        .build();
  }

  private void stubSubscriptionDefinition(String tag, String metricId, double billingFactor) {
    metricId = metricId.replace('_', '-');
    var variant = Variant.builder().tag(tag).build();
    var awsMetric =
        com.redhat.swatch.configuration.registry.Metric.builder()
            .awsDimension(AWS_METRIC_ID)
            .billingFactor(billingFactor)
            .id(metricId)
            .build();
    var subscriptionDefinition =
        SubscriptionDefinition.builder()
            .variants(Set.of(variant))
            .metrics(Set.of(awsMetric))
            .build();
    variant.setSubscription(subscriptionDefinition);
    when(mockSubscriptionDefinitionRegistry.getSubscriptions())
        .thenReturn(List.of(subscriptionDefinition));
  }
}
