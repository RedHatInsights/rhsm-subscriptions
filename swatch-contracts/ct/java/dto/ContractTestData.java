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
package dto;

import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Getter;

/** Test data object for building contract test scenarios in component tests. */
@Getter
@Builder
public class ContractTestData {
  private final String orgId;
  private final String subscriptionId;
  private final String subscriptionNumber;
  private final String awsCustomerId;
  private final String awsAccountId;
  private final String productCode;
  private final String sku;
  private final String metricName;
  private final String metricValue;
  private final String sellerAccountId;
  private final OffsetDateTime startDate;
  private final OffsetDateTime endDate;
  private final String sourcePartner;
}
