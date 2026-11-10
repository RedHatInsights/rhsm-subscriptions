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
package api;

import com.redhat.swatch.billable.usage.openapi.model.MonthlyRemittance;
import com.redhat.swatch.billable.usage.openapi.model.TallyRemittance;
import com.redhat.swatch.component.tests.api.SwatchService;
import com.redhat.swatch.configuration.registry.MetricId;
import domain.Product;
import io.restassured.common.mapper.TypeRef;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.apache.http.HttpStatus;

public class BillableUsageSwatchService extends SwatchService {

  private static final String ENDPOINT_PREFIX = "/api/swatch-billable-usage/internal";
  private static final String REMITTANCE_ENDPOINT =
      ENDPOINT_PREFIX + "/remittance/accountRemittances";
  private static final String REMITTANCE_ENDPOINT_BY_TALLY_ID =
      ENDPOINT_PREFIX + "/remittance/accountRemittances/{tallyId}";
  private static final String FLUSH_ENDPOINT = ENDPOINT_PREFIX + "/rpc/topics/flush";
  private static final String DELETE_REMITTANCES_ENDPOINT =
      ENDPOINT_PREFIX + "/rpc/remittance/{orgId}";

  public List<TallyRemittance> getRemittancesByTally(UUID tallyId) {
    Objects.requireNonNull(tallyId, "tallyId must not be null");
    return given()
        .get(REMITTANCE_ENDPOINT_BY_TALLY_ID, tallyId.toString())
        .then()
        .statusCode(HttpStatus.SC_OK)
        .extract()
        .as(new TypeRef<List<TallyRemittance>>() {});
  }

  public List<MonthlyRemittance> getRemittances(
      String orgId,
      Product product,
      MetricId metricId,
      String billingProvider,
      String billingAccountId) {
    return given()
        .queryParam("orgId", orgId)
        .queryParam("productId", product.getName())
        .queryParam("metricId", metricId.toString())
        .queryParam("billingProvider", billingProvider)
        .queryParam("billingAccountId", billingAccountId)
        .get(REMITTANCE_ENDPOINT)
        .then()
        .statusCode(HttpStatus.SC_OK)
        .extract()
        .as(new TypeRef<List<MonthlyRemittance>>() {});
  }

  public void flushBillableUsageAggregationTopic() {
    given().post(FLUSH_ENDPOINT).then().statusCode(HttpStatus.SC_OK);
  }

  public void deleteRemittancesAssociatedWithOrg(String orgId) {
    Objects.requireNonNull(orgId, "orgId must not be null");
    given().delete(DELETE_REMITTANCES_ENDPOINT, orgId).then().statusCode(HttpStatus.SC_OK);
  }
}
