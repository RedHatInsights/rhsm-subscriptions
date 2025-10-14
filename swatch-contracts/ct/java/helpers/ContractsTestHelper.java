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

import dto.ContractDataDto;
import java.util.Objects;

/**
 * Helper class for creating contract request payloads in component tests.
 *
 * <p>This is a utility class with static methods only and cannot be instantiated.
 *
 * @see ContractDataDto
 */
public final class ContractsTestHelper {

  private ContractsTestHelper() {}

  /**
   * Create a contract request JSON payload using a DTO object.
   *
   * @param contractData the contract data DTO
   * @return JSON string representing the contract request payload
   */
  public static String createContractRequest(ContractDataDto contractData) {
    Objects.requireNonNull(contractData, "contractData must not be null");

    return """
        {
          "partner_entitlement": {
            "rhAccountId": "%s",
            "sourcePartner": "%s",
            "entitlementDates": {
              "startDate": "%s",
              "endDate": "%s"
            },
            "rhEntitlements": [
              {
                "subscriptionNumber": "%s",
                "sku": "%s"
              }
            ],
            "purchase": {
              "vendorProductCode": "%s",
              "contracts": [
                {
                  "dimensions": [
                    {
                      "name": "%s",
                      "value": "%s"
                    }
                  ]
                }
              ]
            },
            "partnerIdentities": {
              "awsCustomerId": "%s",
              "sellerAccountId": "%s",
              "customerAwsAccountId": "%s"
            }
          },
          "subscription_id": "%s"
        }
        """
        .formatted(
            contractData.getOrgId(),
            "aws_marketplace",
            "2025-01-01T00:00:00Z",
            "2026-12-31T23:59:59Z",
            contractData.getSubscriptionNumber(),
            "MW02393",
            contractData.getProductCode(),
            "four_vcpu_hour",
            "10",
            contractData.getAwsCustomerId(),
            "123456789",
            contractData.getAwsAccountId(),
            contractData.getSubscriptionId());
  }
}
