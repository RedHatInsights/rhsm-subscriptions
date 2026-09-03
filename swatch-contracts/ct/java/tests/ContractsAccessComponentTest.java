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
package tests;

import static com.redhat.swatch.component.tests.utils.SwatchUtils.X_RH_IDENTITY_HEADER;
import static com.redhat.swatch.component.tests.utils.Topics.EXPORT_REQUESTS;
import static org.junit.jupiter.api.Assertions.assertEquals;

import api.ExportApiStubs;
import com.redhat.swatch.component.tests.api.AuthorizationModel;
import com.redhat.swatch.component.tests.api.SubscriptionsAccessLevel;
import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.component.tests.utils.SwatchUtils;
import domain.Product;
import io.restassured.response.Response;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

public class ContractsAccessComponentTest extends BaseContractComponentTest {

  static Stream<Arguments> reportingEndpoints() {
    return combinations(
        CustomerFacingEndpoint.CAPACITY,
        CustomerFacingEndpoint.SUBSCRIPTIONS_V1,
        CustomerFacingEndpoint.SUBSCRIPTIONS_V2);
  }

  static Stream<Arguments> allCustomerFacingEndpoints() {
    return combinations(CustomerFacingEndpoint.values());
  }

  private static Stream<Arguments> combinations(CustomerFacingEndpoint... endpoints) {
    return Stream.of(AuthorizationModel.values())
        .flatMap(model -> Stream.of(endpoints).map(endpoint -> Arguments.of(model, endpoint)));
  }

  @AfterEach
  void resetKesselRbacFlag() {
    unleash.disableKesselRbac();
  }

  @ParameterizedTest(name = "authorizationModel={0}, endpoint={1}")
  @MethodSource("reportingEndpoints")
  @TestPlanName("rbac-parity-TC001")
  void shouldAllowReportingWhenAdminAccessGranted(
      AuthorizationModel authorizationModel, CustomerFacingEndpoint endpoint) {
    // Given
    String userId = RandomUtils.generateRandom();
    var requestHeaders = SwatchUtils.securityHeadersWithUserRole(orgId, userId);
    givenUserAccess(
        authorizationModel, userId, requestHeaders, SubscriptionsAccessLevel.GRANTED_ADMIN);

    // When
    Response response = whenGetCustomerFacingEndpoint(endpoint, requestHeaders);

    // Then
    thenResponseStatusIs(response, HttpStatus.SC_OK);
  }

  @ParameterizedTest(name = "authorizationModel={0}")
  @EnumSource(AuthorizationModel.class)
  @TestPlanName("rbac-parity-TC002")
  void shouldAllowBillingAccountIdsWhenAdminGranted(AuthorizationModel authorizationModel) {
    // Given
    String userId = RandomUtils.generateRandom();
    var requestHeaders = SwatchUtils.securityHeadersWithUserRole(orgId, userId);
    givenUserAccess(
        authorizationModel, userId, requestHeaders, SubscriptionsAccessLevel.GRANTED_ADMIN);

    // When
    Response response =
        whenGetCustomerFacingEndpoint(CustomerFacingEndpoint.BILLING_ACCOUNT_IDS, requestHeaders);

    // Then
    thenResponseStatusIs(response, HttpStatus.SC_OK);
  }

  @ParameterizedTest(name = "authorizationModel={0}, endpoint={1}")
  @MethodSource("reportingEndpoints")
  @TestPlanName("rbac-parity-TC003")
  void shouldAllowReportingWhenReaderAccessGranted(
      AuthorizationModel authorizationModel, CustomerFacingEndpoint endpoint) {
    // Given
    String userId = RandomUtils.generateRandom();
    var requestHeaders = SwatchUtils.securityHeadersWithUserRole(orgId, userId);
    givenUserAccess(
        authorizationModel, userId, requestHeaders, SubscriptionsAccessLevel.GRANTED_READER);

    // When
    Response response = whenGetCustomerFacingEndpoint(endpoint, requestHeaders);

    // Then
    thenResponseStatusIs(response, HttpStatus.SC_OK);
  }

