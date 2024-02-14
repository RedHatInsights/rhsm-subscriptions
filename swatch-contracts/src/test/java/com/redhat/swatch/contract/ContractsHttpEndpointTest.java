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
package com.redhat.swatch.contract;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redhat.swatch.clients.rh.partner.gateway.api.model.PartnerEntitlementV1;
import com.redhat.swatch.contract.openapi.model.Contract;
import com.redhat.swatch.contract.openapi.model.ContractRequest;
import com.redhat.swatch.contract.openapi.model.ContractResponse;
import com.redhat.swatch.contract.openapi.model.PartnerEntitlementContract;
import com.redhat.swatch.contract.openapi.model.StatusResponse;
import com.redhat.swatch.contract.service.ContractService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import java.util.List;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ContractsHttpEndpointTest {

  @InjectMock ContractService contractService;

  @Test
  @TestSecurity(
      user = "placeholder",
      roles = {"service"})
  void whenGetContract_thenContractShouldBeFound() {
    Contract contract = new Contract();
    contract.setOrgId("org123");
    when(contractService.getContracts(any(), any(), any(), any(), any(), any()))
        .thenReturn(List.of(contract));
    given()
        .contentType(ContentType.JSON)
        .param("org_id", "org123")
        .when()
        .get("/api/swatch-contracts/internal/contracts")
        .then()
        .statusCode(HttpStatus.SC_OK)
        .body("size()", is(1))
        .body("[0].org_id", is("org123"));
  }

  @Test
  @TestSecurity(
      user = "placeholder",
      roles = {"test"})
  void whenCreateContract_thenCreatedContractShouldBeReturned() {
    Contract newContract = new Contract();
    newContract.setOrgId("org123");
    ContractResponse response = new ContractResponse();
    response.setContract(newContract);
    when(contractService.createContract(any())).thenReturn(response);
    ContractRequest request = new ContractRequest();
    request.setPartnerEntitlementContract(new PartnerEntitlementContract());
    request.setPartnerEntitlement(new PartnerEntitlementV1());
    request.setSubscriptionId("any");
    given()
        .contentType(ContentType.JSON)
        .body(request)
        .when()
        .post("/api/swatch-contracts/internal/contracts")
        .then()
        .statusCode(HttpStatus.SC_OK);
  }

  @Test
  @TestSecurity(
      user = "placeholder",
      roles = {"test"})
  void whenDeleteContract_thenSuccess() {
    given()
        .contentType(ContentType.JSON)
        .when()
        .delete("/api/swatch-contracts/internal/contracts/123")
        .then()
        .statusCode(HttpStatus.SC_NO_CONTENT);
  }

  @Test
  @TestSecurity(
      user = "placeholder",
      roles = {"test"})
  void createPartnerEntitlementContract() {
    StatusResponse statusResponse = new StatusResponse();
    statusResponse.setMessage("Contract created successfully");
    when(contractService.createPartnerContract(any())).thenReturn(statusResponse);
    String contract =
        """
                    {
                      "action" : "contract-updated",
                      "redHatSubscriptionNumber" : "12400374",
                      "currentDimensions" : [ {
                        "dimensionName" : "test_dim_1",
                        "dimensionValue" : "5",
                        "expirationDate" : "2023-02-15T00:00:00Z"
                      }, {
                        "dimensionName" : "test_dim_2",
                        "dimensionValue" : "10",
                        "expirationDate" : "2023-02-15T00:00:00Z"
                      } ],
                      "cloudIdentifiers" : {
                        "awsCustomerId" : "EK57ooq39qs",
                        "awsCustomerAccountId" : "896801664647",
                        "productCode" : "ek1lel8qbwnqimt2wogc5nmey"
                      }
                    }
                    """;
    given()
        .contentType(ContentType.JSON)
        .body(contract)
        .when()
        .post("/api/swatch-contracts/internal/rpc/partner/contracts")
        .then()
        .statusCode(HttpStatus.SC_OK);
  }

  @Test
  @TestSecurity(
      user = "placeholder",
      roles = {"test"})
  void deleteContractsByOrgId() {
    given()
        .when()
        .delete("/api/swatch-contracts/internal/rpc/reset/contracts/org123")
        .then()
        .statusCode(HttpStatus.SC_NO_CONTENT);
    verify(contractService).deleteContractsByOrgId("org123");
  }
}
