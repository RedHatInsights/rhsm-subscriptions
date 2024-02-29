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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Collections;
import java.util.function.Supplier;
import java.util.regex.Pattern;
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

  private final ConfigurableEnvironment environment = new StandardEnvironment();
  private final DeferredLogFactory logFactory = Supplier::get;

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
        "clowder.kafka.brokers.cacert.type|PEM",
        "clowder.endpoints.rhsm-clowdapp-service.url|http://rhsm-clowdapp-service.rhsm.svc:8000",
        "clowder.endpoints.index-service.url|https://index.rhsm.svc:8800",
        "clowder.endpoints.index-service.trust-store-path|file:/tmp/truststore.*.trust",
        "clowder.endpoints.index-service.trust-store-password|.+",
        "clowder.endpoints.index-service.trust-store-type|PKCS12",
        "clowder.privateEndpoints.export-service-service.url|http://export-service-service.svc:10000",
        "clowder.kafka.topics.\"platform.rhsm-subscriptions.tally\".name|platform.rhsm-subscriptions.tally-env-rhsm-rhsm",
        "clowder.kafka.topics.platform.rhsm-subscriptions.tasks.name|platform.rhsm-subscriptions.tasks-env-rhsm-rhsm"
      },
      delimiter = '|')
  void testCustomLogicInProperties(String property, String expectedValue) throws Exception {
    var source = new ClowderJsonPathPropertySource(jsonFromResource(TEST_CLOWDER_CONFIG_JSON));
    var result = source.getProperty(property);
    assertTrue(
        Pattern.matches(expectedValue, (String) result),
        String.format(
            "Actual value was '%s' and expected expression is '%s'", result, expectedValue));
  }

  private void testServletPropertySource(String servletContextPropertySourceName) throws Exception {
    environment
        .getPropertySources()
        .addFirst(getPropertySource(servletContextPropertySourceName, "servlet"));

    var clowder = new ClowderJsonPathPropertySource(jsonFromResource(TEST_CLOWDER_CONFIG_JSON));
    clowder.addToEnvironment(environment, logFactory.getLog(ClowderJsonPathPropertySource.class));

    assertEquals("swatch-tally-db", environment.getProperty("clowder.database.name"));
  }

  private MapPropertySource getPropertySource(String name, String value) {
    return new MapPropertySource(name, Collections.singletonMap("clowder.database.name", value));
  }
}
