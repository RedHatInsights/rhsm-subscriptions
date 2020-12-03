/*
 * Copyright (c) 2019 - 2019 Red Hat, Inc.
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

import org.candlepin.subscriptions.ApplicationProperties;

import org.jboss.resteasy.springboot.ResteasyAutoConfiguration;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration of Resteasy.
 *
 * Should be imported by any component that needs to serve an API.
 */
@Configuration
@Import(ResteasyAutoConfiguration.class)
@ComponentScan(basePackages = {"org.candlepin.subscriptions.exception.mapper",
    "org.candlepin.subscriptions.resteasy"})
public class ResteasyConfiguration implements WebMvcConfigurer {
    @Bean
    @Primary
    public ObjectMapperContextResolver objectMapperContextResolver(
        ApplicationProperties applicationProperties) {
        return new ObjectMapperContextResolver(applicationProperties);
    }

    @Bean
    public static BeanFactoryPostProcessor servletInitializer() {
        return new JaxrsApplicationServletInitializer();
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/api-docs").setViewName("redirect:/api-docs/index.html");
        registry.addViewController("/api-docs/").setViewName("redirect:/api-docs/index.html");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/api-docs/openapi.*").addResourceLocations(
            "classpath:openapi.yaml",
            "classpath:openapi.json"
        );
    }
}
