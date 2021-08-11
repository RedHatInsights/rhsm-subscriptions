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
package org.candlepin.subscriptions.util;

import static io.hawt.web.filters.BaseTagHrefFilter.*;

import io.hawt.springboot.HawtioManagementConfiguration;
import io.hawt.web.filters.BaseTagHrefFilter;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import org.candlepin.subscriptions.ApplicationProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.autoconfigure.web.server.ConditionalOnManagementPort;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementPortType;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Configuration that modifies the base path used by the Hawtio frontend.
 *
 * <p>This enables us to deploy the console behind a reverse proxy with a different prefix.
 *
 * <p>For example, if hawtio is deployed at /actuator/hawtio, and the reverse proxy configuration
 * results in a browser URL of /rhsm-subscriptions/actuator/hawtio, then setting
 * `rhsm-subscriptions.hawtioBasePath` to /rhsm-subscriptions/actuator/hawtio forces the frontend to
 * return the proper URLs for JavaScript/CSS.
 */
@Configuration
@ConditionalOnManagementPort(ManagementPortType.SAME)
@AutoConfigureAfter(HawtioManagementConfiguration.class)
public class HawtioConfiguration {
  @Autowired
  public void modifyBaseTagHrefFilter(
      @Qualifier("baseTagHrefFilter") FilterRegistrationBean<BaseTagHrefFilter> filter,
      ServletContext servletContext,
      ApplicationProperties props)
      throws ServletException {
    if (!StringUtils.isEmpty(props.getHawtioBasePath())) {
      BaseTagHrefFilter baseTagHrefFilter = filter.getFilter();
      BaseTagHrefFilterConfigOverride filterConfig =
          new BaseTagHrefFilterConfigOverride(servletContext);
      filterConfig.setParameter(PARAM_APPLICATION_CONTEXT_PATH, props.getHawtioBasePath());
      baseTagHrefFilter.init(filterConfig);
    }
  }

  private static class BaseTagHrefFilterConfigOverride implements FilterConfig {

    private final ServletContext servletContext;
    private final Map<String, String> parameters = new HashMap<>();

    public BaseTagHrefFilterConfigOverride(ServletContext servletContext) {
      this.servletContext = servletContext;
    }

    @Override
    public String getFilterName() {
      // not used
      return null;
    }

    @Override
    public ServletContext getServletContext() {
      return servletContext;
    }

    @Override
    public String getInitParameter(String name) {
      return parameters.get(name);
    }

    @Override
    public Enumeration<String> getInitParameterNames() {
      return Collections.enumeration(parameters.keySet());
    }

    public void setParameter(String name, String value) {
      parameters.put(name, value);
    }
  }
}
