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
package com.redhat.swatch.contract.resource;

import com.redhat.swatch.contract.repository.BillingProvider;
import com.redhat.swatch.contract.repository.ServiceLevel;
import com.redhat.swatch.contract.repository.Usage;
import java.util.Objects;

/** Functionality common to both capacity and tally resources. */
public class ResourceUtils {

  public static final String ANY = "_ANY";

  private ResourceUtils() {
    throw new IllegalStateException("Utility class; should never be instantiated!");
  }

  /**
   * Uses Usage.ANY for a null value
   *
   * @param usageType nullable Usage value
   * @return Usage enum
   */
  public static Usage sanitizeUsage(Usage usageType) {
    return Objects.isNull(usageType) ? Usage._ANY : usageType;
  }

  /**
   * Uses ServiceLevel.ANY for a null value
   *
   * @param sla nullable ServiceLevel
   * @return ServiceLevel enum
   */
  public static ServiceLevel sanitizeServiceLevel(ServiceLevel sla) {
    return Objects.isNull(sla) ? ServiceLevel._ANY : sla;
  }

  /**
   * Uses BillingProvider.ANY for a null value
   *
   * @param billingProvider nullable billingProvider
   * @return BilligProvider enum
   */
  public static BillingProvider sanitizeBillingProvider(BillingProvider billingProvider) {
    return Objects.isNull(billingProvider) ? BillingProvider._ANY : billingProvider;
  }

  public static String sanitizeBillingAccountId(String billingAccountId) {

    return Objects.isNull(billingAccountId) || billingAccountId.isBlank() ? ANY : billingAccountId;
  }

  /**
   * Simple method to get around sonar complaint: java:S5411 - Boxed "Boolean" should be avoided in
   * boolean expressions
   */
  public static boolean sanitizeBoolean(Boolean value, boolean defaultValue) {
    if (Objects.isNull(value)) {
      return defaultValue;
    }
    return value;
  }
}
