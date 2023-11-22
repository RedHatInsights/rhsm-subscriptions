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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.logging.Log;
import org.springframework.boot.origin.Origin;
import org.springframework.boot.origin.OriginLookup;
import org.springframework.boot.origin.PropertySourceOrigin;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.util.ClassUtils;
import org.springframework.web.context.support.StandardServletEnvironment;

/**
 * Property source that attempts to resolve properties against the Clowder JSON using JSON Path.
 * This class resolves any property starting with "clowder." Everything after the "clowder." prefix
 * is evaluated as a JSON Path expression against the Clowder JSON and the result is returned.
 */
public class ClowderJsonPathPropertySource extends PropertySource<ClowderJson>
    implements OriginLookup<String> {

  public static final String PROPERTY_SOURCE_NAME = "Clowder JSON";
  public static final String PREFIX = "clowder.";
  private static final String KAFKA_BROKERS = "kafka.brokers";

  private static final String SERVLET_ENVIRONMENT_CLASS =
      "org.springframework.web.context.support.StandardServletEnvironment";

  private static final Set<String> SERVLET_ENVIRONMENT_PROPERTY_SOURCES =
      new LinkedHashSet<>(
          Arrays.asList(
              StandardServletEnvironment.JNDI_PROPERTY_SOURCE_NAME,
              StandardServletEnvironment.SERVLET_CONTEXT_PROPERTY_SOURCE_NAME,
              StandardServletEnvironment.SERVLET_CONFIG_PROPERTY_SOURCE_NAME));

  /* This initialization does tie our hands a bit. We can't inject an ObjectMapper of our
   * choosing for example.  The JSONPath documentation states that Configuration changes during
   * runtime are discouraged (although that comment is directed specifically to the change of the
   * default Configuration).  I'm going to leave the Configuration as a static final for now but
   * in the future, it may be necessary to create a Configuration in the constructor with the
   * mappingProvider using an ObjectMapper we inject into the constructor.
   */
  public static final Configuration JSON_NODE_CONFIGURATION =
      Configuration.builder()
          .mappingProvider(new JacksonMappingProvider())
          .jsonProvider(new JacksonJsonNodeJsonProvider())
          .options(Option.ALWAYS_RETURN_LIST)
          .build();

  public ClowderJsonPathPropertySource(ClowderJson source) {
    super(PROPERTY_SOURCE_NAME, source);
  }

  public ClowderJsonPathPropertySource() throws IOException {
    super(PROPERTY_SOURCE_NAME, new ClowderJson());
  }

  @Override
  public Object getProperty(String name) {
    if (!name.startsWith(PREFIX)) {
      return null;
    }

    Object value = getJsonPathValue(name.substring(PREFIX.length()));

    // handling for special properties like kafka brokers
    if (name.endsWith(KAFKA_BROKERS)) {
      return getKafkaBootstrapServersFromBrokerConfig(value);
    }

    return value;
  }

  protected Object getJsonPathValue(String path) {
    JsonNode root = source.getRoot();

    ArrayNode result;
    try {
      result = JsonPath.using(JSON_NODE_CONFIGURATION).parse(root).read(path);
    } catch (PathNotFoundException e) {
      /* Spring resolves properties in a recursive fashion.  For example, "${my.prop:defaultVal}"
       * is first resolved as "my.prop:defaultVal" and if that doesn't resolve, Spring splits on the
       * colon and then attempts to resolve "my.prop" and only then if "my.prop" isn't found does
       * it return the default.  If the JSON Path isn't found, we need to return null so that
       * Spring will continue the resolution process.
       */
      return null;
    }

    /* JSON Path expressions with a filter (like phoneNumbers[?(@.type=='home')]) are called
     * indefinite since they can return zero or more results.  Indefinite results are always
     * returned in a list while definite results (e.g. name) are scalars.  We turn on the
     * Option.ALWAYS_RETURN_LIST option so that we get a consistent return type.
     *
     * If the list only has 1 element, it's either a definite result or an indefinite
     * result with a size of 1, and we're going to want the sole element from the list, so we
     * just unwrap it here.
     */
    if (result.size() == 1) {
      return cast(result.get(0));
    }

    if (result.isEmpty()) {
      return null;
    }

    return cast(result);
  }

  @SuppressWarnings("java:S3776")
  protected Object cast(JsonNode node) {
    if (node.isNull()) {
      return null;
    } else if (node.isBoolean()) {
      return node.asBoolean();
    } else if (node.isTextual()) {
      return node.asText();
    } else if (node.isFloatingPointNumber()) {
      if (node.isBigDecimal()) {
        return node.decimalValue();
      } else {
        return node.asDouble();
      }
    } else if (node.isIntegralNumber()) {
      if (node.isBigInteger()) {
        return node.bigIntegerValue();
      } else if (node.canConvertToInt()) {
        return node.asInt();
      } else {
        return node.asLong();
      }
    } else if (node.isArray()) {
      List<Object> l = new ArrayList<>();
      for (JsonNode listNode : node) {
        l.add(cast(listNode));
      }
      return l;
    } else if (node.isObject()) {
      Map<Object, Object> m = new LinkedHashMap<>();

      for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
        String key = it.next();
        Object value = cast(node.get(key));
        m.put(key, value);
      }
      return m;
    }
    throw new IllegalStateException("Unknown JsonNode type: " + node);
  }

  @Override
  public Origin getOrigin(String key) {
    return new PropertySourceOrigin(this, PROPERTY_SOURCE_NAME);
  }

  public void addToEnvironment(ConfigurableEnvironment environment, Log logger) {
    MutablePropertySources sources = environment.getPropertySources();
    PropertySource<?> existing = sources.get(PROPERTY_SOURCE_NAME);
    if (existing != null) {
      logger.warn(PROPERTY_SOURCE_NAME + " already present. This is unexpected.");
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
   * Find where in the list of PropertySources the ClowderJsonPathPropertySource should be. The list
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
   * property of the <strong>same</strong> name that is defined in an application.yaml file, as an
   * environment variable, or as a Java system property.
   *
   * <p><strong>HOWEVER</strong>, if the Clowder value is read into an intermediate value in a
   * configuration file, environment variables can supersede that value since there is a resolution
   * for the intermediate variable and resolution follows the order described in the Spring
   * documentation. For example, our application.properties file reads
   *
   * <pre><code>
   *   RBAC_PORT=${clowder.endpoints.rbac.port:123}
   *   rhsm-subscriptions.rbac.url=http://localhost:${RBAC_PORT}
   * </code></pre>
   *
   * <p>The Clowder JSON is
   *
   * <pre><code>
   *   {"endpoints": {"rbac": {"port": 456}}}
   * </code></pre>
   *
   * <p>And our system has an environment variable <code>RBAC_PORT=789</code>
   *
   * <p>Spring will resolve "rhsm-subscriptions.rbac.url" as follows:
   *
   * <ol>
   *   <li>${RBAC_PORT} must be resolved.
   *   <li>The ClowderJsonPathPropertySource is higher in the precedence order than the system
   *       environment property source, but since RBAC_PORT does not begin with the "clowder."
   *       prefix, no resolution occurs.
   *   <li>RBAC_PORT exists in the system environment property source which is higher in the
   *       precedence order than the property source associated with application.properties.
   *   <li>Spring resolves RBAC_PORT to 789
   * </ol>
   *
   * <p>If, however, RBAC_PORT were not defined in the environment, the resolution of
   * "rhsm-subscriptions.rbac.url" would follow this path:
   *
   * <ol>
   *   <li>${RBAC_PORT} must be resolved
   *   <li>RBAC_PORT is found in the application.properties property source
   *   <li>${clowder.endpoints.rbac.port} must be resolved
   *   <li>The ClowderJsonPathPropertySource is triggered and resolves to 456.
   * </ol>
   *
   * The ultimate result is that care must be taken with intermediate assignments since environment
   * variables can override them.
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

  @SuppressWarnings("unchecked")
  private Object getKafkaBootstrapServersFromBrokerConfig(Object value) {
    if (value instanceof Collection<?> list) {
      if (list.isEmpty()) {
        return null;
      }

      return list.stream()
          .map(o -> (Map<String, Object>) o)
          .map(m -> m.get("hostname") + ":" + m.get("port"))
          .collect(Collectors.joining(","));
    } else {
      throw new IllegalStateException(
          "Unknown type found in clowder configuration for bootstrap servers");
    }
  }
}
