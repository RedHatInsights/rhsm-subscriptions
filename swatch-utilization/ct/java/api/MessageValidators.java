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

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.swatch.component.tests.api.DefaultMessageValidator;

/** Message validators for notification messages in utilization component tests. */
public class MessageValidators {

  /** Matches notifications by organization ID. */
  public static DefaultMessageValidator<Action> matchesOrgId(String orgId) {
    return new DefaultMessageValidator<>(a -> orgId.equals(a.getOrgId()), Action.class);
  }

  /** Matches overage notifications by org_id, product_id, and metric_id. */
  public static DefaultMessageValidator<Action> matchesOverageNotification(
      String orgId, String productId, String metricId) {
    return new DefaultMessageValidator<>(
        action -> {
          if (!orgId.equals(action.getOrgId())) {
            return false;
          }
          var context = action.getContext();
          if (context == null) {
            return false;
          }
          var props = context.getAdditionalProperties();
          return productId.equals(props.get("product_id"))
              && metricId.equals(props.get("metric_id"));
        },
        Action.class);
  }
}
