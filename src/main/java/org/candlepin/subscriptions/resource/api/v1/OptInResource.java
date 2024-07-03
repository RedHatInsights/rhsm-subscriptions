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
package org.candlepin.subscriptions.resource.api.v1;

import jakarta.ws.rs.BadRequestException;
import org.candlepin.subscriptions.db.model.config.OptInType;
import org.candlepin.subscriptions.resource.ResourceUtils;
import org.candlepin.subscriptions.security.OptInController;
import org.candlepin.subscriptions.security.auth.SubscriptionWatchAdminOnly;
import org.candlepin.subscriptions.utilization.api.v1.model.OptInConfig;
import org.candlepin.subscriptions.utilization.api.v1.resources.OptInApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Defines the API for opting an account and its org into the sync/reporting functionality. */
@Component
public class OptInResource implements OptInApi {

  private OptInController controller;

  @Autowired
  public OptInResource(OptInController controller) {
    this.controller = controller;
  }

  @SubscriptionWatchAdminOnly
  @Override
  public void deleteOptInConfig() {
    controller.optOut(validateOrgId());
  }

  @SubscriptionWatchAdminOnly
  @Override
  public OptInConfig getOptInConfig() {
    return controller.getOptInConfig(validateOrgId());
  }

  @SubscriptionWatchAdminOnly
  @Override
  public OptInConfig putOptInConfig() {
    // NOTE: All query params are defaulted to 'true' by the API definition, however we
    //       double check below.
    return controller.optIn(validateOrgId(), OptInType.API);
  }

  private String validateOrgId() {
    String orgId = ResourceUtils.getOrgId();
    if (orgId == null) {
      throw new BadRequestException("Must specify an org ID.");
    }
    return orgId;
  }
}
