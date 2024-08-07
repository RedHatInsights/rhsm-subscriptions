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
package org.candlepin.subscriptions.resource.api.v2;

import lombok.AllArgsConstructor;
import org.candlepin.subscriptions.resource.api.ApiSpecController;
import org.candlepin.subscriptions.utilization.api.v2.resources.RootApi;
import org.springframework.stereotype.Component;

/** Serves the OpenAPI spec as /openapi.json and /openapi.yaml. */
@Component
@AllArgsConstructor
public class OpenApiResourceV2 implements RootApi {
  private final ApiSpecController controller;

  @Override
  public String getOpenApiJson() {
    return controller.getOpenApiV2Json();
  }

  @Override
  public String getOpenApiYaml() {
    return controller.getOpenApiV2Yaml();
  }
}
