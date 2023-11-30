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
package org.candlepin.subscriptions;

import org.candlepin.subscriptions.clowder.RdsSslBeanPostProcessor;
import org.candlepin.subscriptions.validator.IpAddressValidator;
import org.candlepin.subscriptions.validator.MacAddressValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class SystemConduitConfiguration {

  /**
   * An instance of the MacAddressValidator that we use to detect NICs with bad MAC addresses.
   * Rather than having a ConduitFacts object that fails validation, we use an instance of
   * MacAddressValidator to skip NICs that have bad MAC addresses and therefore construct a
   * compliant ConduitFacts object that will pass bean validation later (since the relevant field in
   * ConduitFacts is tagged with the @MacAddress validation annotation).
   *
   * @return an instance of MacAddressValidator
   */
  @Bean
  public MacAddressValidator macAddressValidator() {
    return new MacAddressValidator();
  }

  /**
   * An instance of the ipAddressValidator that we use to detect NICs with bad IP addresses. Rather
   * than having a ConduitFacts object that fails validation, we use an instance of
   * IpAddressValidator to skip NICs that have bad IP addresses and therefore construct a compliant
   * ConduitFacts object that will pass bean validation later (since the relevant field in
   * ConduitFacts are tagged with the @IpAddress validation annotation).
   *
   * @return an instance of MacAddressValidator
   */
  @Bean
  public IpAddressValidator ipAddressValidator() {
    return new IpAddressValidator();
  }

  /**
   * A bean post-processor responsible for setting up SSL for the database.
   *
   * @param env The Spring Environment
   * @return a rdsSslBeanPostProcessor object
   */
  @Bean
  public RdsSslBeanPostProcessor rdsSslBeanPostProcessor(Environment env) {
    return new RdsSslBeanPostProcessor(env);
  }
}
