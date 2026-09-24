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
package com.redhat.swatch.kessel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.TlsServerCredentials;
import io.grpc.stub.StreamObserver;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.KeyManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.project_kessel.api.inventory.v1beta2.Allowed;
import org.project_kessel.api.inventory.v1beta2.CheckRequest;
import org.project_kessel.api.inventory.v1beta2.CheckResponse;
import org.project_kessel.api.inventory.v1beta2.KesselInventoryServiceGrpc;

/**
 * Real TLS gRPC requests exercise credential attachment, rejection, refresh, and channel
 * recreation.
 */
class KesselAuthenticatedTransportTest {
  private Server server;
  private KesselAuthorizationClient client;
  private final List<String> headers = new CopyOnWriteArrayList<>();
  private final AtomicBoolean rejectNext = new AtomicBoolean();
  private final AtomicReference<String> token = new AtomicReference<>("Bearer first");
  private HccCredentials credentials;
  private String previousTrustStore;
  private String previousPassword;
  private String previousType;

  @BeforeEach
  void setup() throws Exception {
    // Reuse the existing local test CA, never trust arbitrary server certificates.
    Path resources = Path.of("../clients-core/src/test/resources").toAbsolutePath();
    previousTrustStore = System.getProperty("javax.net.ssl.trustStore");
    previousPassword = System.getProperty("javax.net.ssl.trustStorePassword");
    previousType = System.getProperty("javax.net.ssl.trustStoreType");
    System.setProperty("javax.net.ssl.trustStore", resources.resolve("test-ca.jks").toString());
    System.setProperty("javax.net.ssl.trustStorePassword", "password");
    System.setProperty("javax.net.ssl.trustStoreType", "JKS");
    var store = KeyStore.getInstance("JKS");
    try (var input = Files.newInputStream(resources.resolve("server.jks"))) {
      store.load(input, "password".toCharArray());
    }
    var keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    keyManagers.init(store, "password".toCharArray());
    server =
        Grpc.newServerBuilderForPort(
                0,
                TlsServerCredentials.newBuilder().keyManager(keyManagers.getKeyManagers()).build())
            .intercept(
                new ServerInterceptor() {
                  @Override
                  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                      ServerCall<ReqT, RespT> call,
                      Metadata metadata,
                      ServerCallHandler<ReqT, RespT> next) {
                    String header =
                        metadata.get(
                            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER));
                    headers.add(header == null ? "absent" : header);
                    if (header == null || rejectNext.getAndSet(false)) {
                      call.close(Status.UNAUTHENTICATED, new Metadata());
                      return new ServerCall.Listener<>() {};
                    }
                    return next.startCall(call, metadata);
                  }
                })
            .addService(
                new KesselInventoryServiceGrpc.KesselInventoryServiceImplBase() {
                  @Override
                  public void check(CheckRequest request, StreamObserver<CheckResponse> response) {
                    response.onNext(
                        CheckResponse.newBuilder().setAllowed(Allowed.ALLOWED_TRUE).build());
                    response.onCompleted();
                  }
                })
            .build()
            .start();
    credentials = mock(HccCredentials.class);
    when(credentials.isConfigured()).thenReturn(true);
    when(credentials.authorizationHeader()).thenAnswer(invocation -> token.get());
  }

  @AfterEach
  void close() throws Exception {
    if (client != null) {
      client.shutdown();
    }
    if (server != null) {
      server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }
    restore("javax.net.ssl.trustStore", previousTrustStore);
    restore("javax.net.ssl.trustStorePassword", previousPassword);
    restore("javax.net.ssl.trustStoreType", previousType);
  }

  @Test
  void bearerIsAttachedAfterTlsHandshakeAndAfterChannelRecreation() throws Exception {
    client =
        new KesselAuthorizationClient(
            config(false), id -> "workspace", KesselMetricsRecorder.NOOP, credentials);
    client.init();
    assertTrue(check());
    var field = KesselAuthorizationClient.class.getDeclaredField("channel");
    field.setAccessible(true);
    ((ManagedChannel) field.get(client)).shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    token.set("Bearer renewed");
    assertTrue(check());
    assertEquals(List.of("Bearer first", "Bearer renewed"), headers);
  }

  @Test
  void rejectionForcesRefreshBeforeRetry() {
    when(credentials.authorizationHeader(true))
        .thenAnswer(
            invocation -> {
              token.set("Bearer refreshed");
              return token.get();
            });
    client =
        new KesselAuthorizationClient(
            config(false), id -> "workspace", KesselMetricsRecorder.NOOP, credentials);
    client.init();
    rejectNext.set(true);
    assertTrue(check());
    verify(credentials).authorizationHeader(true);
    assertEquals(List.of("Bearer first", "Bearer refreshed"), headers);
  }

  @Test
  void failedTokenCannotProduceAnonymousCall() {
    when(credentials.authorizationHeader()).thenThrow(new IllegalStateException("Unavailable"));
    when(credentials.authorizationHeader(true)).thenThrow(new IllegalStateException("Unavailable"));
    client =
        new KesselAuthorizationClient(
            config(false), id -> "workspace", KesselMetricsRecorder.NOOP, credentials);
    client.init();
    assertFalse(check());
    assertTrue(headers.isEmpty());
  }

  @Test
  void requiredAuthenticationOnPlaintextIsDeniedBeforeConnection() {
    client =
        new KesselAuthorizationClient(
            config(true), id -> "workspace", KesselMetricsRecorder.NOOP, credentials);
    client.init();
    assertFalse(check());
    assertTrue(headers.isEmpty());
  }

  private boolean check() {
    return client.checkAccess("user", "subscriptions:reports:read", "org");
  }

  private KesselConfig config(boolean insecure) {
    return new KesselConfig() {
      public String endpoint() {
        return "localhost:" + server.getPort();
      }

      public boolean insecure() {
        return insecure;
      }

      public long timeoutMs() {
        return 5000;
      }

      public boolean authEnabled() {
        return true;
      }
    };
  }

  private static void restore(String key, String value) {
    if (value == null) {
      System.clearProperty(key);
    } else {
      System.setProperty(key, value);
    }
  }
}
