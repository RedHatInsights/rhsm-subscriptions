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
package com.redhat.swatch.clients.subscription;

import com.redhat.swatch.clients.subscription.api.resources.DefaultApi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@Dependent
public class SearchApiFactory {

  @ApplicationScoped
  @Produces
  public SearchApi getApi(
      @ConfigProperty(
              name = "rhsm-subscriptions.subscription-client.use-stub",
              defaultValue = "false")
          boolean useStub,
      @RestClient DefaultApi searchApi) {
    if (useStub) {
      return new StubSearchApi();
    }

    return new DefaultSearchApi(searchApi);
  }
}
