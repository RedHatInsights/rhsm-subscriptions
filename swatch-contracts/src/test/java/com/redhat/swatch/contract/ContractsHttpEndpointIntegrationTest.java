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
import static org.mockito.Mockito.when;

import com.redhat.swatch.contract.openapi.model.Contract;
import com.redhat.swatch.contract.openapi.model.StatusResponse;
import com.redhat.swatch.contract.service.ContractService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@QuarkusTest
@Tag("integration")
class ContractsHttpEndpointIntegrationTest {

  @InjectMock ContractService contractService;

  @Test
  @TestSecurity(
      user = "placeholder",
      roles = {"service"})
  void whenGetContract_thenContractShouldBeFound() {
    Contract contract = new Contract();
    contract.setOrgId("org123");
    when(contractService.getContracts(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(List.of(contract));
    given()
        .contentType(ContentType.JSON)
        .param("org_id", "org123")
        .when()
        .get("/api/swatch-contracts/internal/contracts")
        .then()
        .statusCode(200)
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
    when(contractService.createContract(any())).thenReturn(newContract);
    String contract =
        """
        {"uuid":"string","subscription_number":"string","sku":"string",
        "start_date":"2022-03-10T12:15:50-04:00","end_date":"2022-03-10T12:15:50-04:00",
        "org_id":"string","billing_provider":"string","billing_account_id":"string",
        "product_id":"string","vendor_product_code":"string","metrics": [ {"metric_id":"string","value": 0 } ] }
        """;
    given()
        .contentType(ContentType.JSON)
        .body(contract)
        .when()
        .post("/api/swatch-contracts/internal/contracts")
        .then()
        .statusCode(200);
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
        .statusCode(204);
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
        .statusCode(200);
  }
}
