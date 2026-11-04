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
package com.redhat.swatch.gcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.gax.rpc.UnavailableException;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import jakarta.inject.Inject;
import java.io.IOException;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregate;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(value = ServiceControlStub.class, restrictToAnnotatedClass = true)
class GcpUsageSubmissionTest {
  @Inject ObjectMapper mapper;
  @Inject GcpUsageService service;
  @Inject GcpBillableUsageAggregateConsumer consumer;

  @Inject
  @Connector("smallrye-in-memory")
  InMemoryConnector connector;

  @BeforeEach
  void reset() {
    ServiceControlStub.paths.clear();
    ServiceControlStub.bodies.clear();
    ServiceControlStub.checkResponse = "{}";
    ServiceControlStub.reportResponse = "{}";
    ServiceControlStub.checkStatus = 200;
    connector.sink("billable-usage-status-out").clear();
  }

  private BillableUsageAggregate usage() throws IOException {
    return mapper.readValue(GcpUsageResourceTest.example(), BillableUsageAggregate.class);
  }

  @Test
  void checksBeforeReportingAndPublishesCorrelatedSuccess() throws IOException {
    var usage = usage();
    consumer.process(usage);
    assertEquals(2, ServiceControlStub.paths.size());
    assertEquals(
        "/v1/services/example-product.gcpmarketplace.example.com:check",
        ServiceControlStub.paths.get(0));
    assertEquals(
        "/v1/services/example-product.gcpmarketplace.example.com:report",
        ServiceControlStub.paths.get(1));
    assertEquals(
        mapper.readTree(ServiceControlStub.bodies.get(0)).get("operation"),
        mapper.readTree(ServiceControlStub.bodies.get(1)).get("operations").get(0));
    var status =
        connector
            .<BillableUsageAggregate>sink("billable-usage-status-out")
            .received()
            .getFirst()
            .getPayload();
    assertEquals(usage.getAggregateId(), status.getAggregateId());
    assertEquals(usage.getRemittanceUuids(), status.getRemittanceUuids());
    assertEquals(BillableUsage.Status.SUCCEEDED, status.getStatus());
    assertNotNull(status.getBilledOn());
  }

  @Test
  void checkErrorsPreventReportEvenWithHttp200() throws IOException {
    ServiceControlStub.checkResponse = "{\"checkErrors\":[{\"code\":\"BILLING_DISABLED\"}]}";
    assertThrows(IllegalStateException.class, () -> service.submit(usage()));
    assertEquals(1, ServiceControlStub.paths.size());
  }

  @Test
  void reportErrorsProduceFailedStatusEvenWithHttp200() throws IOException {
    ServiceControlStub.reportResponse = "{\"reportErrors\":[{\"operationId\":\"example\"}]}";
    consumer.process(usage());
    var status =
        connector
            .<BillableUsageAggregate>sink("billable-usage-status-out")
            .received()
            .getFirst()
            .getPayload();
    assertEquals(BillableUsage.Status.FAILED, status.getStatus());
    assertNull(status.getBilledOn());
  }

  @Test
  void httpFailurePreventsReport() {
    ServiceControlStub.checkStatus = 503;
    assertThrows(UnavailableException.class, () -> service.submit(usage()));
    assertEquals(1, ServiceControlStub.paths.size());
  }
}
