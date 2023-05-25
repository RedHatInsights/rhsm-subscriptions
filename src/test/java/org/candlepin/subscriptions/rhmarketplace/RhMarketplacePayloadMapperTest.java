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
package org.candlepin.subscriptions.rhmarketplace;

import static org.candlepin.subscriptions.utilization.api.model.ProductId.OPENSHIFT_DEDICATED_METRICS;
import static org.candlepin.subscriptions.utilization.api.model.ProductId.OPENSHIFT_METRICS;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.ParameterizedTest.DEFAULT_DISPLAY_NAME;
import static org.junit.jupiter.params.ParameterizedTest.DISPLAY_NAME_PLACEHOLDER;
import static org.mockito.Mockito.*;

import com.redhat.swatch.clients.internal.subscriptions.api.client.ApiException;
import com.redhat.swatch.clients.internal.subscriptions.api.model.RhmUsageContext;
import com.redhat.swatch.clients.internal.subscriptions.api.resources.InternalSubscriptionsApi;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.candlepin.subscriptions.json.BillableUsage;
import org.candlepin.subscriptions.json.BillableUsage.BillingProvider;
import org.candlepin.subscriptions.json.BillableUsage.Sla;
import org.candlepin.subscriptions.json.BillableUsage.Uom;
import org.candlepin.subscriptions.json.BillableUsage.Usage;
import org.candlepin.subscriptions.json.TallyMeasurement;
import org.candlepin.subscriptions.registry.TagProfile;
import org.candlepin.subscriptions.rhmarketplace.api.model.UsageEvent;
import org.candlepin.subscriptions.rhmarketplace.api.model.UsageMeasurement;
import org.candlepin.subscriptions.user.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.backoff.NoBackOffPolicy;
import org.springframework.retry.listener.RetryListenerSupport;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

@ExtendWith(MockitoExtension.class)
class RhMarketplacePayloadMapperTest {

  @Mock TagProfile tagProfile;
  @Mock InternalSubscriptionsApi subscriptionsApi;

  @Mock AccountService accountService;

  RhMarketplacePayloadMapper rhMarketplacePayloadMapper;

  @BeforeEach
  void init() {
    RetryTemplate retry = new RetryTemplate();
    retry.setBackOffPolicy(new NoBackOffPolicy());

    rhMarketplacePayloadMapper =
        new RhMarketplacePayloadMapper(tagProfile, accountService, subscriptionsApi, retry);

    // Tell Mockito not to complain if some of these mocks aren't used in a particular test
    lenient()
        .when(
            tagProfile.rhmMetricIdForTagAndUom(
                OPENSHIFT_METRICS.toString(), TallyMeasurement.Uom.CORES))
        .thenReturn("redhat.com:openshift:cpu_hour");

    lenient()
        .when(
            tagProfile.rhmMetricIdForTagAndUom(
                OPENSHIFT_DEDICATED_METRICS.toString(), TallyMeasurement.Uom.CORES))
        .thenReturn(RhMarketplacePayloadMapper.OPENSHIFT_DEDICATED_4_CPU_HOUR);

    lenient()
        .when(tagProfile.isProductPAYGEligible(OPENSHIFT_DEDICATED_METRICS.toString()))
        .thenReturn(true);
    lenient().when(tagProfile.isProductPAYGEligible(OPENSHIFT_METRICS.toString())).thenReturn(true);
  }

  @Test
  void testProduceUsageEvents() throws Exception {
    RhmUsageContext rhmUsageContext = new RhmUsageContext();
    rhmUsageContext.setRhSubscriptionId("PLACEHOLDER");
    when(subscriptionsApi.getRhmUsageContext(
            any(String.class),
            any(OffsetDateTime.class),
            any(String.class),
            any(String.class),
            any(String.class),
            any(String.class)))
        .thenReturn(rhmUsageContext);

    var snapshotDateLong = 1616100754L;

    OffsetDateTime snapshotDate =
        OffsetDateTime.ofInstant(Instant.ofEpochMilli(snapshotDateLong), ZoneId.of("UTC"));

    String account = "test123";
    String orgId = "org123";
    var usage =
        new BillableUsage()
            .withId(UUID.fromString("c204074d-626f-4272-aa05-b6d69d6de16a"))
            .withAccountNumber(account)
            .withProductId("OpenShift-metrics")
            .withSnapshotDate(snapshotDate)
            .withUsage(Usage.PRODUCTION)
            .withUom(Uom.CORES)
            .withValue(36.0)
            .withSla(Sla.PREMIUM)
            .withBillingProvider(BillingProvider.RED_HAT)
            .withBillingAccountId("sellerAccountId");

    when(accountService.lookupOrgId(account)).thenReturn(orgId);

    var usageMeasurement =
        new UsageMeasurement().value(36.0).metricId("redhat.com:openshift:cpu_hour");
    var expected =
        new UsageEvent()
            .start(snapshotDateLong)
            .end(1619700754L)
            .eventId("c204074d-626f-4272-aa05-b6d69d6de16a")
            .measuredUsage(List.of(usageMeasurement));

    UsageEvent actual = rhMarketplacePayloadMapper.produceUsageEvent(usage);

    assertEquals(expected.getEventId(), actual.getEventId());
    assertEquals(expected.getMeasuredUsage(), actual.getMeasuredUsage());
    assertEquals(expected.getStart(), actual.getStart());
    assertEquals(expected.getEnd(), actual.getEnd());
  }

