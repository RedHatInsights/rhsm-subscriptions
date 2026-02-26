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

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletContainerInitializer;
import java.util.Set;
import org.jboss.resteasy.plugins.server.servlet.HttpServlet30Dispatcher;
import org.jboss.resteasy.plugins.server.servlet.ResteasyContextParameters;
import org.jboss.resteasy.plugins.servlet.ResteasyServletInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.ServletRegistrationBean;

/**
 * This class is the Spring Boot equivalent of {@link ResteasyServletInitializer}, which implements
 * the Servlet API {@link ServletContainerInitializer} interface to find all JAX-RS Application,
 * Provider and Path classes in the classpath.
 *
 * <p>As we all know, in Spring Boot we use an embedded servlet container. However, the Servlet spec
 * does not support embedded containers, and many portions of it do not apply to embedded
 * containers, and ServletContainerInitializer is one of them.
 *
 * <p>This class fills in this gap.
 *
 * <p>Notice that the JAX-RS Application classes are found in this RESTEasy starter by class
 * ResteasyEmbeddedServletInitializer, and that is done by scanning the classpath.
 *
 * <p>The Path and Provider annotated classes are found by using Spring framework (instead of
 * scanning the classpath), since it is assumed those classes are ALWAYS necessarily Spring beans
 * (this starter is meant for Spring Boot applications that use RESTEasy as the JAX-RS
 * implementation)
 *
 * @author Fabio Carvalho (facarvalho@paypal.com or fabiocarvalho777@gmail.com)
 */
public class ResteasyApplicationBuilder {

  public static final String BEAN_NAME = "JaxrsApplicationServletBuilder";

  private static final Logger logger = LoggerFactory.getLogger(ResteasyApplicationBuilder.class);

  public ServletRegistrationBean build(
      String applicationClassName, String path, Set<Class<?>> resources, Set<Class<?>> providers) {
    Servlet servlet = new HttpServlet30Dispatcher();

    ServletRegistrationBean servletRegistrationBean = new ServletRegistrationBean(servlet);

    servletRegistrationBean.setName(applicationClassName);
    servletRegistrationBean.setLoadOnStartup(1);
    servletRegistrationBean.setAsyncSupported(true);
    //		servletRegistrationBean.addInitParameter(Application.class.getTypeName(),
    // applicationClassName);
    servletRegistrationBean.addInitParameter("jakarta.ws.rs.Application", applicationClassName);

    if (path != null) {
      String mapping = path;
      if (!mapping.startsWith("/")) mapping = "/" + mapping;
      String prefix = mapping;
      if (!"/".equals(prefix) && prefix.endsWith("/"))
        prefix = prefix.substring(0, prefix.length() - 1);
      if (mapping.endsWith("/")) mapping += "*";
      else mapping += "/*";
      servletRegistrationBean.addInitParameter("resteasy.servlet.mapping.prefix", prefix);
      servletRegistrationBean.addUrlMappings(mapping);
    }

    if (resources.size() > 0) {
      StringBuilder builder = new StringBuilder();
      boolean first = true;
      for (Class<?> resource : resources) {
        if (first) {
          first = false;
        } else {
          builder.append(",");
        }

        builder.append(resource.getName());
      }
      servletRegistrationBean.addInitParameter(
          ResteasyContextParameters.RESTEASY_SCANNED_RESOURCES, builder.toString());
    }
    if (providers.size() > 0) {
      StringBuilder builder = new StringBuilder();
      boolean first = true;
      for (Class<?> provider : providers) {
        if (first) {
          first = false;
        } else {
          builder.append(",");
        }
        builder.append(provider.getName());
      }
      servletRegistrationBean.addInitParameter(
          ResteasyContextParameters.RESTEASY_SCANNED_PROVIDERS, builder.toString());
    }

    logger.debug(
        "ServletRegistrationBean has just bean created for JAX-RS class " + applicationClassName);

    return servletRegistrationBean;
  }
}
