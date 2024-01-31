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
package org.candlepin.subscriptions.task.queue.kafka;

import com.redhat.swatch.clients.export.api.client.ApiException;
import java.util.stream.Stream;
import org.candlepin.subscriptions.db.SubscriptionRepository;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.rbac.RbacService;
import org.candlepin.subscriptions.subscription.ExportSubscriptionListener;
import org.candlepin.subscriptions.test.ExtendWithEmbeddedKafka;
import org.candlepin.subscriptions.test.ExtendWithExportServiceWireMock;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles(value = {"worker", "kafka-queue", "api", "test-inventory", "capacity-ingress"})
public class ExportPerformanceMemoryTest extends ExtendWithExportServiceWireMock
    implements ExtendWithEmbeddedKafka {
  @Autowired ExportSubscriptionListener listener;

  @MockBean RbacService rbacService;
  @MockBean SubscriptionRepository repository;
  static final String EXPORTMSG =
      """
                  {
                      "$schema": "https://console.redhat.com/api/schemas/events/v1/events.json",
                      "id": "2356aad8-de45-4ba0-80bd-4637763ad115",
                      "source": "urn:redhat:source:console:app:export-service",
                      "specversion": "1.0",
                      "type": "com.redhat.console.export-service.request",
                      "subject": "urn:redhat:subject:export-service:request:2e3d7746-2cf2-441e-84fe-cf28863d22ae",
                      "time": "2023-01-01T00:00:00Z",
                      "redhatorgid": "org123",
                      "redhatconsolebundle": "rhel",
                      "dataschema": "https://console.redhat.com/api/schemas/apps/export-service/v1/resource-request.json",
                      "data": {
                          "resource_request": {
                              "uuid": "b24c269d-33d6-410e-8808-c71c9635e84f",
                              "export_request_uuid": "2e3d7746-2cf2-441e-84fe-cf28863d22ae",
                              "application": "subscriptions",
                              "format": "csv",
                              "resource": "subscriptions",
                              "x-rh-identity": "base64-encoded-identity",
                              "filters": {
                                  "filter1": "value1",
                                  "filter2": "value2"
                              }
                          }
                      }
                  }
                  """;

  @Test
  public void givenValidExportMsg() throws ApiException {
    Mockito.when(repository.streamAll(Mockito.any()))
        .thenReturn(Stream.generate(this::createTestSubs).limit(10000));
    // stream to client with file 2 G should be good
  }

  private Subscription createTestSubs() {
    Subscription subscription = new Subscription();
    subscription.setBillingProviderId("bananas");
    subscription.setSubscriptionId("subId");
    subscription.setOrgId("orgId");
    return subscription;
  }
}
