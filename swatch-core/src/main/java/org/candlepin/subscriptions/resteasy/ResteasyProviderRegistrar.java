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

import com.redhat.swatch.traceresponse.TraceResponseFilter;
import dev.resteasy.providers.jackson.JacksonFeature;
import org.jboss.resteasy.plugins.providers.RegisterBuiltin;
import org.jboss.resteasy.plugins.spring.SpringBeanProcessor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers RESTEasy providers for the Spring servlet integration.
 *
 * <p>Register built-in providers consistently in application and test contexts. Jackson 3 is
 * supplied by a separate Feature that must also be registered with our provider factory.
 */
@Configuration
public class ResteasyProviderRegistrar {
  @Bean
  public SpringBeanProcessor registerBuiltInProviders(
      @Qualifier("resteasySpringBeanProcessor") BeanFactoryPostProcessor postProcessor) {
    var springBeanProcessor = (SpringBeanProcessor) postProcessor;
    RegisterBuiltin.register(springBeanProcessor.getProviderFactory());
    if (!springBeanProcessor.getProviderFactory().isRegistered(JacksonFeature.class)) {
      springBeanProcessor.getProviderFactory().register(JacksonFeature.class);
    }
    return springBeanProcessor;
  }

  @Bean
  public TraceResponseFilter traceResponseFilter() {
    return new TraceResponseFilter();
  }
}
