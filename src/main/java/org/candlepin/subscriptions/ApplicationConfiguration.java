/*
 * Copyright (c) 2019 Red Hat, Inc.
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

import org.candlepin.subscriptions.cloudigrade.ConcurrentApiFactory;
import org.candlepin.subscriptions.db.AccountConfigRepository;
import org.candlepin.subscriptions.files.FileAccountListSource;
import org.candlepin.subscriptions.files.FileAccountSyncListSource;
import org.candlepin.subscriptions.files.ProductIdToProductsMapSource;
import org.candlepin.subscriptions.files.ReportingAccountWhitelist;
import org.candlepin.subscriptions.files.RoleToProductsMapSource;
import org.candlepin.subscriptions.http.HttpClientProperties;
import org.candlepin.subscriptions.jackson.ObjectMapperContextResolver;
import org.candlepin.subscriptions.rbac.RbacApiFactory;
import org.candlepin.subscriptions.retention.TallyRetentionPolicy;
import org.candlepin.subscriptions.retention.TallyRetentionPolicyProperties;
import org.candlepin.subscriptions.tally.AccountListSource;
import org.candlepin.subscriptions.tally.DatabaseAccountListSource;
import org.candlepin.subscriptions.tally.facts.FactNormalizer;
import org.candlepin.subscriptions.util.ApplicationClock;

import org.jboss.resteasy.springboot.ResteasyAutoConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.StringUtils;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import javax.validation.Validator;

/** Class to hold configuration beans */
@Configuration
@Import(ResteasyAutoConfiguration.class) // needed to be able to reference ResteasyApplicationBuilder
@EnableRetry
@EnableAspectJAutoProxy
@EnableScheduling
public class ApplicationConfiguration implements WebMvcConfigurer {

    @Bean
    public ObjectMapperContextResolver objectMapperContextResolver(
        ApplicationProperties applicationProperties) {
        return new ObjectMapperContextResolver(applicationProperties);
    }

    @Bean
    public ApplicationClock applicationClock() {
        return new ApplicationClock();
    }

    @Bean
    public TallyRetentionPolicy tallyRetentionPolicy(ApplicationClock applicationClock,
        TallyRetentionPolicyProperties retentionPolicyProperties) {

        return new TallyRetentionPolicy(applicationClock, retentionPolicyProperties);
    }

    @Bean
    @Qualifier("rbac")
    @ConfigurationProperties(prefix = "rhsm-subscriptions.rbac-service")
    public HttpClientProperties rbacServiceProperties() {
        return new HttpClientProperties();
    }

    @Bean
    public RbacApiFactory rbacApiFactory(@Qualifier("rbac") HttpClientProperties props) {
        return new RbacApiFactory(props);
    }

    @Bean
    @Qualifier("cloudigrade")
    @ConfigurationProperties(prefix = "rhsm-subscriptions.cloudigrade")
    public HttpClientProperties cloudigradeServiceProperties() {
        return new HttpClientProperties();
    }

    @Bean
    public ConcurrentApiFactory concurrentApiFactory(@Qualifier("cloudigrade") HttpClientProperties props) {
        return new ConcurrentApiFactory(props);
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
    public AccountListSource accountListSource(ApplicationProperties applicationProperties,
        AccountConfigRepository accountConfigRepository, ApplicationClock clock) {
        if (StringUtils.hasText(applicationProperties.getAccountListResourceLocation())) {
            return new FileAccountListSource(
                new FileAccountSyncListSource(applicationProperties, clock),
                new ReportingAccountWhitelist(applicationProperties, clock)
            );
        }
        else {
            return new DatabaseAccountListSource(accountConfigRepository);
        }
    }

    @Bean
    public FactNormalizer factNormalizer(ApplicationProperties applicationProperties,
        ProductIdToProductsMapSource productIdToProductsMapSource,
        RoleToProductsMapSource productToRolesMapSource,
        ApplicationClock clock) throws IOException {
        return new FactNormalizer(applicationProperties, productIdToProductsMapSource,
            productToRolesMapSource, clock);
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

    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

    @Bean(name = "collectorRetryTemplate")
    public RetryTemplate collectorRetryTemplate() {
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(4);

        FixedBackOffPolicy backOffPolicy = new FixedBackOffPolicy();
        backOffPolicy.setBackOffPeriod(2000L);

        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(retryPolicy);
        retryTemplate.setBackOffPolicy(backOffPolicy);
        return retryTemplate;
    }

    @Bean(name = "cloudigradeRetryTemplate")
    public RetryTemplate cloudigradeRetryTemplate(ApplicationProperties applicationProperties) {
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(applicationProperties.getCloudigradeMaxAttempts());

        FixedBackOffPolicy backOffPolicy = new FixedBackOffPolicy();
        backOffPolicy.setBackOffPeriod(2000L);

        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(retryPolicy);
        retryTemplate.setBackOffPolicy(backOffPolicy);
        return retryTemplate;
    }

    @Bean(name = "applicableProducts")
    public Set<String> applicableProducts(ProductIdToProductsMapSource productIdToProductsMapSource,
        RoleToProductsMapSource roleToProductsMapSource) throws IOException {
        Set<String> products = new HashSet<>();
        productIdToProductsMapSource.getValue().values().forEach(products::addAll);
        roleToProductsMapSource.getValue().values().forEach(products::addAll);
        return products;
    }
}
