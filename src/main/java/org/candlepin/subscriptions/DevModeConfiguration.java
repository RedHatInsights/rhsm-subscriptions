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

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Contains beans that are specific to dev-mode.
 *
 * <p>- Disables the anti-csrf filter. - Enables scheduling of tasks
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "rhsm-subscriptions", name = "dev-mode", havingValue = "true")
@ComponentScan(
    basePackages = {
      "org.candlepin.subscriptions.tally.job", // for the tally job
      "org.candlepin.subscriptions.retention" // for the retention job
    },
    // Prevent TestConfiguration annotated classes from being picked up by ComponentScan
    excludeFilters = {
      @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
      @ComponentScan.Filter(
          type = FilterType.CUSTOM,
          classes = AutoConfigurationExcludeFilter.class)
    })
public class DevModeConfiguration {

  private static final Logger log = LoggerFactory.getLogger(DevModeConfiguration.class);

  @PostConstruct
  void logDevMode() {
    log.info("Dev-mode enabled.");
  }

  /**
   * Create a thread pool for task scheduling if running in dev-mode
   *
   * @return ThreadPoolTaskScheduler
   */
  @Bean
  public TaskScheduler poolScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setThreadNamePrefix("ThreadPoolTaskScheduler");
    scheduler.setPoolSize(4);
    scheduler.initialize();
    return scheduler;
  }
}
