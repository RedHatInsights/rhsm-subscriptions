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
package services;

import com.redhat.swatch.component.tests.api.SwatchService;
import dto.OfferingTestData;
import io.restassured.response.Response;
import wiremock.OfferingWiremockService;

/** Service layer for offering-related component test workflows. */
public class OfferingTestService {

  private static final String OFFERING_SYNC_ENDPOINT_TEMPLATE =
      "/api/swatch-contracts/internal/rpc/offerings/sync/%s";

  private final SwatchService service;
  private final OfferingWiremockService offeringWiremock;

  public OfferingTestService(SwatchService service, OfferingWiremockService offeringWiremock) {
    this.service = service;
    this.offeringWiremock = offeringWiremock;
  }

  public void setupOffering(OfferingTestData offeringData) {
    stubUpstreamProductData(offeringData);
  }

  private void stubUpstreamProductData(OfferingTestData offeringData) {
    offeringWiremock.stubUpstreamProductData(
        offeringData.getSku(),
        offeringData.getDescription(),
        offeringData.getCores(),
        offeringData.getSockets(),
        offeringData.getLevel1(),
        offeringData.getLevel2(),
        offeringData.getMetered());
    offeringWiremock.stubEngineeringProducts(offeringData.getSku());
  }

  public Response syncOffering(String sku) {
    var endpoint = String.format(OFFERING_SYNC_ENDPOINT_TEMPLATE, sku);
    return service.given().when().put(endpoint);
  }
}
