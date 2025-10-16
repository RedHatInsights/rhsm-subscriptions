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
import io.restassured.response.Response;
import java.util.Objects;

/** Helper class for offering related operations in component tests. */
public final class OfferingTestHelper {

  private static final String OFFERING_SYNC_ENDPOINT =
      "/api/swatch-contracts/internal/rpc/offerings/sync/%s";

  private OfferingTestHelper() {}

  public static Response syncOffering(SwatchService service, String sku) {
    Objects.requireNonNull(service, "service must not be null");
    Objects.requireNonNull(sku, "sku must not be null");

    String endpoint = String.format(OFFERING_SYNC_ENDPOINT, sku);
    return service.given().when().put(endpoint);
  }
}
