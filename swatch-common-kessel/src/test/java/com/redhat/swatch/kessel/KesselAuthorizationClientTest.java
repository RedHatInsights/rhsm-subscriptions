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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.project_kessel.api.inventory.v1beta2.Allowed;
import org.project_kessel.api.inventory.v1beta2.CheckRequest;
import org.project_kessel.api.inventory.v1beta2.CheckResponse;
import org.project_kessel.api.inventory.v1beta2.KesselInventoryServiceGrpc.KesselInventoryServiceBlockingStub;

@ExtendWith(MockitoExtension.class)
class KesselAuthorizationClientTest {

  private static final String ORG_ID = "org123";

  KesselAuthorizationClient client;

  KesselConfig config =
      new KesselConfig() {
        @Override
        public String endpoint() {
          return "localhost:9000";
        }

        @Override
        public boolean insecure() {
          return true;
        }

        @Override
        public long timeoutMs() {
          return 5000;
        }
      };

  @Mock KesselInventoryServiceBlockingStub stub;
  @Mock ManagedChannel channel;

  @BeforeEach
  void setup() throws Exception {
    lenient().when(stub.withDeadlineAfter(anyLong(), any(TimeUnit.class))).thenReturn(stub);
    lenient().when(channel.getState(false)).thenReturn(ConnectivityState.READY);

    client = new KesselAuthorizationClient(config, orgId -> "default");
    client.setStub(stub);
    setChannel(channel);
  }

  private void setChannel(ManagedChannel ch) throws Exception {
    Field channelField = KesselAuthorizationClient.class.getDeclaredField("channel");
    channelField.setAccessible(true);
    channelField.set(client, ch);
  }

  // --- Permission mapping tests ---

  @Test
  void permissionMappingUsesKesselV2RelationNames() {
    assertEquals(
        "subscriptions_report_view",
        KesselAuthorizationClient.mapPermissionToRelation("subscriptions:*:*"));
    assertEquals(
        "subscriptions_report_view",
        KesselAuthorizationClient.mapPermissionToRelation("subscriptions:reports:read"));
  }

  @Test
  void mapPermissionToRelationThrowsForUnknown() {
    assertThrows(
        IllegalArgumentException.class,
        () -> KesselAuthorizationClient.mapPermissionToRelation("unknown:permission:value"));
  }

  // --- checkAccess tests ---

  @Test
  void checkAccessReturnsTrueWhenAllowed() {
    when(stub.check(any(CheckRequest.class)))
        .thenReturn(CheckResponse.newBuilder().setAllowed(Allowed.ALLOWED_TRUE).build());

    assertTrue(client.checkAccess("user123", "subscriptions:reports:read", ORG_ID));
  }

  @Test
  void checkAccessReturnsFalseWhenDenied() {
    when(stub.check(any(CheckRequest.class)))
        .thenReturn(CheckResponse.newBuilder().setAllowed(Allowed.ALLOWED_FALSE).build());

    assertFalse(client.checkAccess("user123", "subscriptions:reports:read", ORG_ID));
  }

  @Test
  void checkAccessReturnsFalseOnNonTransientGrpcError() {
    when(stub.check(any(CheckRequest.class)))
        .thenThrow(new StatusRuntimeException(Status.PERMISSION_DENIED));

    assertFalse(client.checkAccess("user123", "subscriptions:reports:read", ORG_ID));
  }

  @Test
  void checkAccessReturnsFalseWhenStubIsNull() {
    var uninitializedClient = new KesselAuthorizationClient(config, orgId -> "default");
    assertFalse(uninitializedClient.checkAccess("user123", "subscriptions:reports:read", ORG_ID));
  }

  @Test
  void checkAccessBuildsCorrectRequest() {
    when(stub.check(any(CheckRequest.class)))
        .thenReturn(CheckResponse.newBuilder().setAllowed(Allowed.ALLOWED_TRUE).build());

    client.checkAccess("user123", "subscriptions:reports:read", ORG_ID);

    var captor = ArgumentCaptor.forClass(CheckRequest.class);
    verify(stub).check(captor.capture());

    var request = captor.getValue();
    assertEquals("subscriptions_report_view", request.getRelation());
    assertEquals("principal", request.getSubject().getResource().getResourceType());
    assertEquals("user123", request.getSubject().getResource().getResourceId());
    assertEquals("rbac", request.getSubject().getResource().getReporter().getType());
    assertEquals("workspace", request.getObject().getResourceType());
    assertEquals("default", request.getObject().getResourceId());
  }

