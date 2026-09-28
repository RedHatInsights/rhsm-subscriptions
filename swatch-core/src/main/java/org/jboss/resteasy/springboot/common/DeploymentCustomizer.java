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
package org.jboss.resteasy.springboot.common;

import java.util.Objects;
import org.jboss.resteasy.core.AsynchronousDispatcher;
import org.jboss.resteasy.core.ResourceMethodRegistry;
import org.jboss.resteasy.core.SynchronousDispatcher;
import org.jboss.resteasy.plugins.spring.SpringBeanProcessor;
import org.jboss.resteasy.spi.ResteasyDeployment;
import org.jboss.resteasy.spi.ResteasyProviderFactory;

/**
 * Prepares, configures, and initializes the core components of a RESTEasy deployment for Spring
 * Boot servlet registration.
 */
public class DeploymentCustomizer {

  /**
   * Configures and initializes a resteasy deployment with:
   *
   * <ul>
   *   <li>A {@code org.jboss.resteasy.spi.Dispatcher}
   *   <li>A {@code org.jboss.resteasy.spi.ResteasyProviderFactory}
   *   <li>A {@code org.jboss.resteasy.core.ResourceMethodRegistry}
   * </ul>
   *
   * @param resteasySpringBeanProcessor - The spring bean processor to acquire the provider and
   *     resource factories from.
   * @param deployment - The deployment to customize.
   * @param enableAsyncJob - Indicates whether the async job service should be enabled.
   */
  public static void customizeRestEasyDeployment(
      SpringBeanProcessor resteasySpringBeanProcessor,
      ResteasyDeployment deployment,
      boolean enableAsyncJob) {

    Objects.requireNonNull(resteasySpringBeanProcessor);
    Objects.requireNonNull(deployment);

    final ResteasyProviderFactory resteasyProviderFactory =
        resteasySpringBeanProcessor.getProviderFactory();
    final ResourceMethodRegistry resourceMethodRegistry =
        (ResourceMethodRegistry) resteasySpringBeanProcessor.getRegistry();

    deployment.setProviderFactory(resteasyProviderFactory);
    deployment.setRegistry(resourceMethodRegistry);

    if (enableAsyncJob) {
      deployment.setAsyncJobServiceEnabled(true);
      final AsynchronousDispatcher dispatcher =
          new AsynchronousDispatcher(resteasyProviderFactory, resourceMethodRegistry);
      deployment.setDispatcher(dispatcher);
    } else {
      final SynchronousDispatcher dispatcher =
          new SynchronousDispatcher(resteasyProviderFactory, resourceMethodRegistry);
      deployment.setDispatcher(dispatcher);
    }
  }
}
