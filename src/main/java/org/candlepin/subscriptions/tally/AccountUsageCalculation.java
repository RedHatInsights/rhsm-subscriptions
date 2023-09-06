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
package org.candlepin.subscriptions.tally;

import com.redhat.swatch.configuration.registry.MetricId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;

/** The calculated usage for an account. */
public class AccountUsageCalculation {

  private String account;
  private String orgId;
  private Map<UsageCalculation.Key, UsageCalculation> calculations;
  private Set<String> products;

  public AccountUsageCalculation(String orgId) {
    this.orgId = orgId;
    this.calculations = new HashMap<>();
    this.products = new HashSet<>();
  }

  public UsageCalculation getOrCreateCalculation(UsageCalculation.Key key) {
    UsageCalculation calc = getCalculation(key);
    if (calc == null) {
      calc = new UsageCalculation(key);
      addCalculation(calc);
    }
    return calc;
  }

  public String getAccount() {
    return account;
  }

  public void setAccount(String account) {
    this.account = account;
  }

  public String getOrgId() {
    return orgId;
  }

  public void addCalculation(UsageCalculation calc) {
    String productId = calc.getProductId();
    this.calculations.put(
        new UsageCalculation.Key(
            productId,
            calc.getSla(),
            calc.getUsage(),
            calc.getBillingProvider(),
            calc.getBillingAccountId()),
        calc);
    this.products.add(productId);
  }

  public void addUsage(
      UsageCalculation.Key key, HardwareMeasurementType category, MetricId metricId, Double value) {
    UsageCalculation usageCalculation = getOrCreateCalculation(key);
    usageCalculation.add(category, metricId, value);
    products.add(key.getProductId());
  }

  public boolean containsCalculation(UsageCalculation.Key key) {
    return this.calculations.containsKey(key);
  }

  public Set<UsageCalculation.Key> getKeys() {
    return this.calculations.keySet();
  }

  public Set<String> getProducts() {
    return this.products;
  }

  public UsageCalculation getCalculation(UsageCalculation.Key key) {
    return this.calculations.get(key);
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append(String.format("[Account: %s, OrgId: %s, Calculations: [", account, orgId));
    for (UsageCalculation calc : this.calculations.values()) {
      builder.append(calc);
    }
    builder.append("]");
    return builder.toString();
  }
}
