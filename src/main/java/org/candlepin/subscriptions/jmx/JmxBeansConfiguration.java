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
package org.candlepin.subscriptions.jmx;

import org.candlepin.subscriptions.db.AccountConfigRepository;
import org.candlepin.subscriptions.db.model.OrgConfigRepository;
import org.candlepin.subscriptions.security.OptInController;
import org.candlepin.subscriptions.user.AccountService;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/** Configuration that provides admin JMX beans for tally and other functions. */
@Configuration
@ComponentScan(basePackages = "org.candlepin.subscriptions.jmx")
public class JmxBeansConfiguration {
  /* Define the opt-in controller in case we're running in a profile that doesn't define it */
  @Bean
  @ConditionalOnMissingBean(OptInController.class)
  OptInController optInController(
      ApplicationClock clock,
      AccountConfigRepository accountConfigRepo,
      OrgConfigRepository orgConfigRepo,
      AccountService accountService) {
    return new OptInController(clock, accountConfigRepo, orgConfigRepo, accountService);
  }
}
