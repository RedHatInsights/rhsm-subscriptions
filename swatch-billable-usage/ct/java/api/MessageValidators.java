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

import com.redhat.swatch.component.tests.api.DefaultMessageValidator;
import com.redhat.swatch.component.tests.api.MessageValidator;
import java.util.Set;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregate;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregateKey;

public class MessageValidators {

  public static DefaultMessageValidator<BillableUsage> billableUsageMatches(
      String orgId, String productId) {
    return new DefaultMessageValidator<>(
        usage -> orgId.equals(usage.getOrgId()) && productId.equals(usage.getProductId()),
        BillableUsage.class);
  }

  public static DefaultMessageValidator<BillableUsage> billableUsageMatchesWithValue(
      String orgId, String productId, double expectedValue) {
    return new DefaultMessageValidator<>(
        usage ->
            orgId.equals(usage.getOrgId())
                && productId.equals(usage.getProductId())
                && Math.abs(usage.getValue() - expectedValue) < 0.001,
        BillableUsage.class);
  }

  public static MessageValidator<BillableUsageAggregateKey, BillableUsageAggregate>
      billableUsageAggregateMatchesOrg(String orgId) {
    return new MessageValidator<>(
        (key, value) -> orgId.equals(key.getOrgId()),
        BillableUsageAggregateKey.class,
        BillableUsageAggregate.class);
  }

  public static DefaultMessageValidator<BillableUsage> billableUsageMatchesAnyOrg(
      Set<String> orgIds, String productId) {
    return new DefaultMessageValidator<>(
        usage -> orgIds.contains(usage.getOrgId()) && productId.equals(usage.getProductId()),
        BillableUsage.class);
  }

  public static MessageValidator<BillableUsageAggregateKey, BillableUsageAggregate>
      billableUsageAggregateMatchesAnyOrg(Set<String> orgIds) {
    return new MessageValidator<>(
        (key, value) -> key != null && orgIds.contains(key.getOrgId()),
        BillableUsageAggregateKey.class,
        BillableUsageAggregate.class);
  }
}
