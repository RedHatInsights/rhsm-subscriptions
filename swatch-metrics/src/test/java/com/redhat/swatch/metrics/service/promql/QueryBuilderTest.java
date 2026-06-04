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
package com.redhat.swatch.metrics.service.promql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.redhat.swatch.configuration.registry.Metric;
import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import com.redhat.swatch.metrics.configuration.MetricProperties;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.config.SmallRyeConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class QueryBuilderTest {

  private static final String TEST_PROD_TAG = "OpenShift-dedicated-metrics";
  private static final String TEST_METRIC = "Cores";

  @InjectMock MetricProperties metricProperties;
  @Inject QueryBuilder builder;
  Metric tag;

  @BeforeEach
  void setupTest() {
    var subDefOptional = SubscriptionDefinition.lookupSubscriptionByTag(TEST_PROD_TAG);
    tag =
        subDefOptional
            .flatMap(subDef -> subDef.getMetric(TEST_METRIC))
            .orElseThrow(() -> new RuntimeException("Metric for tests not found!"));
  }

  @Test
  void testBuildQuery() {
    String account = "12345";
    String param1 = "ocm_subscription";
    when(metricProperties.getQueryTemplate("default"))
        .thenReturn(
            Optional.of(
                "Account: #{runtime[account]} Metric ID: #{metric.id} P1: #{metric.prometheus.queryParams[metadataMetric]}"));

    Map<String, String> params = new HashMap<>();
    params.put("metadataMetric", param1);

    Map<String, String> runtimeParams = new HashMap<>();
    runtimeParams.put("account", account);

    QueryDescriptor queryDesc = new QueryDescriptor(tag);
    queryDesc.addRuntimeVar("account", account);

    assertEquals(
        String.format("Account: %s Metric ID: %s P1: %s", account, TEST_METRIC, param1),
        builder.build(queryDesc));
  }

  @Test
  void testExceptionWhenInvalidTemplateSpecified() {
    String key = "default";
    QueryDescriptor descriptor = new QueryDescriptor(tag);
    Throwable e = assertThrows(IllegalArgumentException.class, () -> builder.build(descriptor));

    assertEquals(String.format("Unable to find query template for key: %s", key), e.getMessage());
  }

  /**
   * Useful to mock app properties like MetricProperties. More info in <a
   * href="https://quarkus.io/guides/config-mappings#mocking">here</a>.
   */
  public static class MetricPropertiesProducer {
    @Produces
    @ApplicationScoped
    @io.quarkus.test.Mock
    MetricProperties metricProperties(Config config) {
      return config.unwrap(SmallRyeConfig.class).getConfigMapping(MetricProperties.class);
    }
  }
}