  @ParameterizedTest(name = "authorizationModel={0}")
  @EnumSource(AuthorizationModel.class)
  @TestPlanName("rbac-parity-TC004")
  void shouldAllowBillingAccountIdsWhenReaderGranted(AuthorizationModel authorizationModel) {
    // Given
    String userId = RandomUtils.generateRandom();
    var requestHeaders = SwatchUtils.securityHeadersWithUserRole(orgId, userId);
    givenUserAccess(
        authorizationModel, userId, requestHeaders, SubscriptionsAccessLevel.GRANTED_READER);

    // When
    Response response =
        whenGetCustomerFacingEndpoint(CustomerFacingEndpoint.BILLING_ACCOUNT_IDS, requestHeaders);

    // Then
    thenResponseStatusIs(response, HttpStatus.SC_OK);
  }

  @ParameterizedTest(name = "authorizationModel={0}, endpoint={1}")
  @MethodSource("reportingEndpoints")
  @TestPlanName("rbac-parity-TC005")
  void shouldDenyReportingWhenAccessDenied(
      AuthorizationModel authorizationModel, CustomerFacingEndpoint endpoint) {
    // Given
    String userId = RandomUtils.generateRandom();
    var requestHeaders = SwatchUtils.securityHeadersWithUserRole(orgId, userId);
    givenUserAccess(authorizationModel, userId, requestHeaders, SubscriptionsAccessLevel.DENIED);

    // When
    Response response = whenGetCustomerFacingEndpoint(endpoint, requestHeaders);

    // Then
    thenResponseStatusIs(response, HttpStatus.SC_FORBIDDEN);
  }

  @ParameterizedTest(name = "authorizationModel={0}")
  @EnumSource(AuthorizationModel.class)
  @TestPlanName("rbac-parity-TC006")
  void shouldDenyBillingAccountIdsWhenAccessDenied(AuthorizationModel authorizationModel) {
    // Given
    String userId = RandomUtils.generateRandom();
    var requestHeaders = SwatchUtils.securityHeadersWithUserRole(orgId, userId);
    givenUserAccess(authorizationModel, userId, requestHeaders, SubscriptionsAccessLevel.DENIED);

    // When
    Response response =
        whenGetCustomerFacingEndpoint(CustomerFacingEndpoint.BILLING_ACCOUNT_IDS, requestHeaders);

    // Then
    thenResponseStatusIs(response, HttpStatus.SC_FORBIDDEN);
  }

  @ParameterizedTest(name = "authorizationModel={0}, endpoint={1}")
  @MethodSource("allCustomerFacingEndpoints")
  @TestPlanName("rbac-parity-TC007")
  void shouldAllowAllEndpointsWhenServiceAccountAdmin(
      AuthorizationModel authorizationModel, CustomerFacingEndpoint endpoint) {
    // Given
    String clientId = RandomUtils.generateRandom();
    var requestHeaders = SwatchUtils.securityHeadersWithServiceAccount(orgId, clientId);
    givenServiceAccountAccess(
        authorizationModel, clientId, requestHeaders, SubscriptionsAccessLevel.GRANTED_ADMIN);

    // When
    Response response = whenGetCustomerFacingEndpoint(endpoint, requestHeaders);

    // Then
    thenResponseStatusIs(response, HttpStatus.SC_OK);
  }

  @ParameterizedTest(name = "authorizationModel={0}, endpoint={1}")
  @MethodSource("allCustomerFacingEndpoints")
  @TestPlanName("rbac-parity-TC008")
  void shouldDenyAllEndpointsWhenServiceAccountDenied(
      AuthorizationModel authorizationModel, CustomerFacingEndpoint endpoint) {
    // Given
    String clientId = RandomUtils.generateRandom();
    var requestHeaders = SwatchUtils.securityHeadersWithServiceAccount(orgId, clientId);
    givenServiceAccountAccess(
        authorizationModel, clientId, requestHeaders, SubscriptionsAccessLevel.DENIED);

    // When
    Response response = whenGetCustomerFacingEndpoint(endpoint, requestHeaders);

    // Then
    thenResponseStatusIs(response, HttpStatus.SC_FORBIDDEN);
  }

