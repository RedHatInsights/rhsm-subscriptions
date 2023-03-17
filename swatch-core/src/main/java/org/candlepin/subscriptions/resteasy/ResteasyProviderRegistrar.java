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
package org.candlepin.subscriptions.resteasy;

import org.jboss.resteasy.plugins.providers.RegisterBuiltin;
import org.jboss.resteasy.plugins.spring.SpringBeanProcessor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * This is a hack to force spring boot to register "built-in" providers consistently
 *
 * <p>Built-in are those that are sourced from libraries. Without this, the BootApplication is able
 * to find providers from libraries, but @SpringBootTest tests are not.
 */
@Configuration
public class ResteasyProviderRegistrar {
  @Bean
  public SpringBeanProcessor registerBuiltInProviders(
      @Qualifier("ResteasyProviderFactory") BeanFactoryPostProcessor postProcessor) {
    var springBeanProcessor = (SpringBeanProcessor) postProcessor;
    RegisterBuiltin.register(springBeanProcessor.getProviderFactory());
    return springBeanProcessor;
  }
}
