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
package org.candlepin.subscriptions.capacity;

import org.candlepin.subscriptions.db.RhsmSubscriptionsDataSourceConfiguration;
import org.candlepin.subscriptions.resteasy.ResteasyConfiguration;
import org.candlepin.subscriptions.subscription.export.ExportSubscriptionConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Configuration for the "capacity-ingress" profile.
 *
 * <p>This profile is used to receive capacity records from an internal service.
 */
@Configuration
@Profile("capacity-ingress")
@EnableAsync
@Import({
  ResteasyConfiguration.class,
  RhsmSubscriptionsDataSourceConfiguration.class,
  ExportSubscriptionConfiguration.class
})
@ComponentScan(
    basePackages = {"org.candlepin.subscriptions.capacity", "org.candlepin.subscriptions.product"},
    // Prevent TestConfiguration annotated classes from being picked up by ComponentScan
    excludeFilters = {
      @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
      @ComponentScan.Filter(
          type = FilterType.CUSTOM,
          classes = AutoConfigurationExcludeFilter.class)
    })
public class CapacityIngressConfiguration {
  /* Intentionally empty */
}
