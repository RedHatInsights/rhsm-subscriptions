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
package org.candlepin.subscriptions.resource.api;

import org.candlepin.subscriptions.utilization.admin.api.InternalSubscriptionSyncOpenapiJsonApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Serves the OpenAPI json for the internal subscription sync API */
@Component
public class InternalSubscriptionSyncApiJsonResource
    implements InternalSubscriptionSyncOpenapiJsonApi {
  @Autowired ApiSpecController controller;

  @Override
  public String getOpenApiJson() {
    return controller.getInternalSubSyncApiJson();
  }
}
