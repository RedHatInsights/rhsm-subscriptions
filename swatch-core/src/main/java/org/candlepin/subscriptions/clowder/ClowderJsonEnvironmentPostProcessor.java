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
import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StringUtils;

/**
 * An {@link org.springframework.boot.env.EnvironmentPostProcessor} that inserts a {@link
 * ClowderJsonPathPropertySource} into the list of property sources.
 */
public class ClowderJsonEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
  public static final String CLOWDER_CONFIG_LOCATION_PROPERTY = "ACG_CONFIG";

  // At a minimum this needs to run after the ConfigDataEnvironmentPostProcessor so that we can read
  // JSON_RESOURCE_LOCATION out of the config files
  private int order = Ordered.LOWEST_PRECEDENCE;

  private final ObjectMapper objectMapper;

  // This logger records messages and is then replayed through the DeferredLogs class in Spring.
  // As such, we have to use an Apache Commons Log class.
  private final Log logger;

  @Override
  public int getOrder() {
    return this.order;
  }

  public void setOrder(int order) {
    this.order = order;
  }

  public ClowderJsonEnvironmentPostProcessor(DeferredLogFactory logFactory) {
    this.logger = logFactory.getLog(ClowderJsonEnvironmentPostProcessor.class);
    /* If at some point we need to configure this objectMapper, it is possible by having the
     * ClowderJsonEnvironmentPostProcessor accept a ConfigurableBootstrapContext parameter. We
     * can then configure the ObjectMapper in the BootstrapContext.
     */
    this.objectMapper = new ObjectMapper();
  }

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    String clowderConfigPath = environment.getProperty(CLOWDER_CONFIG_LOCATION_PROPERTY);
    if (StringUtils.hasText(clowderConfigPath)) {
      String clowderResourceLocation = "file:" + clowderConfigPath;
      ClowderJson clowderJson = loadResource(clowderResourceLocation);
      processJson(environment, clowderJson);
    } else {
      logger.info(CLOWDER_CONFIG_LOCATION_PROPERTY + " undefined. Will not read clowder config.");
    }
  }

  private ClowderJson loadResource(String clowderResourceLocation) {
    try {
      ResourceLoader resourceLoader = new DefaultResourceLoader();
      Resource resource = resourceLoader.getResource(clowderResourceLocation);
      logger.info("Reading Clowder configuration from " + resource.getURI());
      return new ClowderJson(resource.getInputStream(), objectMapper);
    } catch (IOException e) {
      throw new IllegalStateException("Could not read clowder config", e);
    }
  }

  private void processJson(ConfigurableEnvironment environment, ClowderJson clowderJson) {
    var jsonSource = new ClowderJsonPathPropertySource(clowderJson);
    jsonSource.addToEnvironment(environment, logger);
  }
}
