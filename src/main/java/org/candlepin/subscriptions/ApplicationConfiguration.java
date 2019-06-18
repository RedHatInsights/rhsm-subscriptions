/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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

import org.candlepin.insights.inventory.client.HostsApiFactory;
import org.candlepin.insights.inventory.client.InventoryServiceProperties;
import org.candlepin.subscriptions.jackson.ObjectMapperContextResolver;
import org.candlepin.subscriptions.retention.TallyRetentionPolicy;
import org.candlepin.subscriptions.tally.facts.RhelProductListSource;

import com.zaxxer.hikari.HikariDataSource;

import org.jboss.resteasy.springboot.ResteasyAutoConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.quartz.JobDetail;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.quartz.QuartzDataSource;
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.quartz.CronTriggerFactoryBean;
import org.springframework.scheduling.quartz.JobDetailFactoryBean;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;

import javax.sql.DataSource;
import javax.validation.Validator;
import java.util.Properties;


/** Class to hold configuration beans */
@Configuration
@Import(ResteasyAutoConfiguration.class) // needed to be able to reference ResteasyApplicationBuilder
@EnableConfigurationProperties(ApplicationProperties.class)
// The values in application.yaml should already be loaded by default
@PropertySource("classpath:/rhsm-subscriptions.properties")
public class ApplicationConfiguration implements WebMvcConfigurer {

    @Bean
    public ObjectMapperContextResolver objectMapperContextResolver(
        ApplicationProperties applicationProperties) {
        return new ObjectMapperContextResolver(applicationProperties);
    }

    @Bean
    @ConfigurationProperties(prefix = "rhsm-subscriptions.inventory-service")
    public InventoryServiceProperties inventoryServiceProperties() {
        return new InventoryServiceProperties();
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public TallyRetentionPolicy tallyRetentionPolicy(ApplicationProperties applicationProperties,
        Clock clock) {

        return new TallyRetentionPolicy(clock,
            applicationProperties.getTallyRetentionPolicy());
    }

    @Bean
    public HostsApiFactory hostsApiFactory(InventoryServiceProperties properties) {
        return new HostsApiFactory(properties);
    }

    /**
     * Tell Spring AOP to run methods in classes marked @Validated through the JSR-303 Validation
     * implementation.  Validations that fail will throw an ConstraintViolationException.
     * @return post-processor used by Spring AOP
     */
    @Bean
    public MethodValidationPostProcessor methodValidationPostProcessor() {
        return new MethodValidationPostProcessor();
    }

    @Bean
    public Validator validator() {
        return new LocalValidatorFactoryBean();
    }

    @Bean
    public static BeanFactoryPostProcessor servletInitializer() {
        return new JaxrsApplicationServletInitializer();
    }

    @Bean
    public RhelProductListSource rhelProductListSource(ApplicationProperties applicationProperties) {
        return new RhelProductListSource(applicationProperties);
    }

    @Bean
    @Primary
    @ConfigurationProperties("rhsm-subscriptions.datasource")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    @ConfigurationProperties("rhsm-subscriptions.datasource.configuration")
    public HikariDataSource dataSource(@Qualifier("dataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/api-docs").setViewName("redirect:/api-docs/index.html");
        registry.addViewController("/api-docs/").setViewName("redirect:/api-docs/index.html");
    }

    @Bean
    @ConfigurationProperties(prefix = "rhsm-subscriptions.quartz.datasource")
    public DataSourceProperties quartzDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @QuartzDataSource
    public DataSource quartzDataSource(
        @Qualifier("quartzDataSourceProperties") DataSourceProperties dataSourceProperties) {
        DataSourceBuilder builder = dataSourceProperties.initializeDataSourceBuilder();
        return builder.build();
    }

    @Bean
    public SchedulerFactoryBeanCustomizer schedulerFactoryBeanCustomizer(
        @Qualifier("quartzDataSourceProperties") DataSourceProperties properties) {
        String driverDelegate = "org.quartz.impl.jdbcjobstore.StdJDBCDelegate";
        if (properties.getPlatform().startsWith("postgres")) {
            driverDelegate = "org.quartz.impl.jdbcjobstore.PostgreSQLDelegate";
        }

        final String finalDriverDelegate = driverDelegate;
        return schedulerFactoryBean -> {
            Properties props = new Properties();
            props.put("org.quartz.jobStore.driverDelegateClass", finalDriverDelegate);
            schedulerFactoryBean.setQuartzProperties(props);
        };
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/api-docs/openapi.*").addResourceLocations(
            "classpath:openapi.yaml",
            "classpath:openapi.json"
        );
    }

}
