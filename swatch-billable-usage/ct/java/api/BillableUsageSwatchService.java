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
import io.restassured.common.mapper.TypeRef;
import java.util.List;
import org.apache.http.HttpStatus;

/** Service class for billable usage API operations in component tests */
public class BillableUsageSwatchService extends SwatchService {

  private static final String ENDPOINT_PREFIX = "/api/swatch-billable-usage/internal";
  private static final String REMITTANCE_ENDPOINT =
      ENDPOINT_PREFIX + "/remittance/accountRemittances";
  private static final String REMITTANCE_ENDPOINT_BY_TALLY_ID =
      ENDPOINT_PREFIX + "/remittance/accountRemittances/{tallyId}";
  private static final String FLUSH_ENDPOINT = ENDPOINT_PREFIX + "/rpc/topics/flush";

  /**
   * Get remittances by tally ID using internal API
   *
   * @param tallyId the tally ID to look up
   * @return list of remittances for the given tally ID
   */
  public List<TallyRemittance> getRemittancesByTallyId(String tallyId) {
    return given()
        .get(REMITTANCE_ENDPOINT_BY_TALLY_ID, tallyId)
        .then()
        .statusCode(HttpStatus.SC_OK)
        .extract()
        .as(new TypeRef<List<TallyRemittance>>() {});
  }

  /**
   * Get monthly remittances for an account (product, org, metric, billing provider, billing
   * account). Use to verify remittances per accumulation period (e.g. last month vs current month).
   *
   * @param productId product ID
   * @param orgId organization ID
   * @param metricId metric ID
   * @param billingProvider billing provider
   * @param billingAccountId billing account ID
   * @return list of monthly remittances for the account
   */
  public List<MonthlyRemittance> getRemittances(
      String productId,
      String orgId,
      String metricId,
      String billingProvider,
      String billingAccountId) {
    return given()
        .queryParam("productId", productId)
        .queryParam("orgId", orgId)
        .queryParam("metricId", metricId)
        .queryParam("billingProvider", billingProvider)
        .queryParam("billingAccountId", billingAccountId)
        .get(REMITTANCE_ENDPOINT)
        .then()
        .statusCode(HttpStatus.SC_OK)
        .extract()
        .as(new TypeRef<List<MonthlyRemittance>>() {});
  }

  /** Flush Kafka topics using internal API. */
  public void flushBillableUsageAggregationTopic() {
    given().put(FLUSH_ENDPOINT).then().statusCode(HttpStatus.SC_OK);
  }
}