  @Test
  void testProducesNullUsageRequestWhenSubscriptionIdNotFound() throws Exception {
    RhmUsageContext rhmUsageContext = new RhmUsageContext();
    when(subscriptionsApi.getRhmUsageContext(
            any(String.class),
            any(OffsetDateTime.class),
            any(String.class),
            any(String.class),
            any(String.class),
            any(String.class)))
        .thenReturn(rhmUsageContext);

    var snapshotDateLong = 1616100754L;

    OffsetDateTime snapshotDate =
        OffsetDateTime.ofInstant(Instant.ofEpochMilli(snapshotDateLong), ZoneId.of("UTC"));

    String account = "test123";
    String orgId = "org123";
    var usage =
        new BillableUsage()
            .withId(UUID.fromString("c204074d-626f-4272-aa05-b6d69d6de16a"))
            .withAccountNumber(account)
            .withProductId("OpenShift-metrics")
            .withSnapshotDate(snapshotDate)
            .withUsage(Usage.PRODUCTION)
            .withUom(Uom.CORES)
            .withValue(36.0)
            .withSla(Sla.PREMIUM)
            .withBillingProvider(BillingProvider.RED_HAT)
            .withBillingAccountId("sellerAccountId");

    when(accountService.lookupOrgId(account)).thenReturn(orgId);

    assertNull(rhMarketplacePayloadMapper.produceUsageEvent(usage));
    assertTrue(rhMarketplacePayloadMapper.createUsageRequest(usage).getData().isEmpty());
  }

  @ParameterizedTest(name = DISPLAY_NAME_PLACEHOLDER + " " + DEFAULT_DISPLAY_NAME)
  @MethodSource("generateIsUsageRHMarketplaceEligibleData")
  void testIsUsageRHMarketplaceEligible(BillableUsage usage, boolean isEligible) {
    boolean actual = rhMarketplacePayloadMapper.isUsageRHMarketplaceEligible(usage);
    assertEquals(isEligible, actual);
  }

  static Stream<Arguments> generateIsUsageRHMarketplaceEligibleData() {

    Arguments eligibleRedHatBillingProvider =
        Arguments.of(
            new BillableUsage()
                .withProductId("OpenShift-metrics")
                .withUsage(Usage.PRODUCTION)
                .withSla(Sla.PREMIUM)
                .withBillingProvider(BillingProvider.RED_HAT),
            true);

    Arguments notEligibleNullBillingProvider =
        Arguments.of(
            new BillableUsage()
                .withProductId("OpenShift-metrics")
                .withUsage(Usage.PRODUCTION)
                .withSla(Sla.PREMIUM)
                .withBillingProvider(null),
            false);

    Arguments notEligibleAWSBillingProvider =
        Arguments.of(
            new BillableUsage()
                .withProductId("OpenShift-metrics")
                .withUsage(Usage.PRODUCTION)
                .withSla(Sla.PREMIUM)
                .withBillingProvider(BillingProvider.AWS),
            false);

    Arguments notEligibleAzureBillingProvider =
        Arguments.of(
            new BillableUsage()
                .withProductId("OpenShift-metrics")
                .withUsage(Usage.PRODUCTION)
                .withSla(Sla.PREMIUM)
                .withBillingProvider(BillingProvider.AZURE),
            false);

    Arguments notEligibleOracleBillingProvider =
        Arguments.of(
            new BillableUsage()
                .withProductId("OpenShift-metrics")
                .withUsage(Usage.PRODUCTION)
                .withSla(Sla.PREMIUM)
                .withBillingProvider(BillingProvider.ORACLE),
            false);

    Arguments notEligibleGcpBillingProvider =
        Arguments.of(
            new BillableUsage()
                .withProductId("OpenShift-metrics")
                .withUsage(Usage.PRODUCTION)
                .withSla(Sla.PREMIUM)
                .withBillingProvider(BillingProvider.GCP),
            false);

    Arguments notEligableDefaultBillableUsage = Arguments.of(new BillableUsage(), false);

    return Stream.of(
        eligibleRedHatBillingProvider,
        notEligibleNullBillingProvider,
        notEligibleAWSBillingProvider,
        notEligibleAzureBillingProvider,
        notEligibleOracleBillingProvider,
        notEligibleGcpBillingProvider,
        notEligableDefaultBillableUsage);
  }

  @Test
  void verifyLookupIsRetriedOnFailure() throws Exception {
    int expectedMaxAttempts = 5;
    RetryTemplate retry = new RetryTemplate();
    retry.setBackOffPolicy(new NoBackOffPolicy());
    retry.setRetryPolicy(new SimpleRetryPolicy(expectedMaxAttempts));

    RetryTestSupport retrySupport = new RetryTestSupport();
    retry.registerListener(retrySupport);

    when(subscriptionsApi.getRhmUsageContext(
            any(String.class),
            any(OffsetDateTime.class),
            any(String.class),
            any(String.class),
            any(String.class),
            any(String.class)))
        .thenThrow(ApiException.class);

    RhMarketplacePayloadMapper mapper =
        new RhMarketplacePayloadMapper(tagProfile, accountService, subscriptionsApi, retry);

    BillableUsage billableUsage =
        new BillableUsage()
            .withOrgId("org")
            .withSnapshotDate(OffsetDateTime.now())
            .withAccountNumber("account")
            .withProductId("product")
            .withSla(Sla.PREMIUM)
            .withUsage(Usage.PRODUCTION);

    assertThrows(
        RhmUsageContextLookupException.class, () -> mapper.lookupRhmUsageContext(billableUsage));

    assertEquals(expectedMaxAttempts, retrySupport.getRetryCount());
  }

  /**
   * A retry test support class that will increment a counter whenever an error occurs and the retry
   * logic is run.
   */
  private class RetryTestSupport extends RetryListenerSupport {

    private int retryCount = 0;

    @Override
    public <T, E extends Throwable> void onError(
        RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
      retryCount++;
    }

    public int getRetryCount() {
      return retryCount;
    }
  }
}
