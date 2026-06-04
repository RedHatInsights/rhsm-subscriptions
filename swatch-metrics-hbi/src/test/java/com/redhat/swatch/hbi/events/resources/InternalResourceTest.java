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
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.apache.http.HttpStatus;
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
        arguments(true, false, StatusEnum.SUCCESS, StatusEnum.ALREADY_RUNNING),
        arguments(false, true, StatusEnum.STARTED, StatusEnum.ALREADY_RUNNING),
        arguments(false, false, StatusEnum.STARTED, StatusEnum.ALREADY_RUNNING));
  }

  @ParameterizedTest
  @MethodSource("flushLockParameters")
  void testFlushLockSynchronousScenarios(
      boolean firstIsSync, boolean secondIsSync, StatusEnum firstStatus, StatusEnum secondStatus)
      throws Exception {
    withSynchronousRequestsEnabled(true);

    // Latches to control execution flow
    CountDownLatch firstRequestStarted = new CountDownLatch(1);
    CountDownLatch firstRequestCanComplete = new CountDownLatch(1);

    when(outboxService.flushOutboxRecords())
        .thenAnswer(
            (Answer<Long>)
                invocation -> {
                  firstRequestStarted.countDown(); // Signal that we've started
                  firstRequestCanComplete.await(); // Wait until test says we can complete
                  return 100L;
                });

    // The first request needs to be made in a separate thread because
    // a synchronous call would complete before the second call would initiate.
    withSynchronousRequestHeader(firstIsSync);
    CompletableFuture<FlushResponse> future1 =
        CompletableFuture.supplyAsync(() -> flushOutbox().extract().body().as(FlushResponse.class));

    // Ensure that the first request thread has started (hits the await state above).
    // It will stay in the await state until after the second has completed.
    // The test will not need to wait 2 seconds unless something bad happened with
    // the service, which is not likely to happen here.
    assertTrue(firstRequestStarted.await(2, TimeUnit.SECONDS), "First request did not start!");

    withSynchronousRequestHeader(secondIsSync);
    FlushResponse flushResponse2 = flushOutbox().extract().body().as(FlushResponse.class);
    assertEquals(secondStatus, flushResponse2.getStatus());

    // Now allow the first request to complete
    firstRequestCanComplete.countDown();

    // The test will not need to wait 2 seconds, unless something bad happened with the service.
    FlushResponse flushResponse1 = future1.get(2, TimeUnit.SECONDS);
    assertEquals(firstStatus, flushResponse1.getStatus());
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
