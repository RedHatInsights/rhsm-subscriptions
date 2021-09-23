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
package org.candlepin.subscriptions.clowder;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Function;
import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StringUtils;

/**
 * An {@link org.springframework.boot.env.EnvironmentPostProcessor} that inserts a {@link
 * ClowderJsonPathPropertySource} into the list of property sources.
 */
public class ClowderJsonEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
  public static final String JSON_RESOURCE_LOCATION =
      "rhsm-subscriptions.clowder.json-resource-location";

  public static final String CLOWDER_STRICT_LOADING = "rhsm-subscriptions.clowder.strictLoading";

  // At a minimum this needs to run after the ConfigDataEnvironmentPostProcessor so that we can read
  // JSON_RESOURCE_LOCATION out of the config files
  private int order = Ordered.LOWEST_PRECEDENCE;

  private final ObjectMapper objectMapper;
  private final Log logger;

  @Override
  public int getOrder() {
    return this.order;
  }

  public void setOrder(int order) {
    this.order = order;
  }

  public ClowderJsonEnvironmentPostProcessor(Log logger) {
    this.logger = logger;

    /* If at some point we need to configure this objectMapper, it is possible by having the
     * ClowderJsonEnvironmentPostProcessor accept a ConfigurableBootstrapContext parameter. We
     * can then configure the ObjectMapper in the BootstrapContext.
     */
    this.objectMapper = new ObjectMapper();
  }

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    boolean strictLoading = environment.getProperty(CLOWDER_STRICT_LOADING, boolean.class, true);
    Runnable onFailure;
    String errMsg = "Could not read " + JSON_RESOURCE_LOCATION;
    if (strictLoading) {
      onFailure =
          () -> {
            throw new IllegalStateException(errMsg);
          };
    } else {
      onFailure = () -> logger.warn(errMsg);
    }

    MutablePropertySources propertySources = environment.getPropertySources();
    propertySources.stream()
        .map(getMapper())
        .filter(Objects::nonNull)
        .findFirst()
        .ifPresentOrElse(
            clowderJson -> {
              processJson(environment, clowderJson);
            },
            onFailure);
  }

  private Function<PropertySource<?>, ClowderJson> getMapper() {
    return propertySource -> {
      try {
        var value = propertySource.getProperty(JSON_RESOURCE_LOCATION);

        if (value instanceof String && StringUtils.hasText((String) value)) {
          ResourceLoader resourceLoader = new DefaultResourceLoader();
          Resource resource = resourceLoader.getResource((String) value);
          logger.debug("Loading Clowder configuration from " + resource.getURI());
          return new ClowderJson(resource.getInputStream(), objectMapper);
        }
      } catch (IOException e) {
        logger.warn("Could not read " + JSON_RESOURCE_LOCATION, e);
      }
      return null;
    };
  }

  private void processJson(ConfigurableEnvironment environment, ClowderJson clowderJson) {
    logger.info("Reading Clowder configuration...");
    var jsonSource = new ClowderJsonPathPropertySource(clowderJson);
    jsonSource.addToEnvironment(environment, logger);
  }
}
