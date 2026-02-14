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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static utils.TallyTestProducts.RHEL_FOR_X86_ELS_PAYG;

import io.restassured.response.Response;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;
import utils.TallyDbHostSeeder;

@Slf4j
public class TallyInstancesComponentTest extends BaseTallyComponentTest {

  @Test
  public void testGetBillingAccountIdsCurrentMonthBoundaryViaInstances() {
    // Given: A host with a last_seen date from last month (35 days ago)
    final String testInventoryId = UUID.randomUUID().toString();
    final String billingAccountId = UUID.randomUUID().toString();
    final OffsetDateTime lastMonthDate = OffsetDateTime.now().minusDays(35);

    service.createOptInConfig(orgId);

    TallyDbHostSeeder.insertHostWithBillingAccountAndDate(
        orgId, testInventoryId, RHEL_FOR_X86_ELS_PAYG.productTag(), "AWS", billingAccountId, lastMonthDate);

    // When: Calling get billing account ids
    Map<String, Object> queryParams = new HashMap<>();
    Response response = service.getBillingAccountIds(orgId, queryParams);

    // Then: Response should not contain the billing account from last month
    List<Map<String, String>> ids = response.jsonPath().getList("ids");
    assertNotNull(ids, "Response ids should not be null");

    boolean containsOldAccount =
        ids.stream()
            .anyMatch(
                entry ->
                    orgId.equals(entry.get("org_id"))
                        && billingAccountId.equals(entry.get("billing_account_id"))
                        && RHEL_FOR_X86_ELS_PAYG.productTag().equals(entry.get("product_tag"))
                        && "aws".equals(entry.get("billing_provider")));

    assertFalse(
        containsOldAccount,
        "Response should not contain billing account from last month: " + billingAccountId);
  }

  @Test
  public void testGetBillingAccountIdsViaInstances() {
    // Given: Two hosts with different billing account IDs
    final String testInventoryId1 = UUID.randomUUID().toString();
    final String testInventoryId2 = UUID.randomUUID().toString();
    final String billingAccountId1 = UUID.randomUUID().toString();
    final String billingAccountId2 = UUID.randomUUID().toString();

    service.createOptInConfig(orgId);

    TallyDbHostSeeder.insertHostWithBillingAccount(
        orgId, testInventoryId1, RHEL_FOR_X86_ELS_PAYG.productTag(), "AWS", billingAccountId1);

    TallyDbHostSeeder.insertHostWithBillingAccount(
        orgId, testInventoryId2, RHEL_FOR_X86_ELS_PAYG.productTag(), "AWS", billingAccountId2);

    // When: Calling get billing account ids
    Map<String, Object> queryParams = new HashMap<>();
    Response response = service.getBillingAccountIds(orgId, queryParams);

    // Then: Response contains both billing account IDs with correct structure
    List<Map<String, String>> ids = response.jsonPath().getList("ids");
    assertNotNull(
        ids, "Response ids should not be null. Response body: " + response.getBody().asString());
    assertEquals(2, ids.size(), "Should return two billing account entries");

    List<String> billingAccountIds =
        ids.stream().map(item -> item.get("billing_account_id")).toList();

    assertTrue(
        billingAccountIds.contains(billingAccountId1),
        "Response should contain billing account ID: " + billingAccountId1);
    assertTrue(
        billingAccountIds.contains(billingAccountId2),
        "Response should contain billing account ID: " + billingAccountId2);

    for (Map<String, String> entry : ids) {
      assertEquals(orgId, entry.get("org_id"), "Entry should have correct org_id");
      assertEquals(
          RHEL_FOR_X86_ELS_PAYG.productTag(),
          entry.get("product_tag"),
          "Entry should have correct product_tag");
      assertEquals(
          "aws", entry.get("billing_provider"), "Entry should have correct billing_provider");
    }
  }
}
