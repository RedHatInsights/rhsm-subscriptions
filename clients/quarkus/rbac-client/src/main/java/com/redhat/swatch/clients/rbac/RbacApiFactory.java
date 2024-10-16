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
package com.redhat.swatch.clients.rbac;

import com.redhat.swatch.clients.rbac.api.resources.AccessApi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.util.regex.Pattern;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class RbacApiFactory {

  @RbacClient
  @Produces
  public RbacApi getApi(
      @ConfigProperty(name = "rhsm-subscriptions.rbac-service.use-stub", defaultValue = "false")
          boolean useStub,
      @ConfigProperty(
              name = "rhsm-subscriptions.rbac-service.stub-permissions",
              defaultValue = "subscriptions:*:*")
          String stubPermissions,
      @RestClient AccessApi accessApi) {
    if (useStub) {
      return new StubRbacApi(stubPermissions.split(Pattern.quote(",")));
    }

    return new RbacApiImpl(accessApi);
  }
}