  @ParameterizedTest(name = "authorizationModel={0}")
  @EnumSource(AuthorizationModel.class)
  @TestPlanName("rbac-parity-TC009")
  void shouldAllowBillingAccountIdsForAssociate(AuthorizationModel authorizationModel) {
    // Given
    givenAuthorizationModel(authorizationModel);
    String email = RandomUtils.generateRandom() + "@redhat.com";
    var requestHeaders = SwatchUtils.securityHeadersWithAssociate(orgId, email);

    // When
    Response response =
        whenGetCustomerFacingEndpoint(CustomerFacingEndpoint.BILLING_ACCOUNT_IDS, requestHeaders);

    // Then
    thenResponseStatusIs(response, HttpStatus.SC_OK);
  }

  @ParameterizedTest(name = "authorizationModel={0}, endpoint={1}")
  @MethodSource("reportingEndpoints")
  @TestPlanName("rbac-parity-TC010")
  void shouldDenyReportingForAssociate(
      AuthorizationModel authorizationModel, CustomerFacingEndpoint endpoint) {
    // Given
    givenAuthorizationModel(authorizationModel);
    String email = RandomUtils.generateRandom() + "@redhat.com";
    var requestHeaders = SwatchUtils.securityHeadersWithAssociate(orgId, email);

    // When
    Response response = whenGetCustomerFacingEndpoint(endpoint, requestHeaders);

    // Then
    thenResponseStatusIs(response, HttpStatus.SC_FORBIDDEN);
  }

  @Test
  @TestPlanName("rbac-parity-TC011")
  void shouldDenyCapacityReportWhenKesselUnavailable() {
    // Given
    String userId = RandomUtils.generateRandom();
    var requestHeaders = SwatchUtils.securityHeadersWithUserRole(orgId, userId);
    unleash.enableKesselRbac();
    wiremock.forKesselAccessControl().stubDefaultWorkspace(orgId);

    // When
    Response response =
        whenGetCustomerFacingEndpoint(CustomerFacingEndpoint.CAPACITY, requestHeaders);

    // Then
    thenResponseStatusIs(response, HttpStatus.SC_FORBIDDEN);
  }

  @ParameterizedTest(name = "authorizationModel={0}")
  @EnumSource(AuthorizationModel.class)
  @TestPlanName("rbac-parity-TC012")
  void shouldCompleteExportWhenAdminAccessGranted(AuthorizationModel authorizationModel) {
    // Given
    ExportApiStubs exportApi = givenExportCallbacks();
    String userId = RandomUtils.generateRandom();
    var requestHeaders = SwatchUtils.securityHeadersWithUserRole(orgId, userId);
    String identityHeader = requestHeaders.get(X_RH_IDENTITY_HEADER);
    givenExportAccess(
        authorizationModel, userId, identityHeader, SubscriptionsAccessLevel.GRANTED_ADMIN);
    Map<String, Object> exportRequest = givenExportRequest(identityHeader);

    // When
    whenExportRequestIsPublished(exportRequest);

    // Then
    thenExportUploadWasSent(exportApi);
  }

  @ParameterizedTest(name = "authorizationModel={0}")
  @EnumSource(AuthorizationModel.class)
  @TestPlanName("rbac-parity-TC013")
  void shouldDenyExportWhenAccessDenied(AuthorizationModel authorizationModel) {
    // Given
    ExportApiStubs exportApi = givenExportCallbacks();
    String userId = RandomUtils.generateRandom();
    var requestHeaders = SwatchUtils.securityHeadersWithUserRole(orgId, userId);
    String identityHeader = requestHeaders.get(X_RH_IDENTITY_HEADER);
    givenExportAccess(authorizationModel, userId, identityHeader, SubscriptionsAccessLevel.DENIED);
    Map<String, Object> exportRequest = givenExportRequest(identityHeader);

    // When
    whenExportRequestIsPublished(exportRequest);

    // Then
    thenExportErrorWasSent(exportApi);
  }

  private void givenUserAccess(
      AuthorizationModel authorizationModel,
      String userId,
      Map<String, String> requestHeaders,
      SubscriptionsAccessLevel accessLevel) {
    rbacHelper.givenUserHasSubscriptionsAccess(
        authorizationModel, userId, requestHeaders.get(X_RH_IDENTITY_HEADER), accessLevel);
  }

