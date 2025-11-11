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

import com.redhat.swatch.component.tests.api.MessageValidator;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregate;

public class MessageValidators {

  public static MessageValidator<BillableUsage> billableUsageMatches(
      String orgId, String productId) {
    return new MessageValidator<>(
        usage -> orgId.equals(usage.getOrgId()) && productId.equals(usage.getProductId()),
        BillableUsage.class);
  }

  public static MessageValidator<BillableUsage> billableUsageMatchesWithValue(
      String orgId, String productId, double expectedValue) {
    return new MessageValidator<>(
        usage ->
            orgId.equals(usage.getOrgId())
                && productId.equals(usage.getProductId())
                && Math.abs(usage.getValue() - expectedValue) < 0.001,
        BillableUsage.class);
  }

  public static MessageValidator<BillableUsageAggregate> aggregateMatches(
      String orgId, BillableUsage.Status status) {
    return new MessageValidator<>(
        aggregate ->
            orgId.equals(aggregate.getAggregateKey().getOrgId())
                && status.equals(aggregate.getStatus()),
        BillableUsageAggregate.class);
  }

  public static MessageValidator<BillableUsage> alwaysMatchBillableUsage() {
    return new MessageValidator<>(usage -> true, BillableUsage.class);
  }

  public static MessageValidator<BillableUsageAggregate> alwaysMatchAggregate() {
    return new MessageValidator<>(agg -> true, BillableUsageAggregate.class);
  }
}
