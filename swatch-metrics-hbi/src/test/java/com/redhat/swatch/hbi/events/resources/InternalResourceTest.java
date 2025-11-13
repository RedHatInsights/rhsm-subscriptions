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
import static java.lang.Thread.sleep;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;
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
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.apache.http.HttpStatus;
import org.candlepin.subscriptions.json.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.stubbing.Answer;

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

  static Stream<Arguments> flushLockParameters() {
    return Stream.of(
        arguments(true, true, StatusEnum.SUCCESS, StatusEnum.ALREADY_RUNNING),
        arguments(true, false, StatusEnum.SUCCESS, StatusEnum.STARTED),
        arguments(false, true, StatusEnum.STARTED, StatusEnum.ALREADY_RUNNING),
        arguments(false, false, StatusEnum.STARTED, StatusEnum.STARTED));
  }

  @ParameterizedTest
  @MethodSource("flushLockParameters")
  void testFlushLockSynchronousScenarios(
      boolean firstIsSync, boolean secondIsSync, StatusEnum firstStatus, StatusEnum secondStatus)
      throws InterruptedException {
    withSynchronousRequestsEnabled(true);
    when(outboxService.flushOutboxRecords())
        .thenAnswer(
            (Answer<Long>)
                invocation -> {
                  sleep(2000);
                  return 100L;
                });

    withSynchronousRequestHeader(firstIsSync);
    CompletableFuture<FlushResponse> future1 =
        CompletableFuture.supplyAsync(() -> flushOutbox().extract().body().as(FlushResponse.class));
    // ensure the order of the threads
    sleep(1500);
    withSynchronousRequestHeader(secondIsSync);
    CompletableFuture<FlushResponse> future2 =
        CompletableFuture.supplyAsync(() -> flushOutbox().extract().body().as(FlushResponse.class));

    // Wait for both to complete
    CompletableFuture.allOf(future1, future2).join();
    FlushResponse flushResponse1 = future1.join();
    assertEquals(firstStatus, flushResponse1.getStatus());
    FlushResponse flushResponse2 = future2.join();
    assertEquals(secondStatus, flushResponse2.getStatus());
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
