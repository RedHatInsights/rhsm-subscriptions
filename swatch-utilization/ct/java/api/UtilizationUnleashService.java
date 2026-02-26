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

public class UtilizationUnleashService extends UnleashService {
  private static final String SEND_NOTIFICATIONS = "swatch.swatch-notifications.send-notifications";
  private static final String SEND_NOTIFICATIONS_ORGS_ALLOWLIST =
      "swatch.swatch-notifications.send-notifications-orgs-whitelist";
  private static final String ORGS_VARIANT = "orgs";

  public void enableSendNotificationsFlag() {
    enableFlag(SEND_NOTIFICATIONS);
    waitForUnleashPropagation();
  }

  public void enableSendNotificationsFlagForOrgs(String... orgs) {
    enableFlag(SEND_NOTIFICATIONS_ORGS_ALLOWLIST);
    setVariant(SEND_NOTIFICATIONS_ORGS_ALLOWLIST, ORGS_VARIANT, String.join(",", orgs));
    waitForUnleashPropagation();
  }

  public void disableSendNotificationsFlag() {
    disableFlag(SEND_NOTIFICATIONS);
    waitForUnleashPropagation();
  }

  public void disableSendNotificationsFlagForOrgs() {
    clearVariants(SEND_NOTIFICATIONS_ORGS_ALLOWLIST);
    disableFlag(SEND_NOTIFICATIONS_ORGS_ALLOWLIST);
    waitForUnleashPropagation();
  }

  private void waitForUnleashPropagation() {
    // wait more than 1 sec, so the change is propagated to the app
    // the duration is configured using the property `quarkus.unleash.fetch-toggles-interval`
    // which uses 1 second
    try {
      Thread.sleep(2000);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