  private void givenServiceAccountAccess(
      AuthorizationModel authorizationModel,
      String clientId,
      Map<String, String> requestHeaders,
      SubscriptionsAccessLevel accessLevel) {
    rbacHelper.givenServiceAccountHasSubscriptionsAccess(
        authorizationModel, clientId, requestHeaders.get(X_RH_IDENTITY_HEADER), accessLevel);
  }

  private void givenAuthorizationModel(AuthorizationModel authorizationModel) {
    if (authorizationModel == AuthorizationModel.KESSEL) {
      unleash.enableKesselRbac();
    } else {
      unleash.disableKesselRbac();
    }
  }

  private void givenExportAccess(
      AuthorizationModel authorizationModel,
      String userId,
      String identityHeader,
      SubscriptionsAccessLevel accessLevel) {
    rbacHelper.givenUserHasSubscriptionsAccess(
        authorizationModel, userId, identityHeader, accessLevel);
    // Export uses RbacDelegate, which always calls the RBAC v1 API
    // regardless of the Kessel Unleash flag.
    if (authorizationModel == AuthorizationModel.KESSEL) {
      wiremock.forRbacAccessControl().stubSubscriptionsAccess(identityHeader, accessLevel);
    }
  }

  private ExportApiStubs givenExportCallbacks() {
    ExportApiStubs exportApi = wiremock.forExportAPI();
    exportApi.stubExportCallbacks();
    return exportApi;
  }

  private Map<String, Object> givenExportRequest(String identityHeader) {
    return Map.of(
        "id",
        UUID.randomUUID().toString(),
        "source",
        "urn:redhat:source:console:app:export-service",
        "specversion",
        "1.0",
        "type",
        "com.redhat.console.export-service.request",
        "time",
        Instant.now().toString(),
        "dataschema",
        "https://console.redhat.com/api/schemas/apps/export-service/v1/resource-request.json",
        "redhatorgid",
        orgId,
        "data",
        Map.of(
            "resource_request",
            Map.of(
                "application",
                "subscriptions",
                "export_request_uuid",
                UUID.randomUUID().toString(),
                "filters",
                Map.of(),
                "format",
                "json",
                "resource",
                "subscriptions",
                "uuid",
                UUID.randomUUID().toString(),
                "x-rh-identity",
                identityHeader)));
  }

  private Response whenGetCustomerFacingEndpoint(
      CustomerFacingEndpoint endpoint, Map<String, String> headers) {
    return switch (endpoint) {
      case CAPACITY ->
          service.getCapacityReportByMetricId(Product.OPENSHIFT, CORES.toString(), headers);
      case SUBSCRIPTIONS_V1 -> service.getSkuCapacityReportV1(Product.OPENSHIFT, headers);
      case SUBSCRIPTIONS_V2 -> service.getSkuCapacityReportV2(Product.OPENSHIFT, headers);
      case BILLING_ACCOUNT_IDS -> service.fetchBillingAccountIdsForOrg(orgId, headers);
    };
  }

  private void whenExportRequestIsPublished(Map<String, Object> exportRequest) {
    kafkaBridge.produceKafkaMessage(EXPORT_REQUESTS, exportRequest);
  }

  private void thenResponseStatusIs(Response response, int expectedStatus) {
    assertEquals(
        expectedStatus, response.statusCode(), "Unexpected status for " + response.getStatusLine());
  }

  private void thenExportUploadWasSent(ExportApiStubs exportApi) {
    AwaitilityUtils.untilIsTrue(() -> exportApi.countUploadRequests() > 0);
  }

  private void thenExportErrorWasSent(ExportApiStubs exportApi) {
    AwaitilityUtils.untilIsTrue(() -> exportApi.countErrorRequests() > 0);
  }

  private enum CustomerFacingEndpoint {
    CAPACITY,
    SUBSCRIPTIONS_V1,
    SUBSCRIPTIONS_V2,
    BILLING_ACCOUNT_IDS
  }
}
