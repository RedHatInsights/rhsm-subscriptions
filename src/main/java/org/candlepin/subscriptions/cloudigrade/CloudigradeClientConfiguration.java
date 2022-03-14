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
package org.candlepin.subscriptions.cloudigrade;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackages = "org.candlepin.subscriptions.cloudigrade")
public class CloudigradeClientConfiguration {

  @Bean
  @Qualifier("cloudigrade-internal")
  @ConfigurationProperties(prefix = "rhsm-subscriptions.cloudigrade.internal")
  public CloudigradeServiceProperties internalProperties() {
    return new CloudigradeServiceProperties();
  }

  @Bean
  @Qualifier("cloudigrade")
  @ConfigurationProperties(prefix = "rhsm-subscriptions.cloudigrade")
  public CloudigradeServiceProperties externalProperties() {
    return new CloudigradeServiceProperties();
  }

  @Bean
  public ConcurrentApiFactory concurrentApiFactory(
      @Qualifier("cloudigrade") CloudigradeServiceProperties props) {
    return new ConcurrentApiFactory(props);
  }

  @Bean
  public CloudigradeInternalUserApiFactory cloudigradeInternalUserApiFactory(
      @Qualifier("cloudigrade-internal") CloudigradeServiceProperties internalProperties) {
    return new CloudigradeInternalUserApiFactory(internalProperties);
  }
}
