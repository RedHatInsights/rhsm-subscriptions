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
package com.redhat.swatch.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.io.IOException;
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
class KesselAuthorizationServiceTest {

  KesselAuthorizationService service;
  RhIdentityPrincipalFactory identityFactory;

  @Mock KesselInventoryServiceBlockingStub stub;
  @Mock KesselProperties kesselProperties;

  @BeforeEach
  void setup() {
    lenient().when(kesselProperties.timeoutMs()).thenReturn(5000L);
    lenient().when(stub.withDeadlineAfter(anyLong(), any(TimeUnit.class))).thenReturn(stub);

    service = new KesselAuthorizationService();
    service.setStub(stub);
    service.properties = kesselProperties;

    identityFactory = new RhIdentityPrincipalFactory();
    identityFactory.mapper = new ObjectMapper();
  }

  private RhIdentityPrincipal principalFromJson(String json) {
    try {
      return identityFactory.fromJson(json);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void permissionMappingUsesKesselV2RelationNames() {
    assertEquals(
        "subscriptions_report_view",
        KesselAuthorizationService.mapPermissionToRelation("subscriptions:*:*"));
    assertEquals(
        "subscriptions_report_view",
        KesselAuthorizationService.mapPermissionToRelation("subscriptions:reports:read"));
  }

  @Test
  void checkAccessReturnsTrueWhenAllowed() {
    when(stub.check(any(CheckRequest.class)))
        .thenReturn(CheckResponse.newBuilder().setAllowed(Allowed.ALLOWED_TRUE).build());

    var principal = principalFromJson(RhIdentityUtils.CUSTOMER_IDENTITY_JSON);
    assertTrue(service.checkAccess(principal, "subscriptions:*:*"));
  }

  @Test
  void checkAccessReturnsFalseWhenDenied() {
    when(stub.check(any(CheckRequest.class)))
        .thenReturn(CheckResponse.newBuilder().setAllowed(Allowed.ALLOWED_FALSE).build());

    var principal = principalFromJson(RhIdentityUtils.CUSTOMER_IDENTITY_JSON);
    assertFalse(service.checkAccess(principal, "subscriptions:*:*"));
  }

  @Test
  void checkAccessReturnsFalseOnTransientGrpcError() {
    when(stub.check(any(CheckRequest.class)))
        .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

    var principal = principalFromJson(RhIdentityUtils.CUSTOMER_IDENTITY_JSON);
    assertFalse(service.checkAccess(principal, "subscriptions:*:*"));
  }

  @Test
  void checkAccessReturnsFalseOnNonTransientGrpcError() {
    when(stub.check(any(CheckRequest.class)))
        .thenThrow(new StatusRuntimeException(Status.PERMISSION_DENIED));

    var principal = principalFromJson(RhIdentityUtils.CUSTOMER_IDENTITY_JSON);
    assertFalse(service.checkAccess(principal, "subscriptions:*:*"));
  }

  @Test
  void checkAccessBuildsCorrectRequestForUserPrincipal() {
    when(stub.check(any(CheckRequest.class)))
        .thenReturn(CheckResponse.newBuilder().setAllowed(Allowed.ALLOWED_TRUE).build());

    var principal = principalFromJson(RhIdentityUtils.CUSTOMER_IDENTITY_JSON);
    service.checkAccess(principal, "subscriptions:reports:read");

    var captor = ArgumentCaptor.forClass(CheckRequest.class);
    verify(stub).check(captor.capture());

    var request = captor.getValue();
    assertEquals("subscriptions_report_view", request.getRelation());
    assertEquals("principal", request.getSubject().getResource().getResourceType());
    assertEquals("user123", request.getSubject().getResource().getResourceId());
    assertEquals("workspace", request.getObject().getResourceType());
  }

  @Test
  void getPermissionsReturnsGrantedPermissions() {
    when(stub.check(any(CheckRequest.class)))
        .thenReturn(CheckResponse.newBuilder().setAllowed(Allowed.ALLOWED_TRUE).build())
        .thenReturn(CheckResponse.newBuilder().setAllowed(Allowed.ALLOWED_FALSE).build());

    var principal = principalFromJson(RhIdentityUtils.CUSTOMER_IDENTITY_JSON);
    List<String> permissions = service.getPermissions(principal);

    assertEquals(List.of("subscriptions:*:*"), permissions);
  }

  @Test
  void getPermissionsReturnsEmptyWhenNoneGranted() {
    when(stub.check(any(CheckRequest.class)))
        .thenReturn(CheckResponse.newBuilder().setAllowed(Allowed.ALLOWED_FALSE).build());

    var principal = principalFromJson(RhIdentityUtils.CUSTOMER_IDENTITY_JSON);
    List<String> permissions = service.getPermissions(principal);

    assertTrue(permissions.isEmpty());
  }

  @Test
  void checkAccessReturnsFalseWhenPrincipalIdMissing() {
    var principal =
        principalFromJson(
            """
            {
              "identity": {
                "type": "User",
                "org_id": "org123"
              }
            }
            """);
    assertFalse(service.checkAccess(principal, "subscriptions:*:*"));
  }

  @Test
  void getPermissionsReturnsBothWhenBothGranted() {
    when(stub.check(any(CheckRequest.class)))
        .thenReturn(CheckResponse.newBuilder().setAllowed(Allowed.ALLOWED_TRUE).build());

    var principal = principalFromJson(RhIdentityUtils.CUSTOMER_IDENTITY_JSON);
    List<String> permissions = service.getPermissions(principal);

    assertEquals(List.of("subscriptions:*:*", "subscriptions:reports:read"), permissions);
  }
}
