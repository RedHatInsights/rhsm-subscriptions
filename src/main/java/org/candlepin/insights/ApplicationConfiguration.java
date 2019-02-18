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
import org.candlepin.insights.pinhead.client.PinheadApiFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;

/** Class to hold configuration beans */
@Configuration
@EnableConfigurationProperties(ApplicationProperties.class)
// The values in application.yaml should already be loaded by default
@PropertySource("classpath:/rhsm-conduit.properties")
public class ApplicationConfiguration {
    private static final Logger log = LoggerFactory.getLogger(ApplicationConfiguration.class);

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

    @Bean
    public HostsApiFactory hostInventoryApiFactory() {
        return new HostsApiFactory(applicationProperties.getInventoryService());
    }

    @Bean
    public PinheadApiFactory pinheadApiFactory() {
        return new PinheadApiFactory(applicationProperties.getPinhead());
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
}
