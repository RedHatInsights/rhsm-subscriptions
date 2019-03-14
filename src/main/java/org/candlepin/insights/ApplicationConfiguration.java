/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.insights;

import org.candlepin.insights.inventory.client.HostsApiFactory;
import org.candlepin.insights.inventory.client.InventoryServiceProperties;
import org.candlepin.insights.jackson.ObjectMapperContextResolver;
import org.candlepin.insights.pinhead.client.PinheadApiFactory;
import org.candlepin.insights.pinhead.client.PinheadApiProperties;

import org.jboss.resteasy.springboot.ResteasyAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;

/** Class to hold configuration beans */
@Configuration
@Import(ResteasyAutoConfiguration.class) // needed to be able to reference ResteasyApplicationBuilder
@EnableConfigurationProperties(ApplicationProperties.class)
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

    @Bean
    @ConfigurationProperties(prefix = "rhsm-conduit.inventory-service")
    public InventoryServiceProperties inventoryServiceProperties() {
        return new InventoryServiceProperties();
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
    public ObjectMapperContextResolver objectMapperContextResolver() {
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
}
