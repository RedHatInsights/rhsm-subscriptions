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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.isA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.apache.commons.io.IOUtils;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.context.support.StandardServletEnvironment;

@ContextConfiguration
class ClowderJsonPathPropertySourceTest {
  public static final String TEST_CLOWDER_CONFIG_JSON = "classpath:/test-clowder-config.json";
  public static final String COMPLEX_JSON =
      "{\"phoneNumbers\": ["
          + "    {"
          + "      \"type\"  : \"work\","
          + "      \"number\": \"515-555-1212\""
          + "    },"
          + "    {"
          + "      \"type\"  : \"home\","
          + "      \"number\": \"515-555-8888\""
          + "    }"
          + "  ]}";

  private final ConfigurableEnvironment environment = new StandardEnvironment();
  private final DeferredLogFactory logFactory = Supplier::get;

  @Test
  void testGetProperty() throws Exception {
    var source = new ClowderJsonPathPropertySource(jsonFromResource(TEST_CLOWDER_CONFIG_JSON));
    var result = source.getProperty("clowder.endpoints[?(@.app == 'rbac')]");
    assertNotNull(result);
  }

  @Test
  void testJsonPathForInt() throws Exception {
    var s = "{\"foo\": 3}";
    var source = new ClowderJsonPathPropertySource(jsonFromString(s));
    var result = source.getJsonPathValue("foo");

    assertThat(result, isA(Integer.class));
    assertEquals(3, (Integer) result);
  }

  @Test
  void testJsonPathForDouble() throws Exception {
    var s = "{\"foo\": 3.14158}";
    var source = new ClowderJsonPathPropertySource(jsonFromString(s));
    var result = source.getJsonPathValue("foo");

    assertThat(result, isA(Double.class));
    assertEquals(3.14158, (Double) result);
  }

  @Test
  void testJsonPathForLong() throws Exception {
    String val = Long.toString(Long.MAX_VALUE);
    var s = "{\"foo\": " + val + "}";
    var source = new ClowderJsonPathPropertySource(jsonFromString(s));
    var result = source.getJsonPathValue("foo");

    assertThat(result, isA(Long.class));
    assertEquals(Long.MAX_VALUE, result);
  }

  @Test
  void testJsonPathForBigInt() throws Exception {
    String val = "10000" + Long.MAX_VALUE;
    var s = "{\"foo\": " + val + "}";
    var source = new ClowderJsonPathPropertySource(jsonFromString(s));
    var result = source.getJsonPathValue("foo");

    assertThat(result, isA(BigInteger.class));
    assertEquals(new BigInteger(val), result);
  }

  @Test
  void testJsonPathForBoolean() throws Exception {
    var s = "{\"foo\": true}";
    var source = new ClowderJsonPathPropertySource(jsonFromString(s));
    var result = source.getJsonPathValue("foo");

    assertThat(result, isA(Boolean.class));
    assertEquals(true, result);
  }

  @Test
  void testJsonPathForNull() throws Exception {
    var s = "{\"foo\": null}";
    var source = new ClowderJsonPathPropertySource(jsonFromString(s));
    var result = source.getJsonPathValue("foo");

    assertNull(result);
  }

  @Test
  void testJsonPathForString() throws Exception {
    var s = "{\"foo\": \"bar\"}";
    var source = new ClowderJsonPathPropertySource(jsonFromString(s));
    var result = source.getJsonPathValue("foo");

    assertEquals("bar", result);
  }

  @Test
  void testJsonPathForListOfMaps() throws Exception {
    var source = new ClowderJsonPathPropertySource(jsonFromString(COMPLEX_JSON));
    var result = source.getJsonPathValue("phoneNumbers");

    Map<String, String> m1 = Map.of("type", "work", "number", "515-555-1212");
    Map<String, String> m2 = Map.of("type", "home", "number", "515-555-8888");
    List<Map<String, String>> expected = List.of(m1, m2);

    assertEquals(expected, result);
  }

  @Test
  void testJsonPathForFilterWithNoResults() throws Exception {
    var source = new ClowderJsonPathPropertySource(jsonFromString(COMPLEX_JSON));
    var result = source.getJsonPathValue("phoneNumbers[?(@.type == 'missing')].number");

    assertNull(result);
  }

  @Test
  void testJsonPathWithFilter() throws Exception {
    var source = new ClowderJsonPathPropertySource(jsonFromString(COMPLEX_JSON));
    var result = source.getJsonPathValue("phoneNumbers[?(@.type == 'home')].number");

    assertEquals("515-555-8888", result);
  }

