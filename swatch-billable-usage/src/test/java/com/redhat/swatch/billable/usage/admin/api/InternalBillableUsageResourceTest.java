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
import com.redhat.swatch.billable.usage.data.RemittanceErrorCode;
import com.redhat.swatch.billable.usage.data.RemittanceStatus;
import com.redhat.swatch.billable.usage.kafka.InMemoryMessageBrokerKafkaResource;
import com.redhat.swatch.billable.usage.model.EnabledOrgsRequest;
import com.redhat.swatch.billable.usage.openapi.model.MonthlyRemittance;
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
  void testGetRemittancesReturnsBadRequestWhenNoOrgId() {
    given()
        .queryParam("productId", PRODUCT_ID)
        .get("/api/swatch-billable-usage/internal/remittance/accountRemittances")
        .then()
        .statusCode(HttpStatus.SC_BAD_REQUEST);
  }

  @Test
  void testGetRemittancesReturnsBadRequestWhenWrongDates() {
    given()
        .queryParam("productId", PRODUCT_ID)
        .queryParam("orgId", ORG_ID)
        .queryParam("beginning", OffsetDateTime.now().toString())
        .queryParam("ending", OffsetDateTime.now().minusDays(5).toString())
        .get("/api/swatch-billable-usage/internal/remittance/accountRemittances")
        .then()
        .statusCode(HttpStatus.SC_BAD_REQUEST);
  }

  @Test
  void testGetRemittances() {
    givenRemittanceForOrgId(ORG_ID);
    MonthlyRemittance[] remittances =
        given()
            .queryParam("productId", PRODUCT_ID)
            .queryParam("orgId", ORG_ID)
            .get("/api/swatch-billable-usage/internal/remittance/accountRemittances")
            .as(MonthlyRemittance[].class);
    assertEquals(1, remittances.length);
    assertEquals(ORG_ID, remittances[0].getOrgId());
    assertEquals(RemittanceStatus.FAILED.getValue(), remittances[0].getRemittanceStatus());
    assertEquals(RemittanceErrorCode.UNKNOWN.getValue(), remittances[0].getRemittanceErrorCode());
  }

  @Test
  void testResetBillableUsageRemittance() {
    givenRemittanceForOrgId(ORG_ID);
    given()
        .queryParam("product_id", PRODUCT_ID)
        .queryParam("org_id", ORG_ID)
        .queryParam("start", OffsetDateTime.now().minusDays(5).toString())
        .queryParam("end", OffsetDateTime.now().plusDays(5).toString())
        .put("/api/swatch-billable-usage/internal/rpc/remittance/reset_billable_usage_remittance")
        .then()
        .statusCode(HttpStatus.SC_OK);
  }

  @Test
  void testProcessRetries() {
    var entity = givenRemittanceForOrgId(ORG_ID);
    given()
        .post("/api/swatch-billable-usage/internal/rpc/remittance/processRetries")
        .then()
        .statusCode(HttpStatus.SC_OK);
    Awaitility.await().untilAsserted(() -> assertEquals(2, billableUsageSink.received().size()));
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
            .status(RemittanceStatus.FAILED)
            .errorCode(RemittanceErrorCode.UNKNOWN)
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
