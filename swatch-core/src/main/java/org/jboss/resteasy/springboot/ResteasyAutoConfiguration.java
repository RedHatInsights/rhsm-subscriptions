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
package org.jboss.resteasy.springboot;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import org.jboss.resteasy.plugins.server.servlet.ListenerBootstrap;
import org.jboss.resteasy.plugins.server.servlet.ResteasyBootstrap;
import org.jboss.resteasy.plugins.spring.SpringBeanProcessor;
import org.jboss.resteasy.spi.Dispatcher;
import org.jboss.resteasy.spi.Registry;
import org.jboss.resteasy.spi.ResteasyDeployment;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.jboss.resteasy.springboot.common.DeploymentCustomizer;
import org.jboss.resteasy.springboot.common.ResteasyBeanProcessorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * This is the main class that configures this Resteasy Spring Boot starter
 *
 * <p>WORKAROUND: This class is copied from resteasy-servlet-spring-boot-starter:6.3.0.Final with
 * the Spring Boot 4 import fix for WebMvcAutoConfiguration. See:
 * https://github.com/resteasy/resteasy-spring-boot/issues/429 See:
 * https://github.com/resteasy/resteasy-spring-boot/pull/425
 *
 * <p>This can be removed once a Spring Boot 4 compatible version is released.
 *
 * @author Fabio Carvalho (facarvalho@paypal.com or fabiocarvalho777@gmail.com)
 */
@AutoConfiguration
@EnableConfigurationProperties
public class ResteasyAutoConfiguration {

  private static Logger logger = LoggerFactory.getLogger(ResteasyAutoConfiguration.class);

  /**
   * This is a modified version of {@link ResteasyBootstrap}
   *
   * @param resteasySpringBeanProcessor - A bean processor for Resteasy.
   * @return a ServletContextListener object that configures and start a ResteasyDeployment
   */
  @Bean
  public ServletContextListener resteasyBootstrapListener(
      final @Qualifier("resteasySpringBeanProcessor") SpringBeanProcessor
              resteasySpringBeanProcessor) {

    ServletContextListener servletContextListener =
        new ServletContextListener() {

          protected ResteasyDeployment deployment;

          public void contextInitialized(ServletContextEvent sce) {
            ServletContext servletContext = sce.getServletContext();

            deployment = new ListenerBootstrap(servletContext).createDeployment();
            DeploymentCustomizer.customizeRestEasyDeployment(
                resteasySpringBeanProcessor, deployment, deployment.isAsyncJobServiceEnabled());
            deployment.start();

            servletContext.setAttribute(
                ResteasyProviderFactory.class.getName(), deployment.getProviderFactory());
            servletContext.setAttribute(Dispatcher.class.getName(), deployment.getDispatcher());
            servletContext.setAttribute(Registry.class.getName(), deployment.getRegistry());
          }

          public void contextDestroyed(ServletContextEvent sce) {
            if (deployment != null) {
              deployment.stop();
            }
          }
        };

    logger.debug("ServletContextListener has been created");

    return servletContextListener;
  }

  @Bean(name = ResteasyApplicationBuilder.BEAN_NAME)
  public ResteasyApplicationBuilder resteasyApplicationBuilder() {
    return new ResteasyApplicationBuilder();
  }

  @Bean
  public static ResteasyBeanProcessorTomcat resteasyBeanProcessorTomcat() {
    return new ResteasyBeanProcessorTomcat();
  }

  @Bean("resteasySpringBeanProcessor")
  public static SpringBeanProcessor resteasySpringBeanProcessor() {
    return ResteasyBeanProcessorFactory.resteasySpringBeanProcessor();
  }
}
