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

import static com.redhat.swatch.common.security.PskHeaderAuthenticationMechanism.PSK_HEADER;
import static com.redhat.swatch.hbi.events.resources.InternalResource.SYNCHRONOUS_REQUEST_HEADER;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.redhat.swatch.common.security.SecurityConfiguration;
import com.redhat.swatch.hbi.events.configuration.ApplicationConfiguration;
import com.redhat.swatch.hbi.events.exception.api.ErrorCode;
import com.redhat.swatch.hbi.events.exception.api.SynchronousOutboxFlushException;
import com.redhat.swatch.hbi.events.exception.api.SynchronousRequestsNotEnabledException;
import com.redhat.swatch.hbi.events.services.HbiEventOutboxService;
import com.redhat.swatch.hbi.model.Error;
import com.redhat.swatch.hbi.model.FlushResponse;
import com.redhat.swatch.hbi.model.FlushResponse.StatusEnum;
import com.redhat.swatch.hbi.model.OutboxRecord;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import org.apache.http.HttpStatus;
import org.candlepin.subscriptions.json.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class InternalResourceTest {

  static final String FLUSH_URL = "/api/swatch-metrics-hbi/internal/rpc/outbox/flush";

  @InjectMock HbiEventOutboxService outboxService;
  @InjectMock SecurityConfiguration securityConfiguration;
  @Inject ApplicationConfiguration config;

  private final Map<String, String> headers = new HashMap<>();

  @BeforeEach
  void setup() {
    givenTestApisEnabled();
    withValidPskHeader();
    withSynchronousRequestsEnabled(false);
    when(outboxService.createOutboxRecord(any())).thenReturn(new OutboxRecord());
    when(outboxService.flushOutboxRecords()).thenReturn(1L);
  }

  @Test
  void testFlushOutboxAsyncWithNoHeader() {
    assertSuccessfulAsyncFlushResponse();
  }

  @Test
  void testFlushOutboxAsyncWithFalseValueInHeader() {
    withSynchronousRequestHeader(false);
    assertSuccessfulAsyncFlushResponse();
  }

  @Test
  void testFlushOutboxSynchronously() {
    withSynchronousRequestsEnabled(true);
    withSynchronousRequestHeader(true);
    assertSuccessfulSynchronousFlushResponse(1L);
  }

  @Test
  void testFlushOutboxReportsErrorDuringSynchronousFlush() {
    withSynchronousRequestsEnabled(true);
    withSynchronousRequestHeader(true);
    when(outboxService.flushOutboxRecords()).thenThrow(new RuntimeException("Forced!"));

    Error error =
        flushOutbox()
            .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR)
            .extract()
            .body()
            .as(Error.class);

    assertEquals(ErrorCode.SYNCHRONOUS_OUTBOX_FLUSH_ERROR.getCode(), error.getCode());
    assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, Integer.parseInt(error.getStatus()));
    assertEquals(SynchronousOutboxFlushException.MESSAGE, error.getTitle());
    assertEquals("Forced!", error.getDetail());
  }

  @Test
  void testFlushOutboxAsyncDeniedWithInvalidPsk() {
    withInvalidPskHeader();
    flushOutbox().statusCode(HttpStatus.SC_UNAUTHORIZED);
  }

  @Test
  void testFlushOutboxSynchronouslyDeniedWithInvalidPsk() {
    withInvalidPskHeader();
    withSynchronousRequestsEnabled(true);
    withSynchronousRequestHeader(true);
    flushOutbox().statusCode(HttpStatus.SC_UNAUTHORIZED);
  }

  @Test
  void testFlushOutboxSynchronouslyFailsWhenNotEnabled() {
    withSynchronousRequestHeader(true);
    Error error =
        flushOutbox().statusCode(HttpStatus.SC_BAD_REQUEST).extract().body().as(Error.class);
    assertEquals(ErrorCode.SYNCHRONOUS_OUTBOX_FLUSH_DISABLED.getCode(), error.getCode());
    assertEquals(HttpStatus.SC_BAD_REQUEST, Integer.parseInt(error.getStatus()));
    assertEquals(SynchronousRequestsNotEnabledException.MESSAGE, error.getTitle());
    assertEquals(ErrorCode.SYNCHRONOUS_OUTBOX_FLUSH_DISABLED.getDescription(), error.getDetail());
  }

  @Test
  void testCreateOutboxRecordWhenRequestDoesNotHaveOrgIdThenReturnsBadRequest() {
    Event request = new Event();

    whenCreateOutboxEvent(request).statusCode(HttpStatus.SC_BAD_REQUEST);

    verifyNoInteractions(outboxService);
  }

  @Test
  void testCreateOutboxRecordInvokesService() {
    Event request = givenValidRequest();

    whenCreateOutboxEvent(request).statusCode(HttpStatus.SC_OK);

    verify(outboxService, times(1)).createOutboxRecord(any());
  }

  @Test
  void testCreateOutboxRecordWithoutAuthHeader() {
    given()
        .contentType(ContentType.JSON)
        .body(givenValidRequest())
        .when()
        .post("/api/swatch-metrics-hbi/internal/outbox")
        .then()
        .statusCode(HttpStatus.SC_UNAUTHORIZED);
  }

  @Test
  void testAccessNotAllowed() {
    givenTestApisDisabled();
    whenCreateOutboxEvent(givenValidRequest()).statusCode(HttpStatus.SC_FORBIDDEN);
  }

  private void givenTestApisDisabled() {
    when(securityConfiguration.isTestApisEnabled()).thenReturn(false);
  }

  private void givenTestApisEnabled() {
    when(securityConfiguration.isTestApisEnabled()).thenReturn(true);
  }

  private Event givenValidRequest() {
    Event request = new Event();
    request.setOrgId("org123");
    request.setEventSource("HBI_HOST");
    request.setInstanceId("test-instance-id");
    request.setEventType("test");
    request.setServiceType("RHEL System");
    request.setTimestamp(OffsetDateTime.now());
    return request;
  }

  private ValidatableResponse whenCreateOutboxEvent(Event request) {
    return given()
        .header(PSK_HEADER, "placeholder")
        .contentType(ContentType.JSON)
        .body(request)
        .when()
        .post("/api/swatch-metrics-hbi/internal/outbox")
        .then();
  }

  private void withSynchronousRequestsEnabled(boolean enabled) {
    config.setSynchronousOperationsEnabled(enabled);
  }

  private void withSynchronousRequestHeader(boolean isSyncRequest) {
    headers.put(SYNCHRONOUS_REQUEST_HEADER, Boolean.toString(isSyncRequest));
  }

  private void withValidPskHeader() {
    headers.put(PSK_HEADER, "placeholder");
  }

  private void withInvalidPskHeader() {
    headers.put(PSK_HEADER, "invalid");
  }

  private ValidatableResponse flushOutbox() {
    return given().contentType(ContentType.JSON).headers(headers).when().put(FLUSH_URL).then();
  }

  private void assertSuccessfulAsyncFlushResponse() {
    assertSuccessfulFlushResponse(true, StatusEnum.STARTED, null);
  }

  private void assertSuccessfulSynchronousFlushResponse(long expectedFlushCount) {
    assertSuccessfulFlushResponse(false, StatusEnum.SUCCESS, expectedFlushCount);
  }

  private void assertSuccessfulFlushResponse(
      boolean expectAsyncFlush,
      FlushResponse.StatusEnum expectedFlushStatus,
      Long expectedFlushCount) {
    FlushResponse response =
        flushOutbox().statusCode(HttpStatus.SC_OK).extract().body().as(FlushResponse.class);
    assertEquals(expectAsyncFlush, response.getAsync());
    assertEquals(expectedFlushStatus, response.getStatus());
    assertEquals(expectedFlushCount, response.getCount());
  }
}
