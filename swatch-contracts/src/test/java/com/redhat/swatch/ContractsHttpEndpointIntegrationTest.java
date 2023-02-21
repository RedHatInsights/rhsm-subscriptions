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
package com.redhat.swatch;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.restassured.http.ContentType;
import java.util.List;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ContractsHttpEndpointIntegrationTest {

  @InjectMock ContractService contractService;

  @Test
  void whenGetContract_thenContractShouldBeFound() {
    com.redhat.swatch.openapi.model.Contract contract =
        new com.redhat.swatch.openapi.model.Contract();
    contract.setOrgId("org123");
    when(contractService.getContracts(any())).thenReturn(List.of(contract));
    given()
        .contentType(ContentType.JSON)
        .param("org_id", "org123")
        .when()
        .get("/api/swatch-contracts/internal/contracts")
        .then()
        .statusCode(200)
        .body("size()", is(1));
    // .body("contracts[0].orgId", is("org123"));
  }
}
