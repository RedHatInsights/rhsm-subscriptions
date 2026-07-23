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

import com.redhat.swatch.component.tests.api.UnleashService;
import com.redhat.swatch.component.tests.utils.JsonUtils;
import com.redhat.swatch.utilization.model.SendNotificationVariantPayload;
import java.util.List;

public class UtilizationUnleashService extends UnleashService {
  private static final String SEND_NOTIFICATIONS = "swatch.swatch-notifications.send-notifications";
  private static final String SEND_NOTIFICATIONS_CONFIG_VARIANT = "send-notifications-config";
  private static final String SEND_NOTIFICATIONS_ORGS_ALLOWLIST =
      "swatch.swatch-notifications.send-notifications-orgs-allowlist";
  private static final String ORGS_VARIANT = "orgs";

  public void enableSendNotificationsFlag() {
    enableFlag(SEND_NOTIFICATIONS);
  }

  public void enableSendNotificationsFlagForOrgs(String... orgs) {
    enableFlag(SEND_NOTIFICATIONS_ORGS_ALLOWLIST);
    setVariant(SEND_NOTIFICATIONS_ORGS_ALLOWLIST, ORGS_VARIANT, String.join(",", orgs));
  }

  public void disableSendNotificationsFlag() {
    disableFlag(SEND_NOTIFICATIONS);
  }

  public void disableSendNotificationsFlagForOrgs() {
    clearVariants(SEND_NOTIFICATIONS_ORGS_ALLOWLIST);
    disableFlag(SEND_NOTIFICATIONS_ORGS_ALLOWLIST);
  }

  public void enableSendNotificationsWithEventTypesDenylist(String... eventTypes) {
    enableFlag(SEND_NOTIFICATIONS);
    setVariant(
        SEND_NOTIFICATIONS,
        SEND_NOTIFICATIONS_CONFIG_VARIANT,
        buildEventTypesDenylistJson(eventTypes));
  }

  public void clearSendNotificationsVariants() {
    clearVariants(SEND_NOTIFICATIONS);
  }

  private static String buildEventTypesDenylistJson(String... eventTypes) {
    var payload = new SendNotificationVariantPayload();
    payload.setEventTypesDenylist(List.of(eventTypes));
    return JsonUtils.toJson(payload);
  }
}
