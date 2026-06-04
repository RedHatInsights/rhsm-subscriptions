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
package org.candlepin.subscriptions.security;

import org.candlepin.subscriptions.rbac.RbacApiFactory;
import org.candlepin.subscriptions.rbac.RbacProperties;
import org.candlepin.subscriptions.rbac.RbacService;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configuration for consoledot RBAC service client */
@Configuration
public class RbacConfiguration {

  @Bean
  public RbacService rbacService() {
    return new RbacService();
  }

  @Bean
  @ConfigurationProperties(prefix = "rhsm-subscriptions.rbac-service")
  public RbacProperties rbacServiceProperties() {
    return new RbacProperties();
  }

  @Bean
  public RbacApiFactory rbacApiFactory(RbacProperties props) {
    return new RbacApiFactory(props);
  }
}