  @Test
  void checkAccessUsesWorkspaceFromResolver() {
    var customClient = new KesselAuthorizationClient(config, orgId -> "workspace-" + orgId);
    customClient.setStub(stub);
    try {
      setChannel(channel);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    when(stub.check(any(CheckRequest.class)))
        .thenReturn(CheckResponse.newBuilder().setAllowed(Allowed.ALLOWED_TRUE).build());

    customClient.checkAccess("user123", "subscriptions:reports:read", "myorg");

    var captor = ArgumentCaptor.forClass(CheckRequest.class);
    verify(stub).check(captor.capture());
    assertEquals("workspace-myorg", captor.getValue().getObject().getResourceId());
  }

  // --- getPermissions tests (with dedup) ---

  @Test
  void getPermissionsReturnsSingleReaderPermissionWhenAllowed() {
    when(stub.check(any(CheckRequest.class)))
        .thenReturn(CheckResponse.newBuilder().setAllowed(Allowed.ALLOWED_TRUE).build());

    List<String> permissions = client.getPermissions("user123", ORG_ID);

    assertEquals(List.of("subscriptions:reports:read"), permissions);
    verify(stub, times(1)).check(any(CheckRequest.class));
  }

  @Test
  void getPermissionsReturnsEmptyWhenDenied() {
    when(stub.check(any(CheckRequest.class)))
        .thenReturn(CheckResponse.newBuilder().setAllowed(Allowed.ALLOWED_FALSE).build());

    List<String> permissions = client.getPermissions("user123", ORG_ID);

    assertTrue(permissions.isEmpty());
    verify(stub, times(1)).check(any(CheckRequest.class));
  }

  @Test
  void getPermissionsReturnsEmptyForNullSubjectId() {
    assertTrue(client.getPermissions(null, ORG_ID).isEmpty());
  }

  @Test
  void getPermissionsReturnsEmptyForBlankSubjectId() {
    assertTrue(client.getPermissions("  ", ORG_ID).isEmpty());
  }

  // --- Resilience: retry on transient failure then succeed ---

  @Test
  void checkAccessRetriesOnTransientFailureThenSucceeds() {
    when(stub.check(any(CheckRequest.class)))
        .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE))
        .thenReturn(CheckResponse.newBuilder().setAllowed(Allowed.ALLOWED_TRUE).build());

    assertTrue(client.checkAccess("user123", "subscriptions:reports:read", ORG_ID));
    verify(stub, times(2)).check(any(CheckRequest.class));
  }

  // --- Resilience: exhaust retries, fail closed ---

  @Test
  void checkAccessFailsClosedAfterExhaustingRetries() {
    when(stub.check(any(CheckRequest.class)))
        .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

    assertFalse(client.checkAccess("user123", "subscriptions:reports:read", ORG_ID));
    verify(stub, times(KesselAuthorizationClient.MAX_RETRIES + 1)).check(any(CheckRequest.class));
  }

  // --- Resilience: UNAUTHENTICATED triggers channel recreation ---

  @Test
  void checkAccessRecreatesChannelOnUnauthenticated() {
    when(stub.check(any(CheckRequest.class)))
        .thenThrow(new StatusRuntimeException(Status.UNAUTHENTICATED));

    client.checkAccess("user123", "subscriptions:reports:read", ORG_ID);

    verify(channel).shutdown();
  }

  // --- Resilience: channel SHUTDOWN triggers recreation ---

  @Test
  void getClientRecreatesChannelWhenShutdown() {
    when(channel.getState(false)).thenReturn(ConnectivityState.SHUTDOWN);

    client.getClient();

    verify(channel).shutdown();
  }

  // --- Resilience: synchronized recreation prevents duplicate channels ---

  @Test
  void initializeChannelSkipsIfAlreadyRecreatedByAnotherThread() throws Exception {
    ManagedChannel newChannel = org.mockito.Mockito.mock(ManagedChannel.class, "newChannel");

    setChannel(newChannel);

    client.initializeChannel("test", channel);

    Field channelField = KesselAuthorizationClient.class.getDeclaredField("channel");
    channelField.setAccessible(true);
    var currentChannel = channelField.get(client);
    assertEquals(
        newChannel,
        currentChannel,
        "Channel should not have been replaced since brokenChannel != current channel");
  }
}
