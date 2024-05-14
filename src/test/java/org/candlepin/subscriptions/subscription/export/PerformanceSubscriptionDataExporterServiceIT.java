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

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.cloud.event.apps.exportservice.v1.Format;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.SubscriptionMeasurementKey;
import org.candlepin.subscriptions.export.BaseDataExporterServiceTest;
import org.candlepin.subscriptions.test.ExtendWithSwatchDatabase;
import org.hibernate.SessionFactory;
import org.hibernate.StatelessSession;
import org.hibernate.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.shaded.org.awaitility.Awaitility;

@Disabled
@SpringBootTest(useMainMethod = SpringBootTest.UseMainMethod.ALWAYS)
@ActiveProfiles(value = {"kafka-queue", "test-inventory", "capacity-ingress"})
class PerformanceSubscriptionDataExporterServiceIT extends BaseDataExporterServiceTest
    implements ExtendWithSwatchDatabase {

  @Autowired SessionFactory sessionFactory;
  @Autowired ApplicationClock clock;

  @Override
  protected String resourceType() {
    return "subscriptions";
  }

  @BeforeEach
  @Override
  public void setup() {
    super.setup();

    StatelessSession session = sessionFactory.openStatelessSession();
    Transaction tx = session.beginTransaction();
    // According to https://github.com/RedHatInsights/rhsm-subscriptions/pull/3145,
    // the file size limit is reached when having 500k subscriptions, where
    // the following exception "RESTEASY005081: File limit of 50MB has been reached." is thrown.
    // To avoid this exception and un-limit the file size the client can handle,
    // we need to set the system property "dev.resteasy.entity.file.threshold" to "-1".
    // This setting is being done in org.candlepin.subscriptions.BootApplication.
    for (int num = 0; num < 500_000; num++) {
      session.insert(givenSubscriptionWithMeasurement());
    }

    tx.commit();
    session.close();
  }

  @Test
  void testLargeDataDealingWithRestEasyClientFileLimit() {
    givenExportRequestWithPermissions(Format.JSON);
    whenReceiveExportRequest();
    verifyRequestWasSentToExportService();
  }

  private Subscription givenSubscriptionWithMeasurement() {
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
    return subscription;
  }

  @Override
  protected void verifyRequestWasSentToExportService() {
    Awaitility.await()
        .atMost(Duration.ofMinutes(3))
        .untilAsserted(
            () -> {
              var calls =
                  EXPORT_SERVICE_WIRE_MOCK_SERVER.findAll(
                      postRequestedFor(
                          urlEqualTo(
                              String.format(
                                  "/app/export/v1/%s/subscriptions/%s/upload",
                                  request.getData().getResourceRequest().getExportRequestUUID(),
                                  request.getData().getResourceRequest().getUUID()))));
              assertEquals(1, calls.size());
              assertTrue(calls.get(0).getBody().length > 50_000_000);
            });
  }
}
