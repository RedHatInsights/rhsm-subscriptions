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
package helpers;

import com.redhat.swatch.component.tests.api.SwatchService;
import dto.OfferingDataDto;
import java.util.Objects;
import wiremock.OfferingWiremockService;

/**
 * Helper class for setting up offering data in component tests. Provides methods to stub upstream
 * product APIs and sync offerings to the database.
 *
 * <p>This is a utility class with static methods only and cannot be instantiated.
 *
 * @see OfferingDataDto
 * @see OfferingWiremockService
 */
public final class OfferingTestHelper {

  private static final String OFFERING_SYNC_ENDPOINT_TEMPLATE =
      "/api/swatch-contracts/internal/rpc/offerings/sync/%s";

  private OfferingTestHelper() {}

  /**
   * Setup offering data using a DTO object.
   *
   * @param offeringWiremock the WireMock service for stubbing upstream product APIs
   * @param service the Swatch service for calling the sync API
   * @param offeringData the offering data DTO
   */
  public static void setupOfferingData(
      OfferingWiremockService offeringWiremock,
      SwatchService service,
      OfferingDataDto offeringData) {
    Objects.requireNonNull(offeringWiremock, "offeringWiremock must not be null");
    Objects.requireNonNull(service, "service must not be null");
    Objects.requireNonNull(offeringData, "offeringData must not be null");

    stubUpstreamProductData(offeringWiremock, offeringData);
    syncOffering(service, offeringData.getSku());
  }

  /**
   * Stub the upstream product service with the provided offering data.
   *
   * @param offeringWiremock the WireMock service for stubbing upstream product APIs
   * @param offeringData the offering data to stub
   */
  private static void stubUpstreamProductData(
      OfferingWiremockService offeringWiremock, OfferingDataDto offeringData) {
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

  /**
   * Sync the offering from the mocked upstream source to populate the tables.
   *
   * @param service the Swatch service for calling the sync API
   * @param sku the SKU to sync
   */
  private static void syncOffering(SwatchService service, String sku) {
    var endpoint = String.format(OFFERING_SYNC_ENDPOINT_TEMPLATE, sku);
    service.given().when().put(endpoint).then().statusCode(200);
  }
}
