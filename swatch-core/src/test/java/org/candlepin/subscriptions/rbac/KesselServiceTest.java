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
package org.candlepin.subscriptions.rbac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
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
class KesselServiceTest {

  KesselService service;
  KesselProperties properties;

  @Mock KesselInventoryServiceBlockingStub stub;
  @Mock ManagedChannel channel;

  @BeforeEach
  void setup() throws Exception {
    properties = new KesselProperties();
    properties.setTimeoutMs(5000L);

    lenient().when(stub.withDeadlineAfter(anyLong(), any(TimeUnit.class))).thenReturn(stub);
    lenient().when(channel.getState(false)).thenReturn(ConnectivityState.READY);

    service = new KesselService(properties);
    service.setStub(stub);

    Field channelField = KesselService.class.getDeclaredField("channel");
    channelField.setAccessible(true);
    channelField.set(service, channel);
  }

  @Test
  void permissionMappingUsesKesselV2RelationNames() {
    assertEquals(
        "subscriptions_report_view", KesselService.mapPermissionToRelation("subscriptions:*:*"));
    assertEquals(
        "subscriptions_report_view",
        KesselService.mapPermissionToRelation("subscriptions:reports:read"));
  }

  @Test
  void mapPermissionToRelationThrowsForUnknown() {
    assertThrows(
        IllegalArgumentException.class,
        () -> KesselService.mapPermissionToRelation("unknown:permission:value"));
  }

  @Test
  void checkAccessReturnsTrueWhenAllowed() {
    when(stub.check(any(CheckRequest.class)))
        .thenReturn(CheckResponse.newBuilder().setAllowed(Allowed.ALLOWED_TRUE).build());

    assertTrue(service.checkAccess("user123", "subscriptions:*:*"));
  }

  @Test
  void checkAccessReturnsFalseWhenDenied() {
    when(stub.check(any(CheckRequest.class)))
        .thenReturn(CheckResponse.newBuilder().setAllowed(Allowed.ALLOWED_FALSE).build());

    assertFalse(service.checkAccess("user123", "subscriptions:*:*"));
  }

  @Test
  void checkAccessReturnsFalseOnTransientGrpcError() {
    when(stub.check(any(CheckRequest.class)))
        .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

    assertFalse(service.checkAccess("user123", "subscriptions:*:*"));
  }

  @Test
  void checkAccessReturnsFalseOnNonTransientGrpcError() {
    when(stub.check(any(CheckRequest.class)))
        .thenThrow(new StatusRuntimeException(Status.PERMISSION_DENIED));

    assertFalse(service.checkAccess("user123", "subscriptions:*:*"));
  }

  @Test
  void checkAccessReturnsFalseWhenStubIsNull() {
    var uninitializedService = new KesselService(properties);
    assertFalse(uninitializedService.checkAccess("user123", "subscriptions:*:*"));
  }

  @Test
  void checkAccessBuildsCorrectRequest() {
    when(stub.check(any(CheckRequest.class)))
        .thenReturn(CheckResponse.newBuilder().setAllowed(Allowed.ALLOWED_TRUE).build());

    service.checkAccess("user123", "subscriptions:reports:read");

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
  void getPermissionsReturnsGrantedPermissions() {
    when(stub.check(any(CheckRequest.class)))
        .thenReturn(CheckResponse.newBuilder().setAllowed(Allowed.ALLOWED_TRUE).build())
        .thenReturn(CheckResponse.newBuilder().setAllowed(Allowed.ALLOWED_FALSE).build());

    List<String> permissions = service.getPermissions("user123");

    assertEquals(List.of("subscriptions:*:*"), permissions);
  }

  @Test
  void getPermissionsReturnsEmptyWhenNoneGranted() {
    when(stub.check(any(CheckRequest.class)))
        .thenReturn(CheckResponse.newBuilder().setAllowed(Allowed.ALLOWED_FALSE).build());

    List<String> permissions = service.getPermissions("user123");

    assertTrue(permissions.isEmpty());
  }

  @Test
  void getPermissionsReturnsBothWhenBothGranted() {
    when(stub.check(any(CheckRequest.class)))
        .thenReturn(CheckResponse.newBuilder().setAllowed(Allowed.ALLOWED_TRUE).build());

    List<String> permissions = service.getPermissions("user123");

    assertEquals(List.of("subscriptions:*:*", "subscriptions:reports:read"), permissions);
  }

  @Test
  void getPermissionsReturnsEmptyForNullSubjectId() {
    assertTrue(service.getPermissions(null).isEmpty());
  }

  @Test
  void getPermissionsReturnsEmptyForBlankSubjectId() {
    assertTrue(service.getPermissions("  ").isEmpty());
  }
}
