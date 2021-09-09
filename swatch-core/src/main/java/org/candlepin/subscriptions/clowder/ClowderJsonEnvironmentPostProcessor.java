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

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.boot.origin.Origin;
import org.springframework.boot.origin.OriginLookup;
import org.springframework.boot.origin.TextResourceOrigin;
import org.springframework.boot.origin.TextResourceOrigin.Location;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.support.StandardServletEnvironment;

/**
 * An {@link org.springframework.boot.env.EnvironmentPostProcessor} that takes Clowder JSON and
 * converts it into a form easily interoperable with the rest of the Spring configuration. The
 * Clowder JSON is flattened into properties where it can be referenced elsewhere and each property
 * is prefixed with the word "clowder".
 *
 * <p>For example:
 *
 * <pre><code>
 * { "kafka": {
 *   "brokers": [{
 *     "hostname": "localhost",
 *    }]
 * }}
 * </code></pre>
 *
 * becomes <code>clowder.kafka.brokers[0].hostname</code>.
 *
 * <p>These properties can be referenced elsewhere in application configuration using the "${}"
 * syntax and can be assigned to intermediate properties such as environment variables. This allows
 * usages like
 *
 * <pre><code>
 *   rhsm.kafka.host: ${KAKFA_HOST}
 *   KAFKA_HOST: ${clowder.kafka.brokers[0].hostname}
 * </code></pre>
 *
 * This use of an intermediate environment variable allows for succinct overriding during local
 * application development when the Clowder JSON is not available.
 *
 * <p>Properties that exist in both the Clowder JSON are at a high precedence in the property
 * resolution order. For example, if "clowder.kafka.brokers[0].hostname" is defined in
 * application.yaml, as a Java system property, and in the Clowder JSON, the value from the Clowder
 * JSON will win. Only command line arguments and property values defined in tests have higher
 * precedence.
 *
 * <p>This class is inspired by (and derived from) {@link
 * org.springframework.boot.cloud.CloudFoundryVcapEnvironmentPostProcessor} and {@link
 * org.springframework.boot.env.SpringApplicationJsonEnvironmentPostProcessor}
 */
