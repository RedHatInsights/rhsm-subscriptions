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
package com.redhat.swatch.hbi.events.resources;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.redhat.swatch.hbi.events.services.HbiEventOutboxService;
import com.redhat.swatch.hbi.model.OutboxRecord;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.time.OffsetDateTime;
import org.apache.http.HttpStatus;
import org.candlepin.subscriptions.json.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class InternalResourceTest {

  @InjectMock HbiEventOutboxService outboxService;

  @BeforeEach
  public void setup() {
    when(outboxService.createOutboxRecord(any())).thenReturn(new OutboxRecord());
  }

  @Test
  void testCreateOutboxRecordWhenRequestDoesNotHaveOrgIdThenReturnsBadRequest() {
    Event request = new Event();

    given()
        .contentType(ContentType.JSON)
        .body(request)
        .when()
        .post("/api/swatch-metrics-hbi/internal/outbox")
        .then()
        .statusCode(HttpStatus.SC_BAD_REQUEST);

    verifyNoInteractions(outboxService);
  }

  @Test
  void testCreateOutboxRecordInvokesService() {
    Event request = new Event();
    request.setOrgId("org123");
    request.setEventSource("HBI_HOST");
    request.setInstanceId("test-instance-id");
    request.setEventType("test");
    request.setServiceType("RHEL System");
    request.setTimestamp(OffsetDateTime.now());

    given()
        .contentType(ContentType.JSON)
        .body(request)
        .when()
        .post("/api/swatch-metrics-hbi/internal/outbox")
        .then()
        .statusCode(HttpStatus.SC_OK);

    verify(outboxService, times(1)).createOutboxRecord(any());
  }
}
