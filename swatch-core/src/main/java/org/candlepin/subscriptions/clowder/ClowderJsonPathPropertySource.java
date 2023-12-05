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
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
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
@EqualsAndHashCode(callSuper = true)
@Slf4j
public class ClowderJsonPathPropertySource extends PropertySource<ClowderJson>
    implements OriginLookup<String> {

  public static final String PROPERTY_SOURCE_NAME = "Clowder JSON";
  public static final String PREFIX = "clowder.";
  private static final String KAFKA_BROKERS = "kafka.brokers";
  private static final String ENDPOINTS = "endpoints";
  private static final Integer PORT_NOT_SET = 0;
  private static final String ENDPOINT_TLS_PORT_PROPERTY = "tlsPort";

  /**
   * Custom rules for kafka broker property binding where the key is the suffix of the clowder
   * config and the function is the custom logic to apply. For example, if we want to apply a custom
   * logic for the property "clowder.kafka.broker.sasl", then we need to add a new entry with key
   * ".sasl" and the function with the custom logic.
   *
   * <p>Clowder configuration might include multiple brokers. In this case, the logic will use the
   * first broker with the SASL configuration.
   */
  private static final Map<String, KafkaBrokerConfigMapper> KAFKA_BROKERS_PROPERTIES =
      Map.of(
          "", ClowderJsonPathPropertySource::kafkaBrokerToBootstrapServer,
          ".sasl.securityProtocol",
              ClowderJsonPathPropertySource::kafkaBrokerToSaslSecurityProtocol,
          ".sasl.mechanism", ClowderJsonPathPropertySource::kafkaBrokerToSaslMechanism,
          ".sasl.jaas.config", ClowderJsonPathPropertySource::kafkaBrokerToSaslJaasConfig,
          ".cacert", ClowderJsonPathPropertySource::kafkaBrokerToCaCert,
          ".cacert.type", ClowderJsonPathPropertySource::kafkaBrokerToCaCertType);

  /** Custom rules to configure rest-clients using clowder file. */
  private static final Map<String, EndpointConfigMapper> ENDPOINTS_PROPERTIES =
      Map.of(
          ".url", ClowderJsonPathPropertySource::endpointToUrl,
          ".trust-store-path", ClowderJsonPathPropertySource::endpointToTrustStorePath,
          ".trust-store-password", ClowderJsonPathPropertySource::endpointToTrustStorePassword,
          ".trust-store-type", ClowderJsonPathPropertySource::endpointToTrustStoreType);

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

  private ClowderTrustStoreConfiguration trustStoreConfiguration;

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

    // handling for special properties like kafka brokers
    if (name.contains(KAFKA_BROKERS)) {
      String brokerConfig = name.substring(PREFIX.length() + KAFKA_BROKERS.length());
      // special logic to provide a comma-separated list of kafka brokers
      // value is expected to be a list of key-value collection with the broker configuration.
      KafkaBrokerConfigMapper configFunction = KAFKA_BROKERS_PROPERTIES.get(brokerConfig);
      if (configFunction != null) {
        return getKafkaBrokerConfig(name, configFunction);
      }
    }
    // handling for rest-clients
    if (name.contains(ENDPOINTS)) {
      for (Map.Entry<String, EndpointConfigMapper> entry : ENDPOINTS_PROPERTIES.entrySet()) {
        if (name.endsWith(entry.getKey())) {
          return getEndpointConfig(name, entry.getValue());
        }
      }
    }

    return getJsonPathValue(name.substring(PREFIX.length()));
  }

  protected String getTruststorePath() {
    if (trustStoreConfiguration == null) {
      initializeTrustStoreConfiguration();
    }

    return trustStoreConfiguration.getPath();
  }

  protected String getTruststorePassword() {
    if (trustStoreConfiguration == null) {
      initializeTrustStoreConfiguration();
    }

    return trustStoreConfiguration.getPassword();
  }

  protected String getTruststoreType() {
    if (trustStoreConfiguration == null) {
      initializeTrustStoreConfiguration();
    }

    return ClowderTrustStoreConfiguration.CLOWDER_ENDPOINT_STORE_TYPE;
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

  public static Object kafkaBrokerToBootstrapServer(List<Map<String, Object>> brokerConfig) {
    return brokerConfig.stream()
        .map(m -> m.get("hostname") + ":" + m.get("port"))
        .collect(Collectors.joining(","));
  }

  public static String kafkaBrokerToSaslSecurityProtocol(List<Map<String, Object>> brokerConfig) {
    String saslMechanism = kafkaBrokerToSaslMechanism(brokerConfig);
    if (saslMechanism != null) {
      return getKafkaBrokerSaslProperty(brokerConfig, "securityProtocol");
    }

    // if sasl mechanism isn't specified, kafka communication is unauthenticated, nothing to do
    return null;
  }

  public static String kafkaBrokerToSaslMechanism(List<Map<String, Object>> brokerConfig) {
    return getKafkaBrokerSaslProperty(brokerConfig, "saslMechanism");
  }

  public static Object kafkaBrokerToSaslJaasConfig(List<Map<String, Object>> brokerConfig) {
    String saslMechanism = kafkaBrokerToSaslMechanism(brokerConfig);
    if (saslMechanism != null) {
      // configure the sasl.jaas.config property, inspired by ClowderConfigSource
      String username = getKafkaBrokerSaslProperty(brokerConfig, "username");
      String password = getKafkaBrokerSaslProperty(brokerConfig, "password");
      return switch (saslMechanism) {
        case "PLAIN" -> "org.apache.kafka.common.security.plain.PlainLoginModule required "
            + "username=\""
            + username
            + "\" password=\""
            + password
            + "\";";
        case "SCRAM-SHA-512",
            "SCRAM-SHA-256" -> "org.apache.kafka.common.security.scram.ScramLoginModule required "
            + "username=\""
            + username
            + "\" password=\""
            + password
            + "\";";
        default -> throw new IllegalArgumentException(
            "Invalid SASL mechanism defined: " + saslMechanism);
      };
    }

    // if sasl mechanism isn't specified, kafka communication is unauthenticated, nothing to do
    return null;
  }

  public static Object kafkaBrokerToCaCert(List<Map<String, Object>> brokerConfig) {
    String saslMechanism = kafkaBrokerToSaslMechanism(brokerConfig);
    if (saslMechanism != null) {
      Map<String, Object> broker = getKafkaBrokerWithSasl(brokerConfig);
      if (broker != null) {
        return broker.get("cacert");
      }
    }

    // if sasl mechanism isn't specified, kafka communication is unauthenticated, nothing to do
    return null;
  }

  public static Object kafkaBrokerToCaCertType(List<Map<String, Object>> brokerConfig) {
    Object cacert = kafkaBrokerToCaCert(brokerConfig);
    if (cacert != null) {
      return "PEM";
    }

    return null;
  }

  private void initializeTrustStoreConfiguration() {
    Object tlsCAPath = getJsonPathValue("tlsCAPath");
    if (tlsCAPath instanceof String tlsCAPathAsString && !tlsCAPathAsString.isBlank()) {
      trustStoreConfiguration = new ClowderTrustStoreConfiguration(tlsCAPathAsString);
    } else {
      throw new IllegalStateException(
          "Requested tls port for endpoint but did not provide tlsCAPath");
    }
  }

  public static Object endpointToUrl(
      ClowderJsonPathPropertySource root, Map<String, Object> endpointConfig) {
    String protocol = "http";
    Object hostName = endpointConfig.get("hostname");
    Object port = endpointConfig.get("port");
    if (endpointUsesTls(endpointConfig)) {
      protocol = "https";
      port = endpointConfig.get(ENDPOINT_TLS_PORT_PROPERTY);
    }

    String url = String.format("%s://%s:%s", protocol, hostName, port);
    log.info("Endpoint '{}' using '{}'", getEndpointName(endpointConfig), url);
    return url;
  }

  public static Object endpointToTrustStorePath(
      ClowderJsonPathPropertySource root, Map<String, Object> endpointConfig) {
    if (endpointUsesTls(endpointConfig)) {
      return "file:" + root.getTruststorePath();
    }

    return null;
  }

  public static Object endpointToTrustStorePassword(
      ClowderJsonPathPropertySource root, Map<String, Object> endpointConfig) {
    if (endpointUsesTls(endpointConfig)) {
      return root.getTruststorePassword();
    }

    return null;
  }

  public static Object endpointToTrustStoreType(
      ClowderJsonPathPropertySource root, Map<String, Object> endpointConfig) {
    if (endpointUsesTls(endpointConfig)) {
      return root.getTruststoreType();
    }

    return null;
  }

  public static boolean endpointUsesTls(Map<String, Object> endpointConfig) {
    Object tlsPort = endpointConfig.get(ENDPOINT_TLS_PORT_PROPERTY);
    if (tlsPort instanceof Integer tlsPortAsInt) {
      return !tlsPortAsInt.equals(PORT_NOT_SET);
    }

    return false;
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
  private Object getKafkaBrokerConfig(String name, KafkaBrokerConfigMapper configFunction) {
    Object value = getJsonPathValue(KAFKA_BROKERS);
    // NOTE: because we configure kafka in shared configuration, kafka config is included in
    // services that don't use kafka. Clowder doesn't populate kafka config for those services, so
    // we won't have a value to contribute.
    if (value == null) {
      return null;
    }
    if (value instanceof Collection<?> list) {
      if (list.isEmpty()) {
        return null;
      }
      List<Map<String, Object>> brokers = list.stream().map(o -> (Map<String, Object>) o).toList();
      return configFunction.apply(brokers);
    } else {
      throw new IllegalStateException(
          "Unknown type found in clowder configuration for Kafka Broker property: " + name);
    }
  }

  private Object getEndpointConfig(String name, EndpointConfigMapper configFunction) {
    Object value = getJsonPathValue(ENDPOINTS);
    if (value instanceof Collection<?> list) {
      if (list.isEmpty()) {
        return null;
      }

      String endpoint = extractEndpointName(name);
      Map<String, Object> found =
          list.stream()
              .map(o -> (Map<String, Object>) o)
              .filter(c -> endpoint.equals(getEndpointName(c)))
              .findFirst()
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Could not find the endpoint configuration for property: " + name));
      return configFunction.apply(this, found);
    } else {
      throw new IllegalStateException(
          "Unknown type found in clowder configuration for endpoint property: " + name);
    }
  }

  @SuppressWarnings("unchecked")
  private static String getKafkaBrokerSaslProperty(
      List<Map<String, Object>> brokerConfig, String propertyName) {
    Map<String, Object> broker = getKafkaBrokerWithSasl(brokerConfig);
    if (broker != null) {
      Map<String, Object> saslConfig = (Map<String, Object>) broker.get("sasl");
      Object value = saslConfig.get(propertyName);
      if (value instanceof String str) {
        return str;
      }
    }

    return null;
  }

  private static Map<String, Object> getKafkaBrokerWithSasl(
      List<Map<String, Object>> brokerConfig) {
    return brokerConfig.stream()
        .filter(m -> "sasl".equals(m.get("authtype")))
        .findFirst()
        .orElse(null);
  }

  @FunctionalInterface
  private interface KafkaBrokerConfigMapper extends Function<List<Map<String, Object>>, Object> {}

  private String extractEndpointName(String name) {
    String part = name.substring(PREFIX.length() + ENDPOINTS.length() + 1);
    return part.substring(0, part.indexOf("."));
  }

  private static String getEndpointName(Map<String, Object> endpointConfig) {
    return String.format("%s-%s", endpointConfig.get("app"), endpointConfig.get("name"));
  }

  @FunctionalInterface
  private interface EndpointConfigMapper
      extends BiFunction<ClowderJsonPathPropertySource, Map<String, Object>, Object> {}
}