public class ClowderJsonEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
  public static final String JSON_RESOURCE_LOCATION =
      "rhsm-subscriptions.clowder.json-resource-location";

  private static final String SERVLET_ENVIRONMENT_CLASS =
      "org.springframework.web.context.support.StandardServletEnvironment";

  private static final Set<String> SERVLET_ENVIRONMENT_PROPERTY_SOURCES =
      new LinkedHashSet<>(
          Arrays.asList(
              StandardServletEnvironment.JNDI_PROPERTY_SOURCE_NAME,
              StandardServletEnvironment.SERVLET_CONTEXT_PROPERTY_SOURCE_NAME,
              StandardServletEnvironment.SERVLET_CONFIG_PROPERTY_SOURCE_NAME));

  public static final String CLOWDER_JSON_PREFIX = "clowder";

  // Order before ConfigDataApplicationListener so values there can use Clowder values
  private int order = ConfigDataEnvironmentPostProcessor.ORDER - 1;
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
  }

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {

    MutablePropertySources propertySources = environment.getPropertySources();
    propertySources.stream()
        .map(getMapper())
        .filter(Objects::nonNull)
        .findFirst()
        .ifPresent(v -> processJson(environment, v));
  }

  private Function<PropertySource<?>, ClowderJsonValue> getMapper() {
    return propertySource -> {
      try {
        return ClowderJsonValue.get(propertySource);
      } catch (IOException e) {
        logger.warn("Could not read " + JSON_RESOURCE_LOCATION, e);
      }
      return null;
    };
  }

  private void processJson(ConfigurableEnvironment environment, ClowderJsonValue propertyValue) {
    logger.info("Reading Clowder configuration...");
    JsonParser parser = JsonParserFactory.getJsonParser();
    Map<String, Object> map = parser.parseMap(propertyValue.getJson());
    if (!map.isEmpty()) {
      var jsonSource = new ClowderJsonPropertySource(propertyValue, flatten(map));
      jsonSource.addToEnvironment(environment, logger);
    }
  }

  /**
   * Flatten the map keys using period separator.
   *
   * @param map the map that should be flattened
   * @return the flattened map
   */
  private Map<String, Object> flatten(Map<String, Object> map) {
    Map<String, Object> result = new LinkedHashMap<>();
    flatten(CLOWDER_JSON_PREFIX, result, map);
    return result;
  }

  private void flatten(String prefix, Map<String, Object> result, Map<String, Object> map) {
    String namePrefix = (prefix != null) ? prefix + "." : "";
    map.forEach((key, value) -> extract(namePrefix + key, result, value));
  }

  private void extract(String name, Map<String, Object> result, Object value) {
    if (value instanceof Map) {
      if (CollectionUtils.isEmpty((Map<?, ?>) value)) {
        result.put(name, value);
        return;
      }
      flatten(name, result, (Map<String, Object>) value);
    } else if (value instanceof Collection) {
      if (CollectionUtils.isEmpty((Collection<?>) value)) {
        result.put(name, value);
        return;
      }
      int index = 0;
      for (Object object : (Collection<Object>) value) {
        extract(name + "[" + index + "]", result, object);
        index++;
      }
    } else {
      result.put(name, value);
    }
  }

  private static class ClowderJsonPropertySource extends MapPropertySource
      implements OriginLookup<String> {

    public static final String CLOWDER_PROPERTY_SOURCE_NAME = "clowderProperties";
    private final ClowderJsonValue value;

    ClowderJsonPropertySource(ClowderJsonValue value, Map<String, Object> source) {
      super(CLOWDER_PROPERTY_SOURCE_NAME, source);
      this.value = value;
    }

    @Override
    public Origin getOrigin(String key) {
      return this.value.getOrigin();
    }

    public ClowderJsonValue getJsonValue() {
      return value;
    }

    @Override
    public boolean equals(Object obj) {
      if (!super.equals(obj)) {
        return false;
      }
      ClowderJsonPropertySource jsonObj = (ClowderJsonPropertySource) obj;
      return value.equals(jsonObj.getJsonValue());
    }

    @Override
    public int hashCode() {
      return Objects.hash(super.hashCode(), value);
    }

    public void addToEnvironment(ConfigurableEnvironment environment, Log logger) {
      MutablePropertySources sources = environment.getPropertySources();
      PropertySource<?> existing = sources.get(CLOWDER_PROPERTY_SOURCE_NAME);
      if (existing != null) {
        logger.warn(CLOWDER_PROPERTY_SOURCE_NAME + " already present. This is unexpected.");
        return;
      }

      String name = findPropertySourceInsertionPoint(sources);
      if (sources.contains(name)) {
        sources.addBefore(name, this);
      } else {
        sources.addFirst(this);
      }
    }

    /**
     * Find where in the list of PropertySources the ClowderJsonPropertySource should be. The list
     * of PropertySources defines the resolution order when the same variable is defined in multiple
     * places. See <a
     * href="https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#features.external-config">Spring
     * documentation</a> for a full listing of the resolution order.
     *
     * <p>This method follows the example of {@link
     * org.springframework.boot.env.SpringApplicationJsonEnvironmentPostProcessor} where the
     * PropertySource is inserted at a higher precedence than servlet init parameters but at a lower
     * precedence than command line arguments.
     *
     * <p>This ordering means that a given property defined in the Clowder JSON will supersede a
     * property of the same name that is defined in an application.yaml file, as an environment
     * variable, or as a Java system property.
     *
     * @param sources a list of PropertySources
     * @return the name of the property source the ClowderJsonPropertySource should be immediately
     *     before
     */
    private String findPropertySourceInsertionPoint(MutablePropertySources sources) {
      if (ClassUtils.isPresent(SERVLET_ENVIRONMENT_CLASS, null)) {
        PropertySource<?> servletPropertySource =
            sources.stream()
                .filter(source -> SERVLET_ENVIRONMENT_PROPERTY_SOURCES.contains(source.getName()))
                .findFirst()
                .orElse(null);
        if (servletPropertySource != null) {
          return servletPropertySource.getName();
        }
      }
      return StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME;
    }
  }

  private static class ClowderJsonValue {
    private static final Location START_OF_FILE = new Location(0, 0);

    private final String json;
    private final Resource resource;

    ClowderJsonValue(String json, Resource resource) {
      this.json = json;
      this.resource = resource;
    }

    String getJson() {
      return this.json;
    }

    Origin getOrigin() {
      return new TextResourceOrigin(resource, START_OF_FILE);
    }

    static ClowderJsonValue get(PropertySource<?> propertySource) throws IOException {
      Object value = propertySource.getProperty(JSON_RESOURCE_LOCATION);

      if (value instanceof String && StringUtils.hasText((String) value)) {
        ResourceLoader resourceLoader = new DefaultResourceLoader();
        Resource resource = resourceLoader.getResource((String) value);
        byte[] bytes = resource.getInputStream().readAllBytes();
        String json = new String(bytes);

        return new ClowderJsonValue(json, resource);
      }

      return null;
    }
  }
}