  @Test
  void testJsonPathForMapOfLists() throws Exception {
    var s =
        "{"
            + "\"desserts\": [\"cake\", \"cookies\", \"ice cream\"],"
            + "\"vegetables\": [\"carrots\", \"celery\", \"broccoli\"]"
            + "}";
    var source = new ClowderJsonPathPropertySource(jsonFromString(s));
    // "$" is the root node
    var result = source.getJsonPathValue("$");

    List<String> l1 = List.of("cake", "cookies", "ice cream");
    List<String> l2 = List.of("carrots", "celery", "broccoli");

    Map<String, List<String>> expected = Map.of("desserts", l1, "vegetables", l2);
    assertEquals(expected, result);
  }

  private ClowderJson jsonFromString(String json) throws IOException {
    var s = IOUtils.toInputStream(json, Charsets.UTF_8);
    return new ClowderJson(s, new ObjectMapper());
  }

  private ClowderJson jsonFromResource(String location) throws IOException {
    var s = new DefaultResourceLoader().getResource(location).getInputStream();
    return new ClowderJson(s, new ObjectMapper());
  }

  @Test
  void testAddToEnvironmentOrdering() throws Exception {
    MapPropertySource jndi =
        getPropertySource(StandardServletEnvironment.JNDI_PROPERTY_SOURCE_NAME, "jndi");
    environment.getPropertySources().addFirst(jndi);

    MapPropertySource servlet =
        getPropertySource(
            StandardServletEnvironment.SERVLET_CONTEXT_PROPERTY_SOURCE_NAME, "servlet");
    environment.getPropertySources().addFirst(servlet);

    MapPropertySource custom = getPropertySource("custom", "custom");
    environment.getPropertySources().addFirst(custom);

    var clowder = new ClowderJsonPathPropertySource(jsonFromResource(TEST_CLOWDER_CONFIG_JSON));
    clowder.addToEnvironment(environment, logFactory.getLog(ClowderJsonPathPropertySource.class));

    PropertySource<?> json =
        environment.getPropertySources().get(ClowderJsonPathPropertySource.PROPERTY_SOURCE_NAME);

    // The "custom" property source should supersede the Clowder source in precedence.
    assertEquals("custom", environment.getProperty("clowder.database.name"));
    // Using containsInRelativeOrder because there's actually an Inlined Test Properties source
    // at index zero because of the addClowderJson method
    assertThat(
        environment.getPropertySources(),
        Matchers.containsInRelativeOrder(custom, json, servlet, jndi));
  }

  private MapPropertySource getPropertySource(String name, String value) {
    return new MapPropertySource(name, Collections.singletonMap("clowder.database.name", value));
  }

  @Test
  void propertySourceShouldBeOrderedBeforeJndiPropertySource() throws Exception {
    testServletPropertySource(StandardServletEnvironment.JNDI_PROPERTY_SOURCE_NAME);
  }

  @Test
  void propertySourceShouldBeOrderedBeforeServletContextPropertySource() throws Exception {
    testServletPropertySource(StandardServletEnvironment.SERVLET_CONTEXT_PROPERTY_SOURCE_NAME);
  }

  @Test
  void propertySourceShouldBeOrderedBeforeServletConfigPropertySource() throws Exception {
    testServletPropertySource(StandardServletEnvironment.SERVLET_CONFIG_PROPERTY_SOURCE_NAME);
  }

  @Test
  void testKafkaBrokersPropertyWhenNoBrokersConfiguration() throws Exception {
    var source =
        new ClowderJsonPathPropertySource(
            jsonFromResource("classpath:/test-clowder-config-without-brokers.json"));
    var result = source.getProperty("clowder.kafka.brokers");
    assertNull(result);
  }

  @ParameterizedTest
  @CsvSource(
      value = {
        "clowder.kafka.brokers|env-rhsm-kafka.rhsm.svc:29092,env-rhsm-kafka-secondary.rhsm.svc:29093",
        "clowder.kafka.brokers.sasl.securityProtocol|SASL_SSL",
        "clowder.kafka.brokers.sasl.mechanism|PLAIN",
        "clowder.kafka.brokers.sasl.jaas.config|org.apache.kafka.common.security.plain.PlainLoginModule required username=\"john\" password=\"doe\";",
        "clowder.kafka.brokers.cacert|Dummy value",
        "clowder.kafka.brokers.cacert.type|PEM"
      },
      delimiter = '|')
  void testKafkaBrokersProperties(String property, String expectedValue) throws Exception {
    var source = new ClowderJsonPathPropertySource(jsonFromResource(TEST_CLOWDER_CONFIG_JSON));
    var result = source.getProperty(property);
    assertEquals(expectedValue, result);
  }

  private void testServletPropertySource(String servletContextPropertySourceName) throws Exception {
    environment
        .getPropertySources()
        .addFirst(getPropertySource(servletContextPropertySourceName, "servlet"));

    var clowder = new ClowderJsonPathPropertySource(jsonFromResource(TEST_CLOWDER_CONFIG_JSON));
    clowder.addToEnvironment(environment, logFactory.getLog(ClowderJsonPathPropertySource.class));

    assertEquals("swatch-tally-db", environment.getProperty("clowder.database.name"));
  }
}
