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

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;

public class SimpleTallyComponentTest extends BaseTallyComponentTest {

  @Test
  public void testValidateCapacityReportApiWithoutHeader() {
    // Setup test parameters
    String productId = "RHEL for x86";
    String metricId = "Sockets";
    String granularity = "DAILY";

    OffsetDateTime ending = OffsetDateTime.now();
    OffsetDateTime beginning = ending.minusDays(30);

    String beginningStr = beginning.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

    // Call the capacity report API without the x-rh-identity header and verify 401 status
    service
        .given()
        .queryParam("granularity", granularity)
        .queryParam("beginning", beginningStr)
        .when()
        .get("/v1/capacity/products/{productId}/{metricId}", productId, metricId)
        .then()
        .statusCode(401);
  }
}
