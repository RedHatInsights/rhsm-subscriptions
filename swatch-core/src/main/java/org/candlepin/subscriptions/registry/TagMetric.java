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
package org.candlepin.subscriptions.registry;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** A composite class for tag profiles. Describes tag metric information. */
@AllArgsConstructor
@Builder
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Setter
@ToString
public class TagMetric {
  private String tag;
  private String metricId;
  private String rhmMetricId;
  private String awsDimension;
  @Deprecated private String uom;
  @Default private Double billingFactor = 1.0;
  @Default private BillingWindow billingWindow = BillingWindow.MONTHLY;
  @Default private String queryKey = "default";
  @Default private String accountQueryKey = "default";
  private Map<String, String> queryParams;
}
