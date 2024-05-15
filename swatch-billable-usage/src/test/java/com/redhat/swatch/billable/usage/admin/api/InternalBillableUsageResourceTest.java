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

import static com.redhat.swatch.billable.usage.configuration.Channels.BILLABLE_USAGE_OUT;
import static com.redhat.swatch.billable.usage.configuration.Channels.ENABLED_ORGS;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redhat.swatch.billable.usage.configuration.ApplicationConfiguration;
import com.redhat.swatch.billable.usage.data.BillableUsageRemittanceEntity;
import com.redhat.swatch.billable.usage.data.BillableUsageRemittanceRepository;
import com.redhat.swatch.billable.usage.kafka.InMemoryMessageBrokerKafkaResource;
import com.redhat.swatch.billable.usage.model.EnabledOrgsRequest;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.apache.http.HttpStatus;
import org.awaitility.Awaitility;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(
    value = InMemoryMessageBrokerKafkaResource.class,
    restrictToAnnotatedClass = true)
class InternalBillableUsageResourceTest {

  private static final String ORG_ID = "org123";
  private static final String PRODUCT_ID = "rosa";

  @InjectMock ApplicationConfiguration configuration;
  @InjectSpy BillableUsageRemittanceRepository remittanceRepository;
  @Inject @Any InMemoryConnector connector;

  InMemorySink<EnabledOrgsRequest> enabledOrgsSink;
  InMemorySink<BillableUsage> billableUsageSink;

  @BeforeEach
  void setUp() {
    enabledOrgsSink = connector.sink(ENABLED_ORGS);
    enabledOrgsSink.clear();
    billableUsageSink = connector.sink(BILLABLE_USAGE_OUT);
    billableUsageSink.clear();
  }

  @Test
  void testProcessRetries() {
    var entity = givenRemittanceForOrgId(ORG_ID);
    given()
        .post("/api/swatch-billable-usage/internal/rpc/remittance/processRetries")
        .then()
        .statusCode(HttpStatus.SC_OK);
    Awaitility.await().untilAsserted(() -> assertEquals(1, billableUsageSink.received().size()));
    assertNull(remittanceRepository.findById(entity.getUuid()).getRetryAfter());
  }

  @Test
  void testDeleteRemittancesAssociatedWithOrg() {
    given()
        .delete("/api/swatch-billable-usage/internal/rpc/remittance/" + ORG_ID)
        .then()
        .statusCode(HttpStatus.SC_OK);
    verify(remittanceRepository).deleteByOrgId(ORG_ID);
  }

  @Test
  void testPurgeRemittancesWhenNoPolicy() {
    when(configuration.getRemittanceRetentionPolicyDuration()).thenReturn(null);
    whenPurgeRemittances();
    assertEquals(0, enabledOrgsSink.received().size());
  }

  @Test
  void testPurgeRemittancesWhenPolicyIsConfigured() {
    when(configuration.getRemittanceRetentionPolicyDuration()).thenReturn(Duration.ofDays(1));
    whenPurgeRemittances();
    assertEquals(1, enabledOrgsSink.received().size());
  }

  @Transactional
  BillableUsageRemittanceEntity givenRemittanceForOrgId(String orgId) {
    var entity =
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
            .retryAfter(OffsetDateTime.now().minusDays(1))
            .build();
    remittanceRepository.persist(entity);
    return entity;
  }

  private static void whenPurgeRemittances() {
    given()
        .post("/api/swatch-billable-usage/internal/rpc/remittance/purge")
        .then()
        .statusCode(HttpStatus.SC_OK);
  }
}
