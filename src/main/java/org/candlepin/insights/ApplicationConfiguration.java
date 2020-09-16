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
package org.candlepin.insights;

import org.candlepin.insights.jackson.ObjectMapperContextResolver;
import org.candlepin.insights.pinhead.client.PinheadApiFactory;
import org.candlepin.insights.pinhead.client.PinheadApiProperties;

import org.jboss.resteasy.springboot.ResteasyAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.ExponentialRandomBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.validation.Validator;

/** Class to hold configuration beans */
@Configuration
@EnableRetry
@EnableAspectJAutoProxy
@EnableScheduling
@Import(ResteasyAutoConfiguration.class) // needed to be able to reference ResteasyApplicationBuilder
// The values in application.yaml should already be loaded by default
@PropertySource("classpath:/rhsm-conduit.properties")
public class ApplicationConfiguration implements WebMvcConfigurer {

    @Autowired
    private ApplicationProperties applicationProperties;

    /**
     * Used to set context-param values since Spring Boot does not have a web.xml.  Technically
     * context-params can be set in application.properties (or application.yaml) with the prefix
     * "server.servlet.context-parameters" but the Spring Boot documentation kind of hides that
     * information and the Bean approach seems to be considered the best practice.
     * @return a configured ServletContextInitializer
     */
    @Bean
    public ServletContextInitializer initializer() {
        return new ServletContextInitializer() {
            @Override
            public void onStartup(ServletContext servletContext) throws ServletException {
                servletContext.setInitParameter("resteasy.async.job.service.enabled", "true");
                servletContext.setInitParameter("resteasy.async.job.service.base.path", "/jobs");
            }
        };
    }

    /**
     * Load values from the application properties file prefixed with "rhsm-conduit.pinhead".  For example,
     * "rhsm-conduit.pinhead.keystore-password=password" will be injected into the keystorePassword field.
     * The hyphen is not necessary but it improves readability.  Rather than use the
     * ConfigurationProperties annotation on the class itself and the EnableConfigurationProperties
     * annotation on ApplicationConfiguration, we construct and bind values to the class here so that our
     * sub-projects will not need to have Spring Boot on the class path (since it's Spring Boot that provides
     * those annotations).
     * @return an X509ApiClientFactoryConfiguration populated with values from the various property sources.
     */
    @Bean
    @ConfigurationProperties(prefix = "rhsm-conduit.pinhead")
    public PinheadApiProperties pinheadApiProperties() {
        return new PinheadApiProperties();
    }

    /**
     * Build the BeanFactory implementation ourselves since the docs say "Implementations are not supposed
     * to rely on annotation-driven injection or other reflective facilities."
     * @param properties containing the configuration needed by the factory
     * @return a configured PinheadApiFactory
     */
    @Bean
    public PinheadApiFactory pinheadApiFactory(PinheadApiProperties properties) {
        return new PinheadApiFactory(properties);
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
    public ObjectMapperContextResolver objectMapperContextResolver() {
        return new ObjectMapperContextResolver(applicationProperties);
    }

    @Bean
    public static BeanFactoryPostProcessor servletInitializer() {
        return new JaxrsApplicationServletInitializer();
    }

    @Bean(name = "pinheadRetryTemplate")
    public RetryTemplate retryTemplate() {
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(4);

        ExponentialRandomBackOffPolicy backOffPolicy = new ExponentialRandomBackOffPolicy();
        backOffPolicy.setInitialInterval(1000);

        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(retryPolicy);
        retryTemplate.setBackOffPolicy(backOffPolicy);
        return retryTemplate;
    }

    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
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
