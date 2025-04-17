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

import com.redhat.swatch.clients.subscription.api.resources.SearchApi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.DeploymentException;
import java.util.function.Predicate;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class SearchApiFactory {

  @SearchClient
  @Produces
  public SearchApi getApi(
      @ConfigProperty(
              name = "rhsm-subscriptions.subscription-client.use-stub",
              defaultValue = "false")
          boolean useStub,
      Instance<SearchApi> searchApiInstances) {
    if (useStub) {
      return new StubSearchApi();
    }

    // Disambiguate the api implementation to exclude the stub
    return searchApiInstances.stream()
        .filter(Predicate.not(StubSearchApi.class::isInstance))
        .findFirst()
        .orElseThrow(() -> new DeploymentException("Default search rest client is not available"));
  }
}
