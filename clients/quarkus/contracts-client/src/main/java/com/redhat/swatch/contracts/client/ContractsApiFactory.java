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
package com.redhat.swatch.contracts.client;

import com.redhat.swatch.clients.contracts.api.resources.DefaultApi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.DeploymentException;
import java.util.function.Predicate;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class ContractsApiFactory {

  @ContractsClient
  @Produces
  public DefaultApi getApi(
      @ConfigProperty(name = "rhsm-subscriptions.contracts.use-stub", defaultValue = "false")
          boolean useStub,
      Instance<DefaultApi> contractsApiInstances) {
    if (useStub) {
      return new StubContractsApi();
    }

    // Disambiguate the api implementation to exclude the stub
    return contractsApiInstances.stream()
        .filter(Predicate.not(StubContractsApi.class::isInstance))
        .findFirst()
        .orElseThrow(
            () -> new DeploymentException("Default contracts rest client is not available"));
  }
}
