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
package org.candlepin.subscriptions.files;

import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** Represents the idea of products in Subscription Watch and what family they slot into. */
@Getter
@Setter
@ToString
@EqualsAndHashCode
public class SubscriptionWatchProduct {
  private String engProductId;
  private Set<String> swatchProductIds;

  public SubscriptionWatchProduct() {
    // Required for YAML
  }

  public SubscriptionWatchProduct(String engProductId, Set<String> swatchProductIds) {
    this.engProductId = engProductId;
    this.swatchProductIds = swatchProductIds;
  }
}
